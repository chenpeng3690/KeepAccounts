package com.local.notebook.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val TrashRetentionDays = intPreferencesKey("trash_retention_days")
        val WhitelistPackages = stringSetPreferencesKey("whitelist_packages")
        val GuideShown = booleanPreferencesKey("notification_guide_shown")
        val AiEnabled = booleanPreferencesKey("ai_enabled")
        val AiMode = stringPreferencesKey("ai_mode")
        val AiBaseUrl = stringPreferencesKey("ai_base_url")
        val AiModel = stringPreferencesKey("ai_model")
        val AiConfidencePercent = intPreferencesKey("ai_confidence_percent")
        val ThemeMode = stringPreferencesKey("theme_mode")
        val FeedbackMode = stringPreferencesKey("feedback_mode")
        val QuietEnabled = booleanPreferencesKey("quiet_enabled")
        val QuietStart = stringPreferencesKey("quiet_start")
        val QuietEnd = stringPreferencesKey("quiet_end")
        val FeedbackPermissionAsked = booleanPreferencesKey("feedback_permission_asked")
        val AutoBackupEnabled = booleanPreferencesKey("auto_backup_enabled")
        val AutoBackupPeriodDays = intPreferencesKey("auto_backup_period_days")
        val LastAutoBackupAt = longPreferencesKey("last_auto_backup_at")
        val NextAutoBackupAt = longPreferencesKey("next_auto_backup_at")
    }

    val trashRetentionDays: Flow<Int> =
        context.settingsDataStore.data.map { prefs -> prefs[Keys.TrashRetentionDays] ?: 7 }

    val whitelistPackages: Flow<Set<String>> =
        context.settingsDataStore.data.map { prefs ->
            prefs[Keys.WhitelistPackages] ?: DEFAULT_WHITELIST
        }

    val guideShown: Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[Keys.GuideShown] ?: false }

    val aiEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[Keys.AiEnabled] ?: false }

    val aiMode: Flow<String> =
        context.settingsDataStore.data.map { prefs -> prefs[Keys.AiMode] ?: "DIRECT" }

    val aiBaseUrl: Flow<String> =
        context.settingsDataStore.data.map { prefs ->
            prefs[Keys.AiBaseUrl] ?: "https://api.deepseek.com"
        }

    val aiModel: Flow<String> =
        context.settingsDataStore.data.map { prefs -> prefs[Keys.AiModel] ?: "deepseek-chat" }

    val aiConfidencePercent: Flow<Int> =
        context.settingsDataStore.data.map { prefs -> prefs[Keys.AiConfidencePercent] ?: 70 }

    val themeMode: Flow<String> =
        context.settingsDataStore.data.map { prefs -> prefs[Keys.ThemeMode] ?: "SYSTEM" }

    val feedbackMode: Flow<String> =
        context.settingsDataStore.data.map { prefs -> prefs[Keys.FeedbackMode] ?: "ALL" }

    val quietEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[Keys.QuietEnabled] ?: true }

    val quietStart: Flow<String> =
        context.settingsDataStore.data.map { prefs -> prefs[Keys.QuietStart] ?: "23:00" }

    val quietEnd: Flow<String> =
        context.settingsDataStore.data.map { prefs -> prefs[Keys.QuietEnd] ?: "07:00" }

    val feedbackPermissionAsked: Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[Keys.FeedbackPermissionAsked] ?: false }

    val autoBackupEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[Keys.AutoBackupEnabled] ?: false }

    val autoBackupPeriodDays: Flow<Int> =
        context.settingsDataStore.data.map { prefs -> prefs[Keys.AutoBackupPeriodDays] ?: 7 }

    val lastAutoBackupAt: Flow<Long> =
        context.settingsDataStore.data.map { prefs -> prefs[Keys.LastAutoBackupAt] ?: 0L }

    val nextAutoBackupAt: Flow<Long> =
        context.settingsDataStore.data.map { prefs -> prefs[Keys.NextAutoBackupAt] ?: 0L }

    suspend fun setTrashRetentionDays(days: Int) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.TrashRetentionDays] = days }
    }

    suspend fun setPackageEnabled(packageName: String, enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            val current = prefs[Keys.WhitelistPackages] ?: DEFAULT_WHITELIST
            prefs[Keys.WhitelistPackages] = if (enabled) current + packageName else current - packageName
        }
    }

    suspend fun addPackage(packageName: String) {
        val normalized = packageName.trim()
        if (normalized.isEmpty()) return
        context.settingsDataStore.edit { prefs ->
            val current = prefs[Keys.WhitelistPackages] ?: DEFAULT_WHITELIST
            prefs[Keys.WhitelistPackages] = current + normalized
        }
    }

    suspend fun setGuideShown(shown: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.GuideShown] = shown }
    }

    suspend fun setAiEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.AiEnabled] = enabled }
    }

    suspend fun setAiMode(mode: String) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.AiMode] = mode }
    }

    suspend fun setAiConfig(baseUrl: String, model: String, confidencePercent: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.AiBaseUrl] = baseUrl.trim()
            prefs[Keys.AiModel] = model.trim()
            prefs[Keys.AiConfidencePercent] = confidencePercent.coerceIn(0, 100)
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.ThemeMode] = mode }
    }

    suspend fun setFeedbackMode(mode: String) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.FeedbackMode] = mode }
    }

    suspend fun setQuietEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.QuietEnabled] = enabled }
    }

    suspend fun setFeedbackPermissionAsked(asked: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.FeedbackPermissionAsked] = asked }
    }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.AutoBackupEnabled] = enabled }
    }

    suspend fun setAutoBackupPeriodDays(days: Int) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.AutoBackupPeriodDays] = days }
    }

    suspend fun setLastAutoBackupAt(at: Long) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.LastAutoBackupAt] = at }
    }

    suspend fun setNextAutoBackupAt(at: Long) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.NextAutoBackupAt] = at }
    }

    suspend fun restorePreferences(
        whitelist: Set<String>,
        trashRetentionDays: Int,
        themeMode: String,
        feedbackMode: String,
        quietEnabled: Boolean,
        quietStart: String,
        quietEnd: String,
        aiEnabled: Boolean,
        aiMode: String,
        aiBaseUrl: String,
        aiModel: String,
        aiConfidencePercent: Int
    ) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.WhitelistPackages] = whitelist
            prefs[Keys.TrashRetentionDays] = trashRetentionDays
            prefs[Keys.ThemeMode] = themeMode
            prefs[Keys.FeedbackMode] = feedbackMode
            prefs[Keys.QuietEnabled] = quietEnabled
            prefs[Keys.QuietStart] = quietStart
            prefs[Keys.QuietEnd] = quietEnd
            prefs[Keys.AiEnabled] = aiEnabled
            prefs[Keys.AiMode] = aiMode
            prefs[Keys.AiBaseUrl] = aiBaseUrl
            prefs[Keys.AiModel] = aiModel
            prefs[Keys.AiConfidencePercent] = aiConfidencePercent.coerceIn(0, 100)
        }
    }

    companion object {
        val DEFAULT_WHITELIST = setOf(
            "com.tencent.mm",
            "com.eg.android.AlipayGphone"
        )
    }
}
