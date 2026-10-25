package com.local.notebook.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.local.notebook.BuildConfig
import com.local.notebook.data.local.AccountEntity
import com.local.notebook.data.local.BudgetEntity
import com.local.notebook.data.local.CategoryEntity
import com.local.notebook.data.local.LedgerDatabase
import com.local.notebook.data.local.NotificationRawEntity
import com.local.notebook.data.local.TransactionKeywordEntity
import com.local.notebook.data.local.TxnEntity
import com.local.notebook.data.repository.LedgerRepository
import com.local.notebook.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class BackupPreview(
    val createdAt: Long,
    val txnCount: Int,
    val accountCount: Int,
    val keywordCount: Int,
    val appVersion: String,
    val schemaVersion: Int
)

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: LedgerDatabase,
    private val settings: SettingsRepository,
    private val repository: LedgerRepository
) {
    suspend fun writeBackup(output: OutputStream) = withContext(Dispatchers.IO) {
        val accounts = db.accountDao().getAll()
        val categories = db.categoryDao().getAll()
        val txns = db.txnDao().getAllOnce()
        val budgets = db.budgetDao().getAllOnce()
        val keywords = db.transactionKeywordDao().getAllOnce()
        val raws = db.notificationRawDao().getAllOnce()

        val entries = linkedMapOf<String, ByteArray>()
        entries["data/accounts.csv"] = CsvCodec.encode(
            ACCOUNT_HEADER,
            accounts.map { it.toCsvRow() }
        ).toByteArray(Charsets.UTF_8)
        entries["data/categories.csv"] = CsvCodec.encode(
            CATEGORY_HEADER,
            categories.map { it.toCsvRow() }
        ).toByteArray(Charsets.UTF_8)
        entries["data/transactions.csv"] = CsvCodec.encode(
            TXN_HEADER,
            txns.map { it.toCsvRow() }
        ).toByteArray(Charsets.UTF_8)
        entries["data/budgets.csv"] = CsvCodec.encode(
            BUDGET_HEADER,
            budgets.map { it.toCsvRow() }
        ).toByteArray(Charsets.UTF_8)
        entries["data/notification_raw.csv"] = CsvCodec.encode(
            RAW_HEADER,
            raws.map { it.toCsvRow() }
        ).toByteArray(Charsets.UTF_8)
        entries["data/keywords.json"] = keywordsToJson(keywords)
        entries["data/settings.json"] = settingsToJson()

        val fileHashes = JSONObject()
        entries.forEach { (name, bytes) ->
            fileHashes.put(name, sha256(bytes))
        }
        val manifest = JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("appVersion", BuildConfig.VERSION_NAME)
            put("createdAt", System.currentTimeMillis())
            put("accounts", accounts.size)
            put("categories", categories.size)
            put("transactions", txns.size)
            put("keywords", keywords.size)
            put("files", fileHashes)
        }
        entries["manifest.json"] = manifest.toString().toByteArray(Charsets.UTF_8)

        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    suspend fun writeBackupToFile(file: File) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        file.outputStream().use { output -> writeBackup(output) }
    }

    suspend fun readPreview(uri: Uri): BackupPreview = withContext(Dispatchers.IO) {
        val entries = readEntries(uri)
        val manifestBytes = entries["manifest.json"] ?: error("不是有效的备份文件")
        val manifest = JSONObject(manifestBytes.toString(Charsets.UTF_8))
        BackupPreview(
            createdAt = manifest.optLong("createdAt"),
            txnCount = manifest.optInt("transactions"),
            accountCount = manifest.optInt("accounts"),
            keywordCount = manifest.optInt("keywords", manifest.optInt("rules")),
            appVersion = manifest.optString("appVersion", "未知"),
            schemaVersion = manifest.optInt("schemaVersion", 1)
        )
    }

    suspend fun restore(uri: Uri) = withContext(Dispatchers.IO) {
        val entries = readEntries(uri)
        val manifestBytes = entries["manifest.json"] ?: error("不是有效的备份文件")
        JSONObject(manifestBytes.toString(Charsets.UTF_8))

        val accounts = parseRows(entries["data/accounts.csv"]).map { it.toAccount() }
        val categories = parseRows(entries["data/categories.csv"]).map { it.toCategory() }
        val txns = parseRows(entries["data/transactions.csv"]).map { it.toTxn() }
        val budgets = parseRows(entries["data/budgets.csv"]).map { it.toBudget() }
        val raws = parseRows(entries["data/notification_raw.csv"]).map { it.toRaw() }
        val keywords = entries["data/keywords.json"]?.let { jsonToKeywords(it) } ?: emptyList()

        db.withTransaction {
            db.txnEditLogDao().deleteAll()
            db.txnDao().deleteAll()
            db.notificationRawDao().deleteAll()
            db.budgetDao().deleteAll()
            db.transactionKeywordDao().deleteAll()
            db.categoryDao().deleteAll()
            db.accountDao().deleteAll()

            if (accounts.isNotEmpty()) db.accountDao().insertAll(accounts)
            if (categories.isNotEmpty()) db.categoryDao().insertAll(categories)
            if (raws.isNotEmpty()) db.notificationRawDao().insertAll(raws)
            if (keywords.isNotEmpty()) db.transactionKeywordDao().insertAll(keywords)
            if (budgets.isNotEmpty()) db.budgetDao().insertAll(budgets)
            if (txns.isNotEmpty()) db.txnDao().insertAll(txns)
        }

        entries["data/settings.json"]?.let { restoreSettings(it) }
        repository.ensureSeeded()
        repository.recalculateBalances()
    }

    private suspend fun settingsToJson(): ByteArray {
        val whitelist = settings.whitelistPackages.first()
        val json = JSONObject().apply {
            put("whitelist", JSONArray(whitelist.toList()))
            put("trashRetentionDays", settings.trashRetentionDays.first())
            put("themeMode", settings.themeMode.first())
            put("feedbackMode", settings.feedbackMode.first())
            put("quietEnabled", settings.quietEnabled.first())
            put("quietStart", settings.quietStart.first())
            put("quietEnd", settings.quietEnd.first())
            put("aiEnabled", settings.aiEnabled.first())
            put("aiMode", settings.aiMode.first())
            put("aiBaseUrl", settings.aiBaseUrl.first())
            put("aiModel", settings.aiModel.first())
            put("aiConfidencePercent", settings.aiConfidencePercent.first())
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    private suspend fun restoreSettings(bytes: ByteArray) {
        val json = runCatching { JSONObject(bytes.toString(Charsets.UTF_8)) }.getOrNull() ?: return
        val whitelistArray = json.optJSONArray("whitelist") ?: JSONArray()
        val whitelist = buildSet {
            for (index in 0 until whitelistArray.length()) {
                whitelistArray.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
        settings.restorePreferences(
            whitelist = whitelist,
            trashRetentionDays = json.optInt("trashRetentionDays", 7),
            themeMode = json.optString("themeMode", "SYSTEM"),
            feedbackMode = json.optString("feedbackMode", "ALL"),
            quietEnabled = json.optBoolean("quietEnabled", true),
            quietStart = json.optString("quietStart", "23:00"),
            quietEnd = json.optString("quietEnd", "07:00"),
            aiEnabled = json.optBoolean("aiEnabled", false),
            aiMode = json.optString("aiMode", "DIRECT"),
            aiBaseUrl = json.optString("aiBaseUrl", "https://api.deepseek.com"),
            aiModel = json.optString("aiModel", "deepseek-chat"),
            aiConfidencePercent = json.optInt("aiConfidencePercent", 70)
        )
    }

    private fun keywordsToJson(keywords: List<TransactionKeywordEntity>): ByteArray {
        val array = JSONArray()
        keywords.forEach { keyword ->
            array.put(
                JSONObject().apply {
                    put("word", keyword.word)
                    put("enabled", keyword.enabled)
                    put("isBuiltin", keyword.isBuiltin)
                    put("sortOrder", keyword.sortOrder)
                }
            )
        }
        return array.toString().toByteArray(Charsets.UTF_8)
    }

    private fun jsonToKeywords(bytes: ByteArray): List<TransactionKeywordEntity> {
        val array = runCatching { JSONArray(bytes.toString(Charsets.UTF_8)) }.getOrNull()
            ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val word = obj.optString("word").trim()
            if (word.isEmpty()) return@mapNotNull null
            TransactionKeywordEntity(
                word = word,
                enabled = obj.optBoolean("enabled", true),
                isBuiltin = obj.optBoolean("isBuiltin", false),
                sortOrder = obj.optInt("sortOrder", index)
            )
        }
    }

    private fun readEntries(uri: Uri): Map<String, ByteArray> {
        val stream: InputStream = context.contentResolver.openInputStream(uri)
            ?: error("无法读取备份文件")
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(BufferedInputStream(stream)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return entries
    }

    private fun parseRows(bytes: ByteArray?): List<Map<String, String>> {
        if (bytes == null) return emptyList()
        val rows = CsvCodec.parse(bytes.toString(Charsets.UTF_8))
        if (rows.size < 2) return emptyList()
        val header = rows.first()
        return rows.drop(1).map { row ->
            header.indices.associate { index ->
                header[index] to row.getOrElse(index) { "" }
            }
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun AccountEntity.toCsvRow(): List<String?> = listOf(
        id.toString(), name, type, initialBalance.toString(), currentBalance.toString(),
        creditLimit?.toString(), billDay?.toString(), repayDay?.toString(),
        includeInNetWorth.toString(), icon, colorHex, sortOrder.toString(),
        archived.toString(), createdAt.toString()
    )

    private fun CategoryEntity.toCsvRow(): List<String?> = listOf(
        id.toString(), name, type, icon, tone, parentId?.toString(),
        sortOrder.toString(), isBuiltin.toString()
    )

    private fun TxnEntity.toCsvRow(): List<String?> = listOf(
        id.toString(), type, amount.toString(), accountId.toString(), toAccountId?.toString(),
        categoryId?.toString(), merchant, note, occurredAt.toString(), createdAt.toString(),
        updatedAt.toString(), source, ruleId?.toString(), rawNotificationId?.toString(),
        status, deletedAt?.toString(), deleteReason, editCount.toString(), dedupeKey
    )

    private fun BudgetEntity.toCsvRow(): List<String?> = listOf(
        id.toString(), scope, categoryId?.toString(), period, amount.toString(), startDate.toString()
    )

    private fun NotificationRawEntity.toCsvRow(): List<String?> = listOf(
        id.toString(), packageName, appLabel, title, text, postedAt.toString(),
        receivedAt.toString(), groupKey, ruleId?.toString(), matchResult, rejectReason,
        aiResponse, aiModel, aiError
    )

    private fun Map<String, String>.toAccount(): AccountEntity = AccountEntity(
        id = this["id"]?.toLongOrNull() ?: 0L,
        name = this["name"].orEmpty(),
        type = this["type"].orEmpty().ifBlank { "OTHER" },
        initialBalance = this["initialBalance"]?.toLongOrNull() ?: 0L,
        currentBalance = this["currentBalance"]?.toLongOrNull() ?: 0L,
        creditLimit = this["creditLimit"]?.toLongOrNull(),
        billDay = this["billDay"]?.toIntOrNull(),
        repayDay = this["repayDay"]?.toIntOrNull(),
        includeInNetWorth = this["includeInNetWorth"]?.toBooleanStrictOrNull() ?: true,
        icon = this["icon"].orEmpty().ifBlank { "账" },
        colorHex = this["colorHex"].orEmpty().ifBlank { "#3B5BDB" },
        sortOrder = this["sortOrder"]?.toIntOrNull() ?: 0,
        archived = this["archived"]?.toBooleanStrictOrNull() ?: false,
        createdAt = this["createdAt"]?.toLongOrNull() ?: System.currentTimeMillis()
    )

    private fun Map<String, String>.toCategory(): CategoryEntity = CategoryEntity(
        id = this["id"]?.toLongOrNull() ?: 0L,
        name = this["name"].orEmpty(),
        type = this["type"].orEmpty().ifBlank { "EXPENSE" },
        icon = this["icon"].orEmpty().ifBlank { "其" },
        tone = this["tone"].orEmpty().ifBlank { "GRAY" },
        parentId = this["parentId"]?.toLongOrNull(),
        sortOrder = this["sortOrder"]?.toIntOrNull() ?: 0,
        isBuiltin = this["isBuiltin"]?.toBooleanStrictOrNull() ?: false
    )

    private fun Map<String, String>.toTxn(): TxnEntity = TxnEntity(
        id = this["id"]?.toLongOrNull() ?: 0L,
        type = this["type"].orEmpty().ifBlank { "EXPENSE" },
        amount = this["amount"]?.toLongOrNull() ?: 0L,
        accountId = this["accountId"]?.toLongOrNull() ?: 0L,
        toAccountId = this["toAccountId"]?.toLongOrNull(),
        categoryId = this["categoryId"]?.toLongOrNull(),
        merchant = this["merchant"]?.takeIf { it.isNotBlank() },
        note = this["note"]?.takeIf { it.isNotBlank() },
        occurredAt = this["occurredAt"]?.toLongOrNull() ?: 0L,
        createdAt = this["createdAt"]?.toLongOrNull() ?: System.currentTimeMillis(),
        updatedAt = this["updatedAt"]?.toLongOrNull() ?: System.currentTimeMillis(),
        source = this["source"].orEmpty().ifBlank { "MANUAL" },
        ruleId = this["ruleId"]?.toLongOrNull(),
        rawNotificationId = this["rawNotificationId"]?.toLongOrNull(),
        status = this["status"].orEmpty().ifBlank { "NORMAL" },
        deletedAt = this["deletedAt"]?.toLongOrNull(),
        deleteReason = this["deleteReason"]?.takeIf { it.isNotBlank() },
        editCount = this["editCount"]?.toIntOrNull() ?: 0,
        dedupeKey = this["dedupeKey"]?.takeIf { it.isNotBlank() }
    )

    private fun Map<String, String>.toBudget(): BudgetEntity = BudgetEntity(
        id = this["id"]?.toLongOrNull() ?: 0L,
        scope = this["scope"].orEmpty().ifBlank { "TOTAL" },
        categoryId = this["categoryId"]?.toLongOrNull(),
        period = this["period"].orEmpty().ifBlank { "MONTH" },
        amount = this["amount"]?.toLongOrNull() ?: 0L,
        startDate = this["startDate"]?.toLongOrNull() ?: 0L
    )

    private fun Map<String, String>.toRaw(): NotificationRawEntity = NotificationRawEntity(
        id = this["id"]?.toLongOrNull() ?: 0L,
        packageName = this["packageName"].orEmpty(),
        appLabel = this["appLabel"]?.takeIf { it.isNotBlank() },
        title = this["title"]?.takeIf { it.isNotBlank() },
        text = this["text"]?.takeIf { it.isNotBlank() },
        postedAt = this["postedAt"]?.toLongOrNull() ?: 0L,
        receivedAt = this["receivedAt"]?.toLongOrNull() ?: 0L,
        groupKey = this["groupKey"]?.takeIf { it.isNotBlank() },
        ruleId = this["ruleId"]?.toLongOrNull(),
        matchResult = this["matchResult"].orEmpty().ifBlank { "REJECTED" },
        rejectReason = this["rejectReason"]?.takeIf { it.isNotBlank() },
        aiResponse = this["aiResponse"]?.takeIf { it.isNotBlank() },
        aiModel = this["aiModel"]?.takeIf { it.isNotBlank() },
        aiError = this["aiError"]?.takeIf { it.isNotBlank() }
    )

    private companion object {
        const val SCHEMA_VERSION = 2
        val ACCOUNT_HEADER = listOf(
            "id", "name", "type", "initialBalance", "currentBalance", "creditLimit",
            "billDay", "repayDay", "includeInNetWorth", "icon", "colorHex", "sortOrder",
            "archived", "createdAt"
        )
        val CATEGORY_HEADER = listOf(
            "id", "name", "type", "icon", "tone", "parentId", "sortOrder", "isBuiltin"
        )
        val TXN_HEADER = listOf(
            "id", "type", "amount", "accountId", "toAccountId", "categoryId", "merchant",
            "note", "occurredAt", "createdAt", "updatedAt", "source", "ruleId",
            "rawNotificationId", "status", "deletedAt", "deleteReason", "editCount", "dedupeKey"
        )
        val BUDGET_HEADER = listOf("id", "scope", "categoryId", "period", "amount", "startDate")
        val RAW_HEADER = listOf(
            "id", "packageName", "appLabel", "title", "text", "postedAt", "receivedAt",
            "groupKey", "ruleId", "matchResult", "rejectReason", "aiResponse", "aiModel", "aiError"
        )
    }
}
