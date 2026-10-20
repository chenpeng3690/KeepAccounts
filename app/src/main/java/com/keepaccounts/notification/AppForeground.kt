package com.keepaccounts.notification

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * 前台状态追踪。
 *
 * 记账浮悬提示要做「前台判断」：本 App 在前台时不推系统通知，改走应用内 Snackbar，
 * 避免同一件事提示两次。这里用 ActivityLifecycleCallbacks 维护一个粗略的 started 计数。
 */
object AppForeground : Application.ActivityLifecycleCallbacks {

    @Volatile
    var isForeground: Boolean = false
        private set

    private var started = 0

    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        started++
        isForeground = started > 0
    }

    override fun onActivityStopped(activity: Activity) {
        started--
        isForeground = started <= 0
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
