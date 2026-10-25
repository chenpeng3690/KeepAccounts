package com.local.notebook.data.ai

import com.local.notebook.data.local.AccountEntity
import com.local.notebook.data.local.NotificationRawEntity
import com.local.notebook.data.local.TxnEntity
import com.local.notebook.data.repository.LedgerRepository
import com.local.notebook.data.repository.SettingsRepository
import com.local.notebook.domain.ai.AiCallResult
import com.local.notebook.domain.ai.AiConfig
import com.local.notebook.domain.ai.AiParseResult
import com.local.notebook.domain.parser.AmountParser
import com.local.notebook.domain.parser.CategoryGuesser
import com.local.notebook.domain.parser.Direction
import com.local.notebook.domain.parser.DirectionParser
import com.local.notebook.service.feedback.FeedbackNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AiRunResult {
    data object Done : AiRunResult
    data object Retry : AiRunResult
}

@Singleton
class AiParseRunner @Inject constructor(
    private val repository: LedgerRepository,
    private val settings: SettingsRepository,
    private val keyStore: AiKeyStore,
    private val client: AiClient,
    private val feedbackNotifier: FeedbackNotifier
) {

    suspend fun run(rawId: Long): AiRunResult = withContext(Dispatchers.IO) {
        val raw = repository.getRaw(rawId) ?: return@withContext AiRunResult.Done
        if (raw.matchResult !in setOf("QUEUED", "AI_RETRY")) return@withContext AiRunResult.Done

        val enabled = settings.aiEnabled.first()
        val apiKey = keyStore.load()
        if (!enabled || apiKey.isNullOrBlank()) {
            fallback(raw, "AI 未启用或未配置 API Key")
            return@withContext AiRunResult.Done
        }

        val config = AiConfig(
            baseUrl = settings.aiBaseUrl.first(),
            model = settings.aiModel.first(),
            apiKey = apiKey,
            confidencePercent = settings.aiConfidencePercent.first()
        )
        val categories = repository.categories.first().map { it.name to it.type }
        val accounts = repository.accounts.first().filterNot { it.archived }
        val text = listOfNotNull(raw.title, raw.text)
            .filter { it.isNotBlank() }
            .joinToString("\n")

        when (val call = client.parse(
            config = config,
            appLabel = raw.appLabel ?: raw.packageName,
            packageName = raw.packageName,
            title = raw.title.orEmpty(),
            text = raw.text.orEmpty(),
            categories = categories,
            accounts = accounts.map { it.name },
            occurredAt = raw.postedAt
        )) {
            is AiCallResult.Failure -> {
                repository.updateRawAiResult(
                    id = rawId,
                    result = "AI_RETRY",
                    reason = "AI 请求失败：${call.message}",
                    response = null,
                    model = config.model,
                    error = call.message
                )
                if (call.retryable) {
                    AiRunResult.Retry
                } else {
                    fallback(raw, call.message)
                    AiRunResult.Done
                }
            }

            is AiCallResult.Success -> {
                applyResult(raw, call.result, call.rawJson, config)
                AiRunResult.Done
            }
        }
    }

    /** AI 不可用或连续重试失败时，尽量用本地金额提取转待确认，避免丢单。 */
    suspend fun fallback(rawId: Long, reason: String) {
        val raw = repository.getRaw(rawId) ?: return
        fallback(raw, reason)
    }

    private suspend fun applyResult(
        raw: NotificationRawEntity,
        result: AiParseResult,
        rawJson: String,
        config: AiConfig
    ) {
        if (!result.isTransaction) {
            repository.updateRawAiResult(
                id = raw.id,
                result = "AI_IGNORED",
                reason = "AI 判定非交易：${result.reason ?: "无说明"}",
                response = rawJson,
                model = config.model,
                error = null
            )
            return
        }

        val type = result.type
        val amount = result.amountCents
        if (type == null || amount == null || amount <= 0) {
            repository.updateRawAiResult(
                id = raw.id,
                result = "AI_RETRY",
                reason = "AI 结果缺少有效类型或金额",
                response = rawJson,
                model = config.model,
                error = "invalid result"
            )
            fallback(raw, "AI 结果缺少有效类型或金额")
            return
        }

        val text = listOfNotNull(raw.title, raw.text).joinToString("\n")
        val accounts = repository.accounts.first().filterNot { it.archived }
        val accountId = resolveAccount(result.account, accounts, raw.packageName)
        val toAccountId = resolveAccount(result.toAccount, accounts, null)
        var categoryName = result.category?.takeIf { it.isNotBlank() }
        var categoryId = repository.categoryIdByName(categoryName, type)
        if (categoryId == null) {
            categoryName = if (type == "INCOME") "其他收入" else "其他"
            categoryId = repository.categoryIdByName(categoryName, type)
        }
        val merchant = result.merchant ?: raw.title?.takeIf { it.isNotBlank() }
        val mode = settings.aiMode.first()
        val validTransfer = type != "TRANSFER" ||
            (toAccountId != null && toAccountId != accountId)
        val direct = mode == "DIRECT" &&
            result.confidence >= config.confidenceThreshold &&
            accountId != null &&
            validTransfer

        val dedupeKey = dedupeKey(raw, amount, merchant)
        if (repository.countTxnByDedupeKey(dedupeKey) > 0) {
            repository.updateRawAiResult(
                id = raw.id,
                result = "AI_DUPLICATE",
                reason = "重复交易，已去重",
                response = rawJson,
                model = config.model,
                error = null
            )
            return
        }

        val pendingAccountId = accountId ?: accounts.firstOrNull()?.id
        if (pendingAccountId == null) {
            repository.updateRawAiResult(
                id = raw.id,
                result = "AI_FAILED",
                reason = "没有可用账户，无法入账",
                response = rawJson,
                model = config.model,
                error = "no account"
            )
            return
        }

        val status = if (direct) "NORMAL" else "PENDING"
        val txnId = repository.insertNotificationTransaction(
            TxnEntity(
                type = type,
                amount = amount,
                accountId = pendingAccountId,
                toAccountId = if (type == "TRANSFER") toAccountId else null,
                categoryId = categoryId,
                merchant = merchant,
                note = if (type == "TRANSFER" && toAccountId == null) {
                    "AI 未识别到转入账户，请补充"
                } else {
                    null
                },
                occurredAt = raw.postedAt,
                source = "NOTIFICATION",
                rawNotificationId = raw.id,
                status = status,
                dedupeKey = dedupeKey
            )
        )

        val summary = "$merchant · ${categoryName ?: "未分类"} · ¥${money(amount)}"
        repository.updateRawAiResult(
            id = raw.id,
            result = if (direct) "AI_MATCHED" else "AI_PENDING",
            reason = if (direct) "AI 已入账 · $summary" else "AI 待确认 · $summary",
            response = rawJson,
            model = config.model,
            error = null
        )

        if (direct) {
            feedbackNotifier.onAutoPosted(
                txnId = txnId,
                amount = amount,
                merchant = merchant,
                category = categoryName,
                account = accounts.firstOrNull { it.id == pendingAccountId }?.name,
                appLabel = raw.appLabel ?: raw.packageName
            )
        } else {
            feedbackNotifier.onPending(
                txnId = txnId,
                amount = amount,
                appLabel = raw.appLabel ?: raw.packageName,
                merchant = merchant
            )
        }
    }

    private suspend fun fallback(raw: NotificationRawEntity, reason: String) {
        val text = listOfNotNull(raw.title, raw.text).joinToString("\n")
        val amount = AmountParser.bestAmount(text)?.cents
        val accounts = repository.accounts.first().filterNot { it.archived }
        val accountId = accounts.firstOrNull()?.id
        if (amount == null || accountId == null) {
            repository.updateRawAiResult(
                id = raw.id,
                result = "AI_FAILED",
                reason = "$reason；${if (amount == null) "未提取到金额" else "没有可用账户"}",
                response = null,
                model = settings.aiModel.first(),
                error = reason
            )
            return
        }

        val direction = DirectionParser.detect(text).let {
            if (it == Direction.UNKNOWN) Direction.EXPENSE else it
        }
        val categoryName = CategoryGuesser.guess(text, direction)
        val dedupeKey = dedupeKey(raw, amount, raw.title)
        if (repository.countTxnByDedupeKey(dedupeKey) > 0) {
            repository.updateRawAiResult(
                id = raw.id,
                result = "AI_DUPLICATE",
                reason = "重复交易，已去重",
                response = null,
                model = null,
                error = null
            )
            return
        }
        val txnId = repository.insertNotificationTransaction(
            TxnEntity(
                type = direction.name,
                amount = amount,
                accountId = accountId,
                categoryId = repository.categoryIdByName(categoryName, direction.name),
                merchant = raw.title?.takeIf { it.isNotBlank() },
                note = "AI 解析失败，已转待确认",
                occurredAt = raw.postedAt,
                source = "NOTIFICATION",
                rawNotificationId = raw.id,
                status = "PENDING",
                dedupeKey = dedupeKey
            )
        )
        repository.updateRawAiResult(
            id = raw.id,
            result = "AI_PENDING",
            reason = "$reason；已转待确认",
            response = null,
            model = settings.aiModel.first(),
            error = reason
        )
        feedbackNotifier.onPending(
            txnId = txnId,
            amount = amount,
            appLabel = raw.appLabel ?: raw.packageName,
            merchant = raw.title
        )
    }

    private fun resolveAccount(
        hint: String?,
        accounts: List<AccountEntity>,
        packageName: String?
    ): Long? {
        val value = hint?.trim().orEmpty()
        if (value.isNotEmpty()) {
            accounts.firstOrNull { it.name.contains(value) || value.contains(it.name) }
                ?.let { return it.id }
            if (value.contains("微信")) {
                accounts.firstOrNull { it.name.contains("微信") }?.let { return it.id }
            }
            if (value.contains("支付宝")) {
                accounts.firstOrNull { it.name.contains("支付宝") }?.let { return it.id }
            }
            if (value.contains("银行") || value.contains("卡")) {
                accounts.firstOrNull { it.type == "DEBIT" || it.type == "CREDIT" }
                    ?.let { return it.id }
            }
        }
        if (packageName == "com.tencent.mm") {
            accounts.firstOrNull { it.name.contains("微信") }?.let { return it.id }
        }
        if (packageName == "com.eg.android.AlipayGphone") {
            accounts.firstOrNull { it.name.contains("支付宝") }?.let { return it.id }
        }
        return accounts.singleOrNull()?.id
    }

    private fun dedupeKey(raw: NotificationRawEntity, amount: Long, merchant: String?): String {
        val bucket = raw.receivedAt / DEDUPE_WINDOW_MS
        val content = listOf(
            raw.packageName,
            raw.groupKey.orEmpty(),
            amount.toString(),
            merchant.orEmpty(),
            bucket.toString()
        ).joinToString("|")
        return MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun money(cents: Long): String =
        java.math.BigDecimal.valueOf(cents)
            .movePointLeft(2)
            .setScale(2, java.math.RoundingMode.HALF_UP)
            .toPlainString()

    private companion object {
        const val DEDUPE_WINDOW_MS = 60_000L
    }
}
