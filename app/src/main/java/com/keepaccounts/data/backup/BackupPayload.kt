package com.keepaccounts.data.backup

import com.keepaccounts.data.local.db.AccountEntity
import com.keepaccounts.data.local.db.AppSettingEntity
import com.keepaccounts.data.local.db.BudgetEntity
import com.keepaccounts.data.local.db.CategoryEntity
import com.keepaccounts.data.local.db.RuleEntity
import com.keepaccounts.data.local.db.TransactionEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * 备份包的内容。
 *
 * 刻意导出「逻辑数据」而不是直接拷贝 .db 文件：
 * 数据库被 SQLCipher 用**设备绑定的 Keystore 密钥**加密，把文件拷到新手机上是打不开的。
 * 导成 JSON 才能跨设备。
 */
data class BackupPayload(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val createdAt: Long = System.currentTimeMillis(),
    val appVersion: String = "0.1.0",
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val rules: List<RuleEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val settings: List<AppSettingEntity> = emptyList()
) {
    fun toJson(): String {
        val root = JSONObject()
        root.put("schemaVersion", schemaVersion)
        root.put("createdAt", createdAt)
        root.put("appVersion", appVersion)
        root.put("accounts", JSONArray().also { arr -> accounts.forEach { arr.put(it.toJson()) } })
        root.put("categories", JSONArray().also { arr -> categories.forEach { arr.put(it.toJson()) } })
        root.put("transactions", JSONArray().also { arr -> transactions.forEach { arr.put(it.toJson()) } })
        root.put("rules", JSONArray().also { arr -> rules.forEach { arr.put(it.toJson()) } })
        root.put("budgets", JSONArray().also { arr -> budgets.forEach { arr.put(it.toJson()) } })
        root.put("settings", JSONArray().also { arr -> settings.forEach { arr.put(it.toJson()) } })
        return root.toString()
    }

    companion object {
        const val CURRENT_SCHEMA = 1

        fun fromJson(text: String): BackupPayload {
            val root = JSONObject(text)
            return BackupPayload(
                schemaVersion = root.optInt("schemaVersion", 1),
                createdAt = root.optLong("createdAt", 0L),
                appVersion = root.optString("appVersion", "unknown"),
                accounts = root.optJSONArray("accounts").mapObjects { it.toAccount() },
                categories = root.optJSONArray("categories").mapObjects { it.toCategory() },
                transactions = root.optJSONArray("transactions").mapObjects { it.toTransaction() },
                rules = root.optJSONArray("rules").mapObjects { it.toRule() },
                budgets = root.optJSONArray("budgets").mapObjects { it.toBudget() },
                settings = root.optJSONArray("settings").mapObjects {
                    AppSettingEntity(it.getString("key"), it.optString("value", ""))
                }
            )
        }
    }
}

/* ---------------- 写 ---------------- */

private fun JSONObject.putNullable(key: String, value: Any?) {
    put(key, value ?: JSONObject.NULL)
}

private fun AccountEntity.toJson() = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("type", type)
    put("initialBalance", initialBalance)
    put("currentBalance", currentBalance)
    putNullable("cardTail", cardTail)
    putNullable("creditLimit", creditLimit)
    putNullable("billDay", billDay)
    putNullable("repayDay", repayDay)
    put("includeInNetWorth", includeInNetWorth)
    put("icon", icon)
    put("color", color)
    put("sortOrder", sortOrder)
    put("archived", archived)
    put("createdAt", createdAt)
}

private fun CategoryEntity.toJson() = JSONObject().apply {
    put("id", id)
    putNullable("parentId", parentId)
    put("name", name)
    put("type", type)
    put("icon", icon)
    put("color", color)
    put("sortOrder", sortOrder)
    put("hidden", hidden)
    put("createdAt", createdAt)
}

private fun TransactionEntity.toJson() = JSONObject().apply {
    put("id", id)
    put("type", type)
    put("amount", amount)
    put("occurredAt", occurredAt)
    put("accountId", accountId)
    putNullable("toAccountId", toAccountId)
    putNullable("categoryId", categoryId)
    putNullable("merchant", merchant)
    putNullable("note", note)
    put("source", source)
    put("confidence", confidence)
    put("status", status)
    putNullable("refundOf", refundOf)
    putNullable("rawEventId", rawEventId)
    putNullable("ruleId", ruleId)
    putNullable("deletedAt", deletedAt)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}

