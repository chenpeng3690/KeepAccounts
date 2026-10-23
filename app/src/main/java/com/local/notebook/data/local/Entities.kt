package com.local.notebook.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "account")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val initialBalance: Long,
    val currentBalance: Long,
    val creditLimit: Long? = null,
    val billDay: Int? = null,
    val repayDay: Int? = null,
    val includeInNetWorth: Boolean = true,
    val icon: String = "账",
    val colorHex: String = "#3B5BDB",
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "category")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val icon: String,
    val tone: String,
    val parentId: Long? = null,
    val sortOrder: Int = 0,
    val isBuiltin: Boolean = true
)

@Entity(
    tableName = "txn",
    indices = [
        Index("occurredAt"),
        Index("deletedAt"),
        Index("accountId"),
        Index("categoryId"),
        Index("status")
    ]
)
data class TxnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val amount: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val merchant: String? = null,
    val note: String? = null,
    val occurredAt: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val source: String = "MANUAL",
    val ruleId: Long? = null,
    val rawNotificationId: Long? = null,
    val status: String = "NORMAL",
    val deletedAt: Long? = null,
    val deleteReason: String? = null,
    val editCount: Int = 0,
    val dedupeKey: String? = null
)

@Entity(tableName = "txn_edit_log", indices = [Index("txnId")])
data class TxnEditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val txnId: Long,
    val beforeJson: String,
    val afterJson: String,
    val editedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "budget")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scope: String,
    val categoryId: Long? = null,
    val period: String,
    val amount: Long,
    val startDate: Long
)

@Entity(tableName = "notification_raw")
data class NotificationRawEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appLabel: String? = null,
    val title: String? = null,
    val text: String? = null,
    val postedAt: Long,
    val receivedAt: Long,
    val groupKey: String? = null,
    val ruleId: Long? = null,
    val matchResult: String,
    val rejectReason: String? = null
)

@Entity(tableName = "rule")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val packageName: String? = null,
    val priority: Int = 0,
    val isBuiltin: Boolean = false,
    val state: String = "OBSERVING",
    val templateJson: String = "",
    val directionWords: String = "",
    val excludeKeywords: String = "",
    val defaultAccountId: Long? = null,
    val defaultCategoryId: Long? = null,
    val amountHint: Long? = null,
    val statsHits: Int = 0,
    val statsConfirmed: Int = 0,
    val statsRejected: Int = 0,
    val lastHitAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "backup_record")
data class BackupRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val createdAt: Long,
    val sizeBytes: Long,
    val encrypted: Boolean,
    val txnCount: Int,
    val accountCount: Int,
    val schemaVersion: Int,
    val type: String
)

/** 账单列表联表查询结果。 */
data class TxnRow(
    val id: Long,
    val type: String,
    val amount: Long,
    val accountId: Long,
    val toAccountId: Long?,
    val categoryId: Long?,
    val merchant: String?,
    val note: String?,
    val occurredAt: Long,
    val source: String,
    val status: String,
    val editCount: Int,
    val deletedAt: Long?,
    val accountName: String?,
    val toAccountName: String?,
    val categoryName: String?,
    val categoryIcon: String?,
    val categoryTone: String?
)

/** 待确认箱联表结果，附带通知原文与规则状态。 */
data class PendingRow(
    val id: Long,
    val type: String,
    val amount: Long,
    val merchant: String?,
    val note: String?,
    val occurredAt: Long,
    val accountId: Long,
    val categoryId: Long?,
    val ruleId: Long?,
    val rawNotificationId: Long?,
    val accountName: String?,
    val categoryName: String?,
    val categoryIcon: String?,
    val categoryTone: String?,
    val ruleName: String?,
    val ruleState: String?,
    val appLabel: String?,
    val packageName: String?,
    val rawTitle: String?,
    val rawText: String?,
    val rawReceivedAt: Long?,
    val rawResult: String?
)
