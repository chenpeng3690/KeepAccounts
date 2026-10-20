package com.keepaccounts.notification

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * 通知使用权（NotificationListener）的状态查询与跳转。
 *
 * 这项授权只能由用户在系统设置里手动勾选，App 无法弹框申请 —— 所以界面上
 * 必须有一个随时可见的入口，而不是藏在设置深处。
 */
object NotificationAccess {

    fun isGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)

    fun openListenerSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            // 少数 ROM 砍了这个页面，退回到应用详情页
            .onFailure { openAppDetails(context) }
    }

    fun openAppDetails(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** 是否已豁免电池优化；没豁免的话后台监听很容易被 ROM 杀掉 */
    fun isBatteryUnrestricted(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
}

/**
 * 从通知里把可用的文本拼出来。
 *
 * 只读 EXTRA_TEXT 会漏内容：微信的金额常在 bigText 里，部分银行把摘要放在 subText。
 * 拼接顺序按「信息量」排，后面的如果已被前面的包含就不重复追加。
 */
object NotificationTextExtractor {

    data class Content(val title: String?, val text: String)

    fun extract(notification: Notification): Content {
        val extras: Bundle = notification.extras ?: Bundle.EMPTY

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val baseText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()
        val infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()?.trim().orEmpty()

        val collected = mutableListOf<String>()
        // bigText 通常是完整正文，baseText 只是它的截断版
        if (bigText.isNotEmpty()) collected.add(bigText)
        if (baseText.isNotEmpty() && collected.none { it.contains(baseText) }) collected.add(baseText)
        if (subText.isNotEmpty() && collected.none { it.contains(subText) }) collected.add(subText)
        if (infoText.isNotEmpty() && collected.none { it.contains(infoText) }) collected.add(infoText)

        return Content(title = title, text = collected.joinToString("\n"))
    }

    /** 组摘要只是「你有 N 条新消息」，没有正文，采它只会制造噪音 */
    fun isGroupSummary(notification: Notification): Boolean =
        (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
}
