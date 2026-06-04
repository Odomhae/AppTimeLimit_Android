package com.odom.applimit.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.odom.applimit.data.AppDatabase
import java.util.Calendar
import java.util.concurrent.TimeUnit

class DailyResetWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        AppDatabase.getInstance(applicationContext).appLimitDao().resetDailyNotificationFlags()
        scheduleNext(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "daily_limit_reset"

        fun scheduleNext(context: Context) {
            val now = Calendar.getInstance()
            val nextMidnight = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_MONTH, 1)
            }
            val delayMs = nextMidnight.timeInMillis - now.timeInMillis
            val request = OneTimeWorkRequestBuilder<DailyResetWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
