package com.local.notebook.service.feedback

import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppForegroundState @Inject constructor() {
    private val startedActivities = AtomicInteger(0)

    val isForeground: Boolean
        get() = startedActivities.get() > 0

    fun onActivityStarted() {
        startedActivities.incrementAndGet()
    }

    fun onActivityStopped() {
        startedActivities.updateAndGet { value -> (value - 1).coerceAtLeast(0) }
    }
}
