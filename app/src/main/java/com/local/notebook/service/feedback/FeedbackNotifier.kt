package com.local.notebook.service.feedback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.local.notebook.MainActivity
import com.local.notebook.R
import com.local.notebook.data.repository.SettingsRepository
import com.local.notebook.service.FeedbackActionReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalTime
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private data class AutoFeedback(
    val txnId: Long,
    val amount: Long,
    val merchant: String?,
    val category: String?,
    val account: String?,
    val appLabel: String,
    val at: Long
)

@Singleton
class FeedbackNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val foregroundState: AppForegroundState,
    private val bus: FeedbackBus
) {
    private val autoEvents = mutableListOf<AutoFeedback>()
    private val lock = Any()
    private var hasPostedAuto = false
    private val moneyFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))

    suspend fun onAutoPosted(
        txnId: Long,
        amount: Long,
        merchant: String?,
        category: String?,
        account: String?,
        appLabel: String
    ) {
        if (!shouldNotify(success = true)) return
        val quiet = isQuietNow()

        if (foregroundState.isForeground) {
            if (quiet) return
            bus.publish(
                FeedbackEvent(
                    kind = FeedbackKind.AUTO,
                    title = "已记账 ¥${money(amount)}",
                    body = listOfNotNull(merchant, category, account).joinToString(" · "),
                    txnId = txnId
                )
            )
            return
        }

        if (!canPostNotifications()) return
        val (list, mergedTitle, mergedBody) = synchronized(lock) {
            val now = System.currentTimeMillis()
            if (autoEvents.isNotEmpty() && now - autoEvents.last().at > MERGE_WINDOW_MS) {
                autoEvents.clear()
            }
            autoEvents.add(
                AutoFeedback(txnId, amount, merchant, category, account, appLabel, now)
            )
            val snapshot = autoEvents.toList()
            val total = snapshot.sumOf { it.amount }
            val title = if (snapshot.size == 1) {
                "已记账 ¥${money(amount)}"
            } else {
                "已记账 ${snapshot.size} 笔，共 ¥${money(total)}"
            }
            val body = if (snapshot.size == 1) {
                listOfNotNull(merchant, category, account).joinToString(" · ")
            } else {
                "最近：${snapshot.last().merchant ?: snapshot.last().appLabel}"
            }
            Triple(snapshot, title, body)
        }

        val last = list.last()
        val channel = if (quiet) NotificationChannels.FEEDBACK_QUIET else NotificationChannels.FEEDBACK
        val style = if (list.size > 1) {
            val inbox = NotificationCompat.InboxStyle()
            inbox.setBigContentTitle(mergedTitle)
            list.take(6).forEach { event ->
                inbox.addLine("${event.merchant ?: event.appLabel}  ¥${money(event.amount)}")
            }
            inbox
        } else {
            NotificationCompat.BigTextStyle().bigText(mergedBody)
        }
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_check)
            .setContentTitle(mergedTitle)
            .setContentText(mergedBody)
            .setStyle(style)
            .setPriority(if (quiet) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(hasPostedAuto)
            .setContentIntent(viewIntent("notiledger://txn/${last.txnId}"))
            .addAction(
                0,
                "撤销",
                broadcastIntent(FeedbackActionReceiver.ACTION_UNDO, last.txnId)
            )
            .apply {
                if (quiet) setSilent(true)
            }
        NotificationManagerCompat.from(context).notify(AUTO_NOTIFICATION_ID, builder.build())
        hasPostedAuto = true
    }

    suspend fun onPending(
        txnId: Long,
        amount: Long,
        appLabel: String,
        merchant: String?
    ) {
        if (!shouldNotify(success = false)) return
        val quiet = isQuietNow()
        val title = "有一笔待确认 ¥${money(amount)}"
        val body = "$appLabel · ${merchant ?: "商户未识别"}，点一下确认"

        if (foregroundState.isForeground) {
            if (quiet) return
            bus.publish(
                FeedbackEvent(
                    kind = FeedbackKind.PENDING,
                    title = title,
                    body = body,
                    pendingTxnId = txnId
                )
            )
            return
        }

        if (!canPostNotifications()) return
        val channel = if (quiet) NotificationChannels.FEEDBACK_QUIET else NotificationChannels.FEEDBACK
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_check)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (quiet) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(viewIntent("notiledger://pending"))
            .addAction(
                0,
                "确认入账",
                broadcastIntent(FeedbackActionReceiver.ACTION_CONFIRM, txnId)
            )
            .apply {
                if (quiet) setSilent(true)
            }
        NotificationManagerCompat.from(context).notify(pendingNotificationId(txnId), builder.build())
    }

    private suspend fun shouldNotify(success: Boolean): Boolean {
        return when (settings.feedbackMode.first()) {
            "NONE" -> false
            "SUCCESS" -> success
            else -> true
        }
    }

    private suspend fun isQuietNow(): Boolean {
        if (!settings.quietEnabled.first()) return false
        val start = parseTime(settings.quietStart.first()) ?: return false
        val end = parseTime(settings.quietEnd.first()) ?: return false
        val now = LocalTime.now()
        return if (start <= end) {
            now >= start && now < end
        } else {
            now >= start || now < end
        }
    }

    private fun canPostNotifications(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun parseTime(value: String): LocalTime? = runCatching {
        val parts = value.split(":")
        LocalTime.of(parts[0].toInt(), parts[1].toInt())
    }.getOrNull()

    private fun viewIntent(uri: String): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            uri.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun broadcastIntent(action: String, txnId: Long): PendingIntent {
        val intent = Intent(context, FeedbackActionReceiver::class.java)
            .setAction(action)
            .putExtra(FeedbackActionReceiver.EXTRA_TXN_ID, txnId)
        return PendingIntent.getBroadcast(
            context,
            txnId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun money(cents: Long): String = moneyFormat.format(cents / 100.0)

    companion object {
        const val AUTO_NOTIFICATION_ID = 1001
        private const val MERGE_WINDOW_MS = 60_000L

        fun pendingNotificationId(txnId: Long): Int = 2000 + (txnId % 100_000L).toInt()
    }
}
