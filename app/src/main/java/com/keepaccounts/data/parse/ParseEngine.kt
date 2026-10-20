package com.keepaccounts.data.parse

import androidx.room.withTransaction
import com.keepaccounts.data.local.db.AppDatabase
import com.keepaccounts.data.local.db.Confidence
import com.keepaccounts.data.local.db.ParseStatus
import com.keepaccounts.data.local.db.RawEventEntity
import com.keepaccounts.data.local.db.RuleState
import com.keepaccounts.data.local.db.SettingKeys
import com.keepaccounts.data.local.db.TxSource
import com.keepaccounts.data.local.db.TxStatus
import com.keepaccounts.data.local.db.TxType
import com.keepaccounts.data.repo.SettingsRepository
import com.keepaccounts.data.repo.TransactionRepository
import com.keepaccounts.data.repo.TxnDraft

/**
 * 解析管线。
 *
 * 顺序（对应规格 4.1）：
 *   白名单（监听器已做）→ 噪音过滤 → 规则路由 → 字段抽取 → 语义校验 → 去重 → 写库。
 *
 * 安全原则：拿不准就进待确认箱，绝不静默丢数据。
 * - 专属规则命中且方向明确 → 直接入账（ACTIVE 且置信度高）；
 * - 通用兜底 / 方向不明 / 观察模式 → 进待确认箱。
 */
class ParseEngine(
    private val db: AppDatabase,
    private val txnRepo: TransactionRepository,
    private val settingsRepo: SettingsRepository
) {

    private val rawDao = db.rawEventDao()
    private val ruleDao = db.ruleDao()
    private val categoryDao = db.categoryDao()
    private val accountDao = db.accountDao()
    private val txnDao = db.transactionDao()

    /** 解析单条通知。异常只把该条标记成待处理，绝不让监听回调崩掉 */
    suspend fun processEvent(eventId: Long) {
        val event = rawDao.findById(eventId) ?: return
        if (event.parseStatus != ParseStatus.CAPTURED) return
        runCatching { run(event) }.onFailure {
            rawDao.updateResult(eventId, ParseStatus.UNMATCHED, "解析异常：${it.message}", null, null)
        }
    }

    /** 启动时把遗留的 captured 记录补跑一遍，返回处理条数 */
    suspend fun processCaptured(): Int {
        val events = rawDao.listCaptured(200)
        events.forEach { processEvent(it.id) }
        return events.size
    }

    private suspend fun run(event: RawEventEntity) {
        val text = event.text
        val title = event.title
        val now = System.currentTimeMillis()

        // ① 噪音过滤
        val noise = NoiseFilter.filter(text)
        if (noise.blocked) {
            rawDao.updateResult(event.id, ParseStatus.REJECTED, "${noise.layer} ${noise.reason}", null, null)
            return
        }

        // ② 规则路由：专属规则按 priority，其次通用兜底
        val packageRules = ruleDao.listForPackage(event.packageName)
            .filter { it.state == RuleState.ACTIVE }
        val matched = packageRules.firstOrNull {
            RuleTemplate.fromJson(it.templateJson).matches(text)
        }
        val genericEnabled = settingsRepo.getBoolean(SettingKeys.GENERIC_RULE_ENABLED, true)
        val rule = matched
            ?: if (genericEnabled) ruleDao.findGeneric()?.takeIf { it.state == RuleState.ACTIVE } else null

        if (rule == null) {
            rawDao.updateResult(event.id, ParseStatus.UNMATCHED, "未命中规则", null, null)
            return
        }

        // ③ 字段抽取
        val amount = AmountExtractor.extract(text)
        if (amount == null) {
            rawDao.updateResult(event.id, ParseStatus.UNMATCHED, "未找到金额", rule.id, null)
            return
        }
        if (amount > rule.amountMax) {
            rawDao.updateResult(event.id, ParseStatus.REJECTED, "L3 金额超上限", rule.id, null)
            return
        }

        val direction = DirectionClassifier.classify(text)
        val merchant = MerchantClassifier.extract(title, text)
        val categoryName = MerchantClassifier.guessCategory(text, direction)
        val categoryId = categoryName
            ?.let { name -> categoryDao.listAll().firstOrNull { it.name == name }?.id }
            ?: rule.defaultCategoryId
        val accountId = rule.defaultAccountId ?: accountDao.listActive().firstOrNull()?.id

        if (accountId == null) {
            rawDao.updateResult(event.id, ParseStatus.UNMATCHED, "无可用账户", rule.id, null)
            return
        }

        // ④ 去重（L6）
        if (txnDao.countSimilar(merchant ?: "", amount, accountId, now - 60_000) > 0) {
            rawDao.updateResult(event.id, ParseStatus.REJECTED, "L6 重复通知", rule.id, null)
            return
        }

        // ⑤ 定级：专属 + 方向明确 → 直接入账；其余进待确认
        val isGeneric = rule.packageName == null
        val autoConfirm = !isGeneric && direction != null
        val status = if (autoConfirm) TxStatus.CONFIRMED else TxStatus.PENDING
        val confidence = when {
            direction == null -> Confidence.LOW
            isGeneric -> Confidence.MEDIUM
            else -> Confidence.HIGH
        }

        val occurredAt = event.postedAt.takeIf { it > 0 } ?: now

        db.withTransaction {
            val txnId = txnRepo.insertWithinTransaction(
                TxnDraft(
                    type = direction ?: TxType.EXPENSE,
                    amount = amount,
                    accountId = accountId,
                    categoryId = categoryId,
                    merchant = merchant,
                    note = null,
                    source = TxSource.NOTIFICATION,
                    status = status,
                    confidence = confidence,
                    occurredAt = occurredAt,
                    rawEventId = event.id,
                    ruleId = if (isGeneric) null else rule.id
                )
            )
            val parseStatus = if (status == TxStatus.CONFIRMED) ParseStatus.MATCHED else ParseStatus.PENDING
            rawDao.updateResult(event.id, parseStatus, null, if (isGeneric) null else rule.id, txnId)
            if (!isGeneric) {
                ruleDao.recordHit(rule.id, now)
                if (status == TxStatus.CONFIRMED) ruleDao.recordConfirm(rule.id, now)
            }
        }
    }
}
