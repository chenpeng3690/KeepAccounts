package com.local.notebook.service

import com.local.notebook.data.local.NotificationRawEntity
import com.local.notebook.data.repository.LedgerRepository
import com.local.notebook.data.repository.SettingsRepository
import com.local.notebook.domain.keyword.KeywordMatcher
import com.local.notebook.domain.parser.NotificationPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class ProcessOutcome { IGNORED, DUPLICATE, QUEUED }

/**
 * 白名单应用 → 60 秒重复更新 → 关键词初筛 → 落库并交给 AI。
 * 未命中关键词的通知不保存原文。
 */
@Singleton
class NotificationProcessor @Inject constructor(
    private val repository: LedgerRepository,
    private val settings: SettingsRepository,
    private val scheduler: AiParseScheduler
) {

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

        val keywords = repository.enabledKeywords()
        val hit = KeywordMatcher.match(payload.fullText, keywords)
            ?: return@withContext ProcessOutcome.IGNORED

        val rawId = repository.insertRawNotification(
            NotificationRawEntity(
                packageName = payload.packageName,
                appLabel = payload.appLabel,
                title = payload.title,
                text = payload.text,
                postedAt = payload.postedAt,
                receivedAt = payload.receivedAt,
                groupKey = payload.notificationKey,
                matchResult = "QUEUED",
                rejectReason = "关键词命中「$hit」，等待 AI 识别"
            )
        )
        scheduler.enqueue(rawId)
        ProcessOutcome.QUEUED
    }

    private companion object {
        const val DEDUPE_WINDOW_MS = 60_000L
    }
}
