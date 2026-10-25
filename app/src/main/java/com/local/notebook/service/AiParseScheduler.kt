package com.local.notebook.service

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiParseScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun enqueue(rawId: Long) {
        if (rawId <= 0) return
        val request = OneTimeWorkRequestBuilder<AiParseWorker>()
            .setInputData(workDataOf(AiParseWorker.KEY_RAW_ID to rawId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.SECONDS
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "ai_parse_$rawId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
