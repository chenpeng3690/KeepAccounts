package com.keepaccounts.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 表结构一次性建全（9 张），避免后续步骤频繁写迁移。
 *
 * 三条贯穿全项目的数据约定：
 * 1. 金额一律存「分」的 Long，恒为正，方向由 type 表达 —— 这样聚合时不用判符号。
 * 2. 所有业务查询必须带 `deleted_at IS NULL`，软删除的隐形契约，漏一个回收站数据就会混进统计。
 * 3. 枚举存小写字符串（见 Enums.kt），CHECK 约束由 Repository 层保证。
 */

@Entity(
    tableName = "accounts",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["archived", "sort_order"])
    ]
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    /** 建账户时填的余额，之后不再变；校准会生成一条 adjust 流水而不是改这里 */
    @ColumnInfo(name = "initial_balance") val initialBalance: Long = 0,
    /**
     * 冗余字段，跟流水表有一致性契约：任何增删改都必须在同一事务里用
     * `applyBalance(delta)` 重算，不允许单独更新。
     */
    @ColumnInfo(name = "current_balance") val currentBalance: Long = 0,
    @ColumnInfo(name = "card_tail") val cardTail: String? = null,
    /** 仅信用卡/花呗类使用；负债账户 currentBalance 为负 */
    @ColumnInfo(name = "credit_limit") val creditLimit: Long? = null,
    @ColumnInfo(name = "bill_day") val billDay: Int? = null,
    @ColumnInfo(name = "repay_day") val repayDay: Int? = null,
    @ColumnInfo(name = "include_in_net_worth") val includeInNetWorth: Boolean = true,
    val icon: String = "wallet",
    val color: String = "#3B5BDB",
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    val archived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["parent_id", "sort_order"]), Index(value = ["type", "sort_order"])]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "parent_id") val parentId: Long? = null,
    val name: String,
    val type: String,
    val icon: String = "more",
    val color: String = "#A0A6B0",
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    val hidden: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["to_account_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["refund_of"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["occurred_at"]),
        Index(value = ["account_id", "occurred_at"]),
        Index(value = ["to_account_id"]),
        Index(value = ["category_id", "occurred_at"]),
        Index(value = ["status", "occurred_at"]),
        Index(value = ["type", "occurred_at"]),
        Index(value = ["refund_of"]),
        Index(value = ["deleted_at"]),
        Index(value = ["raw_event_id"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    /** 单位「分」，恒为正 */
    val amount: Long,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "account_id") val accountId: Long,
    /** 仅 transfer 使用 */
    @ColumnInfo(name = "to_account_id") val toAccountId: Long? = null,
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    /** 通知里抽到的原始商户名 */
    val merchant: String? = null,
    val note: String? = null,
    val source: String,
    val confidence: String = Confidence.HIGH,
    val status: String = TxStatus.CONFIRMED,
    /** 退款指向原支出 */
    @ColumnInfo(name = "refund_of") val refundOf: Long? = null,
    /** 关联的原始通知，便于回溯「这笔是哪个通知记的」 */
    @ColumnInfo(name = "raw_event_id") val rawEventId: Long? = null,
    @ColumnInfo(name = "rule_id") val ruleId: Long? = null,
    /** 非 null = 已进回收站 */
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

/** 账单编辑历史，用于误改后回溯（只保留最近若干条） */
@Entity(
    tableName = "transaction_edits",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["txn_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["txn_id"])]
)
data class TransactionEditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "txn_id") val txnId: Long,
    @ColumnInfo(name = "before_json") val beforeJson: String,
    @ColumnInfo(name = "after_json") val afterJson: String,
    @ColumnInfo(name = "edited_at") val editedAt: Long
)

@Entity(
    tableName = "raw_events",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["received_at"]),
        Index(value = ["package_name", "received_at"]),
        Index(value = ["parse_status", "received_at"]),
        Index(value = ["transaction_id"])
    ]
)
data class RawEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "app_label") val appLabel: String? = null,
    val title: String? = null,
    /** 由 title + text + bigText + subText + infoText 拼成的完整正文 */
    val text: String,
    @ColumnInfo(name = "posted_at") val postedAt: Long,
    @ColumnInfo(name = "received_at") val receivedAt: Long,
    @ColumnInfo(name = "group_key") val groupKey: String? = null,
    @ColumnInfo(name = "parse_status") val parseStatus: String,
    /** 被拦下时记录是哪一层拦的，例："L1 命中噪音词：优惠券" */
    @ColumnInfo(name = "reject_reason") val rejectReason: String? = null,
    @ColumnInfo(name = "matched_rule_id") val matchedRuleId: Long? = null,
    @ColumnInfo(name = "transaction_id") val transactionId: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

/**
 * 解析规则。用户侧看到的是「贴一条通知、点一下金额」，这里存的是片段模板。
 *
 * template_json 形如：
 * [{"t":"literal","v":"支付成功"},{"t":"amount"},{"t":"literal","v":"元"}]
 * 详见 step 6 的 RuleTemplate 解析器。绝不向用户暴露语法。
 */
@Entity(
    tableName = "rules",
    indices = [
        Index(value = ["package_name", "priority"]),
        Index(value = ["state"])
    ]
)
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** null = 通用兜底规则 */
    @ColumnInfo(name = "package_name") val packageName: String? = null,
    val priority: Int = 100,
    val builtin: Boolean = false,
    val state: String = RuleState.OBSERVING,
    @ColumnInfo(name = "template_json") val templateJson: String,
    /** 逗号分隔的方向词，留空则用全局内置词表 */
    @ColumnInfo(name = "expense_words") val expenseWords: String? = null,
    @ColumnInfo(name = "income_words") val incomeWords: String? = null,
    /** 逗号分隔的排除词 */
    @ColumnInfo(name = "exclude_keywords") val excludeKeywords: String? = null,
    /** 单笔金额上限（分），超过视为异常拦截 */
    @ColumnInfo(name = "amount_max") val amountMax: Long = 10_000_000,
    @ColumnInfo(name = "default_account_id") val defaultAccountId: Long? = null,
    @ColumnInfo(name = "default_category_id") val defaultCategoryId: Long? = null,
    @ColumnInfo(name = "hit_count") val hitCount: Int = 0,
    @ColumnInfo(name = "confirmed_count") val confirmedCount: Int = 0,
    @ColumnInfo(name = "rejected_count") val rejectedCount: Int = 0,
    @ColumnInfo(name = "last_hit_at") val lastHitAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["category_id"])]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** total / category */
    val scope: String,
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    /** month / year */
    val period: String,
    val amount: Long,
    @ColumnInfo(name = "start_date") val startDate: Long,
    val enabled: Boolean = true
)

@Entity(
    tableName = "backup_history",
    indices = [Index(value = ["created_at"]), Index(value = ["trigger", "created_at"])]
)
data class BackupHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "file_name") val fileName: String,
    /** SAF 返回的 document uri，或自动备份的私有目录路径 */
    @ColumnInfo(name = "location") val location: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long = 0,
    val trigger: String,
    val encrypted: Boolean = true,
    @ColumnInfo(name = "txn_count") val txnCount: Int = 0,
    @ColumnInfo(name = "account_count") val accountCount: Int = 0,
    @ColumnInfo(name = "schema_version") val schemaVersion: Int = 1,
    val status: String,
    val error: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String
)
