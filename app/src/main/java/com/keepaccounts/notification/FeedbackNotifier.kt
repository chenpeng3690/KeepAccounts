package com.keepaccounts.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.keepaccounts.MainActivity
import com.keepaccounts.core.AppContainer
import com.keepaccounts.data.local.db.SettingKeys
import com.keepaccounts.data.local.db.TransactionEntity
import com.keepaccounts.util.Money
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap

/** 前台时投给 UI 的事件，用 Snackbar 展示 */
sealed class FeedbackEvent {
    data class Snackbar(val text: String) : FeedbackEvent()
}

/**
 * 记账结果反馈。
 *
 * 用户付款时人在微信/支付宝里，应用内弹窗看不到，所以用系统 heads-up 横幅浮在别的 App 上层。
 * 不需要 SYSTEM_ALERT_WINDOW，只是普通的高优先级通知（静音、不震动）。
 *
 * 防打扰三条：三档开关、静音时段、60 秒合并。
 */
class FeedbackNotifier(
    private val container: AppContainer,
    private val appContext: Context
) {

    companion object {
        const val CHANNEL_ID = "ledger_feedback"
        private const val AGG_WINDOW_MS = 60_000L
    }

    private val _snackbars = MutableSharedFlow<FeedbackEvent>(extraBufferCapacity = 16)
    val snackbars: SharedFlow<FeedbackEvent> = _snackbars.asSharedFlow()

    private data class Agg(val count: Int, val totalCents: Long, val lastAt: Long)
    private val agg = ConcurrentHashMap<Long, Agg>()

    fun ensureChannel() {
        val nm = appContext.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID, "记账提示", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "付款后在本机提示已记账，静音不震动"
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(channel)
    }

    /** 自动入账成功：带「撤销」action */
    suspend fun onAutoPosted(txn: TransactionEntity) {
        val level = container.settingsRepo.get(SettingKeys.FEEDBACK_LEVEL) ?: "all"
        if (level == "off") return
        if (inQuietHours()) return

        val body = listOfNotNull(
            txn.merchant,
            txn.categoryId?.let { id -> container.db.categoryDao().findById(id)?.name },
            container.db.accountDao().findById(txn.accountId)?.name
        ).joinToString(" · ")

        // 60 秒内同一账户的连续支付合并成一条
        val now = System.currentTimeMillis()
        val prev = agg[txn.accountId]
        val (count, total) = if (prev != null && now - prev.lastAt <= AGG_WINDOW_MS) {
            val n = prev.count + 1
            val t = prev.totalCents + txn.amount
            agg[txn.accountId] = Agg(n, t, now)
            n to t
        } else {
            agg[txn.accountId] = Agg(1, txn.amount, now)
            1 to txn.amount
        }

        val title = if (count > 1) "已记账 $count 笔，共 ¥${Money.format(total)}"
        else "已记账 ¥${Money.format(txn.amount)}"

        deliver(title, body, txn.id, undo = true)
    }

    /** 待确认：带「确认入账」action */
    suspend fun onPending(txn: TransactionEntity) {
        val level = container.settingsRepo.get(SettingKeys.FEEDBACK_LEVEL) ?: "all"
        if (level == "off" || level == "success_only") return
        if (inQuietHours()) return

        val merchant = txn.merchant ?: "商户未识别"
        val title = "有一笔待确认 ¥${Money.format(txn.amount)}"
        val body = "$merchant · 点一下确认"

        deliver(title, body, txn.id, undo = false)
    }

    private suspend fun inQuietHours(): Boolean {
        if (!container.settingsRepo.getBoolean(SettingKeys.QUIET_ENABLED, true)) return false
        val start = (container.settingsRepo.get(SettingKeys.QUIET_START) ?: "23:00").toHm()
        val end = (container.settingsRepo.get(SettingKeys.QUIET_END) ?: "07:00").toHm()
        val cal = java.util.Calendar.getInstance()
        val now = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        return if (start <= end) now in start..end
        else now >= start || now <= end // 跨天：23:00 - 07:00
    }

    private fun deliver(title: String, body: String, txnId: Long, undo: Boolean) {
        if (AppForeground.isForeground) {
            _snackbars.tryEmit(FeedbackEvent.Snackbar("$title $body"))
            return
        }
        post(title, body, txnId, undo)
    }

    private fun post(title: String, body: String, txnId: Long, undo: Boolean) {
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) return

        val contentPi = PendingIntent.getActivity(
            appContext, 0,
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(com.keepaccounts.R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(contentPi)

        val action = if (undo) "撤销" to FeedbackActionReceiver.ACTION_UNDO
        else "确认入账" to FeedbackActionReceiver.ACTION_CONFIRM
        val pi = PendingIntent.getBroadcast(
            appContext, txnId.toInt(),
            Intent(appContext, FeedbackActionReceiver::class.java).apply {
                this.action = action.second
                putExtra(FeedbackActionReceiver.EXTRA_TXN_ID, txnId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(0, action.first, pi)

        val nm = appContext.getSystemService(NotificationManager::class.java) ?: return
        nm.notify(txnId.toInt(), builder.build())
    }
}

/** "HH:mm" -> 分钟数；解析失败返回 0 */
private fun String?.toHm(): Int {
    val s = this ?: return 0
    val parts = s.split(":")
    if (parts.size != 2) return 0
    val h = parts[0].toIntOrNull() ?: return 0
    val m = parts[1].toIntOrNull() ?: return 0
    return h * 60 + m
}
