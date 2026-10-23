package com.local.notebook.service.feedback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val FEEDBACK = "ledger_feedback"
    const val FEEDBACK_QUIET = "ledger_feedback_quiet"

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val normal = NotificationChannel(
                FEEDBACK,
                "记账提示",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "自动入账与待确认提醒"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
            val quiet = NotificationChannel(
                FEEDBACK_QUIET,
                "记账提示（静音时段）",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "静音时段只静默投递，不弹横幅"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
            manager.createNotificationChannel(normal)
            manager.createNotificationChannel(quiet)
        }
    }
}
