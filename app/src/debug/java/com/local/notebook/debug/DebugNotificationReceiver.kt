package com.local.notebook.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.local.notebook.data.repository.SettingsRepository
import com.local.notebook.domain.parser.NotificationPayload
import com.local.notebook.service.NotificationProcessor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 仅 debug 包可用的通知注入入口，用于在真机上验证解析链路：
 * adb shell am broadcast -a com.local.notebook.DEBUG_NOTI \
 *   -n com.local.notebook/.debug.DebugNotificationReceiver \
 *   --es pkg com.tencent.mm --es title 微信支付 --es text "支付成功¥25.00瑞幸咖啡"
 */
@AndroidEntryPoint
class DebugNotificationReceiver : BroadcastReceiver() {

    @Inject
    lateinit var processor: NotificationProcessor

    @Inject
    lateinit var settings: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                if (intent.getBooleanExtra("guideShown", false)) {
                    settings.setGuideShown(true)
                }
                intent.getStringExtra("enablePkg")?.let { settings.setPackageEnabled(it, true) }
                intent.getStringExtra("disablePkg")?.let { settings.setPackageEnabled(it, false) }

                val pkg = intent.getStringExtra("pkg") ?: return@launch
                val title = intent.getStringExtra("title").orEmpty()
                val text = intent.getStringExtra("text").orEmpty()
                val now = System.currentTimeMillis()
                val label = runCatching {
                    context.packageManager.getApplicationLabel(
                        context.packageManager.getApplicationInfo(pkg, 0)
                    ).toString()
                }.getOrDefault(pkg)

                processor.process(
                    NotificationPayload(
                        packageName = pkg,
                        appLabel = label,
                        title = title,
                        text = text,
                        notificationKey = intent.getStringExtra("key") ?: "debug-$now",
                        groupKey = null,
                        category = intent.getStringExtra("category"),
                        postedAt = now,
                        receivedAt = now
                    )
                )
            } finally {
                pending.finish()
            }
        }
    }
}