private fun RuleEntity.toJson() = JSONObject().apply {
    put("id", id)
    put("name", name)
    putNullable("packageName", packageName)
    put("priority", priority)
    put("builtin", builtin)
    put("state", state)
    put("templateJson", templateJson)
    putNullable("expenseWords", expenseWords)
    putNullable("incomeWords", incomeWords)
    putNullable("excludeKeywords", excludeKeywords)
    put("amountMax", amountMax)
    putNullable("defaultAccountId", defaultAccountId)
    putNullable("defaultCategoryId", defaultCategoryId)
    put("hitCount", hitCount)
    put("confirmedCount", confirmedCount)
    put("rejectedCount", rejectedCount)
    putNullable("lastHitAt", lastHitAt)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}

private fun BudgetEntity.toJson() = JSONObject().apply {
    put("id", id)
    put("scope", scope)
    putNullable("categoryId", categoryId)
    put("period", period)
    put("amount", amount)
    put("startDate", startDate)
    put("enabled", enabled)
}

/* ---------------- 读 ---------------- */

private fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    val out = ArrayList<T>(length())
    for (i in 0 until length()) {
        val item = optJSONObject(i) ?: continue
        out.add(transform(item))
    }
    return out
}

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (isNull(key)) null else optLong(key)

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (isNull(key)) null else optInt(key)

private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key)

private fun JSONObject.toAccount() = AccountEntity(
    id = getLong("id"),
    name = getString("name"),
    type = getString("type"),
    initialBalance = optLong("initialBalance", 0),
    currentBalance = optLong("currentBalance", 0),
    cardTail = optStringOrNull("cardTail"),
    creditLimit = optLongOrNull("creditLimit"),
    billDay = optIntOrNull("billDay"),
    repayDay = optIntOrNull("repayDay"),
    includeInNetWorth = optBoolean("includeInNetWorth", true),
    icon = optString("icon", "wallet"),
    color = optString("color", "#3B5BDB"),
    sortOrder = optInt("sortOrder", 0),
    archived = optBoolean("archived", false),
    createdAt = optLong("createdAt", 0)
)

private fun JSONObject.toCategory() = CategoryEntity(
    id = getLong("id"),
    parentId = optLongOrNull("parentId"),
    name = getString("name"),
    type = getString("type"),
    icon = optString("icon", "more"),
    color = optString("color", "#A0A6B0"),
    sortOrder = optInt("sortOrder", 0),
    hidden = optBoolean("hidden", false),
    createdAt = optLong("createdAt", 0)
)

private fun JSONObject.toTransaction() = TransactionEntity(
    id = getLong("id"),
    type = getString("type"),
    amount = getLong("amount"),
    occurredAt = getLong("occurredAt"),
    accountId = getLong("accountId"),
    toAccountId = optLongOrNull("toAccountId"),
    categoryId = optLongOrNull("categoryId"),
    merchant = optStringOrNull("merchant"),
    note = optStringOrNull("note"),
    source = optString("source", "manual"),
    confidence = optString("confidence", "high"),
    status = optString("status", "confirmed"),
    refundOf = optLongOrNull("refundOf"),
    rawEventId = optLongOrNull("rawEventId"),
    ruleId = optLongOrNull("ruleId"),
    deletedAt = optLongOrNull("deletedAt"),
    createdAt = optLong("createdAt", 0),
    updatedAt = optLong("updatedAt", 0)
)

private fun JSONObject.toRule() = RuleEntity(
    id = getLong("id"),
    name = getString("name"),
    packageName = optStringOrNull("packageName"),
    priority = optInt("priority", 100),
    builtin = optBoolean("builtin", false),
    state = optString("state", "observing"),
    templateJson = optString("templateJson", "[]"),
    expenseWords = optStringOrNull("expenseWords"),
    incomeWords = optStringOrNull("incomeWords"),
    excludeKeywords = optStringOrNull("excludeKeywords"),
    amountMax = optLong("amountMax", 10_000_000),
    defaultAccountId = optLongOrNull("defaultAccountId"),
    defaultCategoryId = optLongOrNull("defaultCategoryId"),
    hitCount = optInt("hitCount", 0),
    confirmedCount = optInt("confirmedCount", 0),
    rejectedCount = optInt("rejectedCount", 0),
    lastHitAt = optLongOrNull("lastHitAt"),
    createdAt = optLong("createdAt", 0),
    updatedAt = optLong("updatedAt", 0)
)

private fun JSONObject.toBudget() = BudgetEntity(
    id = getLong("id"),
    scope = optString("scope", "total"),
    categoryId = optLongOrNull("categoryId"),
    period = optString("period", "month"),
    amount = optLong("amount", 0),
    startDate = optLong("startDate", 0),
    enabled = optBoolean("enabled", true)
)
