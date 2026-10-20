package com.keepaccounts.notification

import android.content.Context
import android.service.notification.StatusBarNotification
import com.keepaccounts.core.AppContainer
import com.keepaccounts.data.local.db.ParseStatus
import com.keepaccounts.data.local.db.RawEventEntity

/**
 * 把一条通知落库。
 *
 * 第 5 步只采集不解析：所有记录都是 [ParseStatus.CAPTURED]，等第 6 步的解析管线接上。
 * 之所以先只采集，是因为微信 / 支付宝的通知文案每个版本都在变，凭想象写规则必然漏，
 * 得先拿真机上的原文样本。
 */
object NotificationCapture {

    private const val DEDUPE_WINDOW_MS = 90_000L

    suspend fun store(
        context: Context,
        container: AppContainer,
        sbn: StatusBarNotification,
        content: NotificationTextExtractor.Content
    ): Boolean {
        val packageName = sbn.packageName ?: return false
        val title = content.title
        val text = content.text
        if (text.isBlank()) return false

        val now = System.currentTimeMillis()
        // 系统可能对同一条通知重复回调，同文案在窗口期内只留一条
        val similar = container.db.rawEventDao()
            .countSimilar(packageName, title, text, now - DEDUPE_WINDOW_MS)
        if (similar > 0) return false

        container.db.rawEventDao().insert(
            RawEventEntity(
                packageName = packageName,
                appLabel = NotificationGate.appLabel(context, packageName),
                title = title,
                text = text,
                postedAt = sbn.postTime,
                receivedAt = now,
                groupKey = sbn.groupKey,
                parseStatus = ParseStatus.CAPTURED,
                createdAt = now
            )
        )
        return true
    }
}
