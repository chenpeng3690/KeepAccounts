package com.keepaccounts.data.local.db

/**
 * 枚举一律用小写字符串常量，不用 Kotlin enum。
 *
 * 原因：Room 会把 enum 存成大写的 "EXPENSE"，跟导出 CSV 里期望的小写不一致；
 * 而且以后加值时要改枚举再改迁移，字符串常量没有这个负担。
 */
object TxType {
    const val EXPENSE = "expense"
    const val INCOME = "income"
    const val TRANSFER = "transfer"
    const val REFUND = "refund"
}

object TxSource {
    const val MANUAL = "manual"
    const val NOTIFICATION = "notification"
    const val IMPORT = "import"
    /** 账户余额校准 */
    const val ADJUSTMENT = "adjustment"
}

object TxStatus {
    /** 正常入账 */
    const val CONFIRMED = "confirmed"
    /** 进了待确认箱，尚未计入统计与余额 */
    const val PENDING = "pending"
}

object Confidence {
    const val HIGH = "high"
    const val MEDIUM = "medium"
    const val LOW = "low"
}

object AccountType {
    const val CASH = "cash"
    const val DEBIT = "debit"
    const val CREDIT = "credit"
    /** 花呗 / 白条 */
    const val BNPL = "bnpl"
    /** 借呗 / 网贷 */
    const val LOAN = "loan"
    const val INVESTMENT = "investment"
    const val OTHER = "other"

    /** 负债类账户，余额以负数表示欠款 */
    val LIABILITY = setOf(CREDIT, BNPL, LOAN)
}

object CategoryType {
    const val EXPENSE = "expense"
    const val INCOME = "income"
}

object RuleState {
    /** 观察模式：命中只记录不记账，攒够确认后由用户手动启用 */
    const val OBSERVING = "observing"
    const val ACTIVE = "active"
    const val DISABLED = "disabled"
}

/** 通知的解析结果 */
object ParseStatus {
    /** 已采集但还没解析（第 5 步的状态） */
    const val CAPTURED = "captured"
    /** 成功生成账单 */
    const val MATCHED = "matched"
    /** 命中但置信度不足，进了待确认箱 */
    const val PENDING = "pending"
    /** 被某一层噪音规则拦下 */
    const val REJECTED = "rejected"
    /** 未命中任何规则 */
    const val UNMATCHED = "unmatched"
}

object BackupTrigger {
    const val MANUAL = "manual"
    const val AUTO = "auto"
}

/** app_settings 表的 key 常量，避免手写字符串拼错 */
object SettingKeys {
    const val SEEDED = "seeded"
    /** 回收站保留天数，默认 7 */
    const val RECYCLE_RETENTION_DAYS = "recycle_retention_days"
    /** 自动备份开关，默认关闭 */
    const val AUTO_BACKUP_ENABLED = "auto_backup_enabled"
    /** 自动备份周期（天），可选 1 / 3 / 7 / 30，默认 7 */
    const val AUTO_BACKUP_INTERVAL_DAYS = "auto_backup_interval_days"
    /** 记账提示档位：all / success_only / off */
    const val FEEDBACK_LEVEL = "feedback_level"
    /** 白名单包名，逗号分隔 */
    const val WHITELIST = "whitelist_packages"
    /** 收录全部通知（true 时不再看白名单） */
    const val CAPTURE_ALL = "capture_all_apps"
    /** 通用兜底规则开关，默认开。关闭后白名单内没有专属规则的应用不再解析 */
    const val GENERIC_RULE_ENABLED = "generic_rule_enabled"
    /** 静音时段开关，默认开 */
    const val QUIET_ENABLED = "quiet_enabled"
    /** 静音时段起（HH:mm），默认 23:00 */
    const val QUIET_START = "quiet_start"
    /** 静音时段止（HH:mm），默认 07:00 */
    const val QUIET_END = "quiet_end"
}
