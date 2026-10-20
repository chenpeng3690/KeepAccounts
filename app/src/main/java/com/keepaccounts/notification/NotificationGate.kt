package com.keepaccounts.notification

import android.content.Context
import android.content.pm.PackageManager
import com.keepaccounts.data.local.db.SettingKeys
import com.keepaccounts.data.repo.SettingsRepository

/**
 * 采集门禁。
 *
 * 系统会把**所有**应用的通知都推给监听服务，我们无法只订阅部分包名。
 * 因此省电的关键不是"少收"，而是"收到后第一件事就是用内存里的白名单挡掉" ——
 * 一次 HashSet 判断是纳秒级，一次数据库查询是毫秒级且带 IO，差别在这里。
 *
 * 所以白名单常驻内存（[allowed]），只在设置变更时刷新，绝不在 onNotificationPosted 里查库。
 */
object NotificationGate {

    /** 内置默认白名单：微信、支付宝 */
    val DEFAULT_PACKAGES = listOf(
        "com.tencent.mm",
        "com.eg.android.AlipayGphone"
    )

    @Volatile
    private var allowed: Set<String> = DEFAULT_PACKAGES.toSet()

    @Volatile
    private var captureAll: Boolean = false

    private val labelCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun snapshot(): Set<String> = allowed

    fun isCaptureAll(): Boolean = captureAll

    fun isAllowed(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        if (packageName == "android") return false
        return captureAll || packageName in allowed
    }

    /** 从设置里重新读入白名单。启动时与每次设置变更后调用 */
    suspend fun refresh(settings: SettingsRepository) {
        val raw = settings.get(SettingKeys.WHITELIST)
        val set = if (raw.isNullOrBlank()) {
            DEFAULT_PACKAGES.toSet()
        } else {
            raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }
        allowed = set
        captureAll = settings.getBoolean(SettingKeys.CAPTURE_ALL, false)
    }

    /** 直接覆盖内存快照，供设置页保存后立即生效，省一次读库 */
    fun update(packages: Set<String>, captureAllApps: Boolean) {
        allowed = packages
        captureAll = captureAllApps
    }

    fun encode(packages: Set<String>): String = packages.joinToString(",")

    /** 应用显示名。PackageManager 查询较慢，做一层缓存 */
    fun appLabel(context: Context, packageName: String): String {
        labelCache[packageName]?.let { return it }
        val label = runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrElse { packageName }
        labelCache[packageName] = label
        return label
    }

    fun packageInstalled(context: Context, packageName: String): Boolean =
        runCatching {
            context.packageManager.getApplicationInfo(packageName, 0)
            true
        }.getOrElse { false }
}

/** 已安装应用的轻量描述，供白名单页列表使用 */
data class InstalledApp(
    val packageName: String,
    val label: String
)

fun loadLaunchableApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
        .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
    return runCatching {
        pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    label = pm.getApplicationLabel(info).toString()
                )
            }
            .sortedBy { it.label }
    }.getOrElse { emptyList() }
}
