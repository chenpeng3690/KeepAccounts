package com.local.notebook

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.local.notebook.data.repository.LedgerRepository
import com.local.notebook.service.feedback.AppForegroundState
import com.local.notebook.service.feedback.NotificationChannels
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class NotiLedgerApp : Application() {

    @Inject
    lateinit var repository: LedgerRepository

    @Inject
    lateinit var foregroundState: AppForegroundState

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensure(this)
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) = foregroundState.onActivityStarted()
            override fun onActivityStopped(activity: Activity) = foregroundState.onActivityStopped()
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        appScope.launch {
            repository.ensureSeeded()
        }
    }
}
