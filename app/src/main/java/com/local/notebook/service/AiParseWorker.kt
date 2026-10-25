package com.local.notebook.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.local.notebook.data.ai.AiParseRunner
import com.local.notebook.data.ai.AiRunResult
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AiParseWorkerEntryPoint {
    fun aiParseRunner(): AiParseRunner
}

class AiParseWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val rawId = inputData.getLong(KEY_RAW_ID, -1L)
        if (rawId <= 0) return Result.failure()
        val runner = EntryPointAccessors.fromApplication(
            applicationContext,
            AiParseWorkerEntryPoint::class.java
        ).aiParseRunner()

        return when (runner.run(rawId)) {
            AiRunResult.Done -> Result.success()
            AiRunResult.Retry -> {
                if (runAttemptCount < MAX_ATTEMPTS) {
                    Result.retry()
                } else {
                    runner.fallback(rawId, "AI 连续请求失败")
                    Result.success()
                }
            }
        }
    }

    companion object {
        const val KEY_RAW_ID = "rawId"
        private const val MAX_ATTEMPTS = 3
    }
}
