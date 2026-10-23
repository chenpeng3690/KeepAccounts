package com.local.notebook.service

import com.local.notebook.data.local.NotificationRawEntity
import com.local.notebook.data.local.TxnEntity
import com.local.notebook.data.repository.LedgerRepository
import com.local.notebook.data.repository.SettingsRepository
import com.local.notebook.domain.parser.Direction
import com.local.notebook.domain.parser.NotificationPayload
import com.local.notebook.domain.parser.NotificationRuleEngine
import com.local.notebook.domain.parser.ParseOutcome
import com.local.notebook.domain.parser.RuleDefinition
import com.local.notebook.domain.parser.RuleTemplateCodec
import com.local.notebook.service.feedback.FeedbackNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class ProcessOutcome { IGNORED, DUPLICATE, REJECTED, MATCHED, PENDING }

@Singleton
class NotificationProcessor @Inject constructor(
    private val repository: LedgerRepository,
    private val settings: SettingsRepository,
    private val feedbackNotifier: FeedbackNotifier
) {
    private val engine = NotificationRuleEngine()
    private val moneyFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))

    suspend fun process(payload: NotificationPayload): ProcessOutcome = withContext(Dispatchers.IO) {
        if (payload.isGroupSummary) return@withContext ProcessOutcome.IGNORED

        val whitelist = settings.whitelistPackages.first()
        if (payload.packageName !in whitelist) return@withContext ProcessOutcome.IGNORED

        val now = payload.receivedAt
        val recent = repository.findRecentRawByKey(payload.notificationKey, now - DEDUPE_WINDOW_MS)
        if (recent != null) {
            repository.updateRawContent(
                id = recent.id,
                title = payload.title,
                text = payload.text,
                receivedAt = now
            )
            return@withContext ProcessOutcome.DUPLICATE
        }

        val rules = repository.rulesForPackage(payload.packageName).mapNotNull { entity ->
            val decoded = RuleTemplateCodec.decode(entity.templateJson)
            if (decoded.literalGroups.isEmpty()) {
                null
            } else {
                RuleDefinition(
                    id = entity.id,
                    name = entity.name,
                    packageName = entity.packageName,
                    priority = entity.priority,
                    state = entity.state,
                    literalGroups = decoded.literalGroups,
                    fuzzy = decoded.fuzzy,
                    explicitDirection = decoded.direction,
                    excludeKeywords = entity.excludeKeywords
                        .split(',', '，')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() },
                    defaultAccountId = entity.defaultAccountId,
                    defaultCategoryId = entity.defaultCategoryId
                )
            }
        }
        val fallbackEnabled = settings.fallbackRuleEnabled.first()
        val outcome = engine.parse(payload, rules, fallbackEnabled)

        when (outcome) {
            ParseOutcome.Ignored -> ProcessOutcome.IGNORED
            is ParseOutcome.Rejected -> {
                repository.insertRawNotification(
                    baseRaw(payload).copy(
                        matchResult = "REJECTED",
                        rejectReason = "已拦截 → ${outcome.layer} ${outcome.reason}"
                    )
                )
                ProcessOutcome.REJECTED
            }
            is ParseOutcome.Matched -> handleMatched(payload, outcome)
            is ParseOutcome.Pending -> handlePending(payload, outcome)
        }
    }

    private suspend fun handleMatched(
        payload: NotificationPayload,
        matched: ParseOutcome.Matched
    ): ProcessOutcome {
        val accountId = matched.defaultAccountId
            ?: repository.defaultAccountIdForPackage(payload.packageName)
            ?: return storeRejected(payload, "L3 找不到可用账户")
        val categoryId = repository.categoryIdByName(
            matched.categoryName,
            matched.direction.name
        ) ?: matched.defaultCategoryId
        val dedupeKey = dedupeKey(payload, matched.amount, matched.merchant)
        val explanation = "${matched.ruleName} → 已入账 · " +
            "${matched.categoryName ?: "未分类"} ${sign(matched.direction)}${money(matched.amount)}"

        if (repository.countTxnByDedupeKey(dedupeKey) > 0) {
            return storeRejected(payload, "L6 重复通知，已去重", ruleId = matched.ruleId)
        }

        val rawId = repository.insertRawNotification(
            baseRaw(payload).copy(
                ruleId = matched.ruleId,
                matchResult = "MATCHED",
                rejectReason = explanation
            )
        )
        val txnId = repository.insertNotificationTransaction(
            TxnEntity(
                type = matched.direction.name,
                amount = matched.amount,
                accountId = accountId,
                categoryId = categoryId,
                merchant = matched.merchant,
                occurredAt = payload.postedAt,
                source = "NOTIFICATION",
                ruleId = matched.ruleId,
                rawNotificationId = rawId,
                status = "NORMAL",
                dedupeKey = dedupeKey
            )
        )
        if (txnId > 0) {
            repository.recordRuleHit(matched.ruleId, payload.receivedAt)
            feedbackNotifier.onAutoPosted(
                txnId = txnId,
                amount = matched.amount,
                merchant = matched.merchant,
                category = matched.categoryName,
                account = repository.getAccount(accountId)?.name,
                appLabel = payload.appLabel
            )
        }
        return ProcessOutcome.MATCHED
    }

    private suspend fun handlePending(
        payload: NotificationPayload,
        pending: ParseOutcome.Pending
    ): ProcessOutcome {
        val accountId = pending.defaultAccountId
            ?: repository.defaultAccountIdForPackage(payload.packageName)
            ?: return storeRejected(payload, "L3 找不到可用账户", pending.ruleId)
        val direction = pending.direction ?: Direction.EXPENSE
        val categoryId = repository.categoryIdByName(
            pending.categoryName,
            direction.name
        ) ?: pending.defaultCategoryId
        val dedupeKey = dedupeKey(payload, pending.amount, pending.merchant)
        val explanation = pending.ruleName?.let { "$it → 待确认" } ?: "通用规则命中 → 待确认"

        if (repository.countTxnByDedupeKey(dedupeKey) > 0) {
            return storeRejected(payload, "L6 重复通知，已去重", pending.ruleId)
        }

        val rawId = repository.insertRawNotification(
            baseRaw(payload).copy(
                ruleId = pending.ruleId,
                matchResult = "PENDING",
                rejectReason = explanation
            )
        )
        val pendingTxnId = repository.insertNotificationTransaction(
            TxnEntity(
                type = direction.name,
                amount = pending.amount,
                accountId = accountId,
                categoryId = categoryId,
                merchant = pending.merchant,
                occurredAt = payload.postedAt,
                source = "NOTIFICATION",
                ruleId = pending.ruleId,
                rawNotificationId = rawId,
                status = "PENDING",
                dedupeKey = dedupeKey
            )
        )
        pending.ruleId?.let { repository.recordRuleHit(it, payload.receivedAt) }
        feedbackNotifier.onPending(
            txnId = pendingTxnId,
            amount = pending.amount,
            appLabel = payload.appLabel,
            merchant = pending.merchant
        )
        return ProcessOutcome.PENDING
    }

    private suspend fun storeRejected(
        payload: NotificationPayload,
        reason: String,
        ruleId: Long? = null
    ): ProcessOutcome {
        repository.insertRawNotification(
            baseRaw(payload).copy(
                ruleId = ruleId,
                matchResult = "REJECTED",
                rejectReason = "已拦截 → $reason"
            )
        )
        return ProcessOutcome.REJECTED
    }

    private fun baseRaw(payload: NotificationPayload) = NotificationRawEntity(
        packageName = payload.packageName,
        appLabel = payload.appLabel,
        title = payload.title,
        text = payload.text,
        postedAt = payload.postedAt,
        receivedAt = payload.receivedAt,
        groupKey = payload.notificationKey,
        matchResult = "REJECTED"
    )

    private fun dedupeKey(payload: NotificationPayload, amount: Long, merchant: String?): String {
        val bucket = payload.receivedAt / DEDUPE_WINDOW_MS
        val raw = listOf(
            payload.packageName,
            payload.notificationKey,
            amount.toString(),
            merchant.orEmpty(),
            bucket.toString()
        ).joinToString("|")
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun sign(direction: Direction): String =
        if (direction == Direction.INCOME) "+" else "-"

    private fun money(cents: Long): String = moneyFormat.format(cents / 100.0)

    private companion object {
        const val DEDUPE_WINDOW_MS = 60_000L
    }
}
