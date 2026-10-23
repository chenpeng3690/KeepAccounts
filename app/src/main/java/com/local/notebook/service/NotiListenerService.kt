package com.local.notebook.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.local.notebook.domain.parser.NotificationPayload
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotiListenerService : NotificationListenerService() {

    @Inject
    lateinit var processor: NotificationProcessor

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val payload = sbn.toPayload() ?: return
        scope.launch {
            runCatching { processor.process(payload) }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun StatusBarNotification.toPayload(): NotificationPayload? {
        val notification = notification ?: return null
        val extras = notification.extras

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val bodyParts = listOf(
            Notification.EXTRA_TEXT,
            Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_INFO_TEXT
        ).mapNotNull { key ->
            extras.getCharSequence(key)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        }.distinct()
        val body = bodyParts.joinToString("\n")

        if (title.isEmpty() && body.isEmpty()) return null

        val label = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        }.getOrDefault(packageName)

        return NotificationPayload(
            packageName = packageName,
            appLabel = label,
            title = title,
            text = body,
            notificationKey = key ?: "${packageName}:${postTime}",
            groupKey = groupKey,
            category = notification.category,
            postedAt = postTime,
            receivedAt = System.currentTimeMillis(),
            isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        )
    }
}
