package com.keepaccounts.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.keepaccounts.KeepAccountsApp
import kotlinx.coroutines.launch

/**
 * 通知监听入口。
 *
 * 这个回调在系统进程的调用栈里执行，自己也在主线程上 —— 所以这里只做最便宜的事：
 * 判白名单（内存 HashSet）、L0 类别否决、拼文本（纯内存）、丢给 IO 协程。
 * 任何数据库操作都不在这里做。
 *
 * 顺序也很重要：白名单判断放在最前面，非白名单的通知在纳秒级就返回，
 * 连文本都不解析。这是这类服务最容易忽略的耗电点。
 */
class KaNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return

        if (!NotificationGate.isAllowed(notification.packageName)) return
        if (notification.notification == null) return
        if (isCategoryBlocked(notification.notification)) return
        if (NotificationTextExtractor.isGroupSummary(notification.notification)) return

        val content = NotificationTextExtractor.extract(notification.notification)
        if (content.text.isBlank()) return

        val app = applicationContext as? KeepAccountsApp ?: return
        val container = app.container

        container.applicationScope.launch {
            runCatching {
                val eventId = NotificationCapture.store(app, container, notification, content)
                if (eventId != null) {
                    // 采到就顺手解析：命中即入账，拿不准进待确认箱
                    container.parseRepo.processEvent(eventId)
                }
            }
        }
    }

    /** L0：营销 / 推荐 / 社交 / 系统类通知直接不采，这些永远不会是账单 */
    private fun isCategoryBlocked(n: Notification): Boolean {
        val c = n.category ?: return false
        return c == Notification.CATEGORY_PROMO ||
            c == Notification.CATEGORY_RECOMMENDATION ||
            c == Notification.CATEGORY_SOCIAL ||
            c == Notification.CATEGORY_SYSTEM
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // 授权状态可能在系统设置里变过，连上时顺手刷新一次门禁快照
        val app = applicationContext as? KeepAccountsApp ?: return
        app.container.applicationScope.launch {
            runCatching { NotificationGate.refresh(app.container.settingsRepo) }
        }
    }
}
