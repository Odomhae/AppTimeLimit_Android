package com.odom.applimit

import android.app.Application
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.ads.MobileAds
import com.odom.applimit.worker.DailyResetWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.Calendar
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class AppLimitApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this) {}
        scheduleDailyResetIfNeeded()
    }

    private fun scheduleDailyResetIfNeeded() {
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
        // KEEP preserves any existing scheduled reset so the chain stays alive across app restarts
        WorkManager.getInstance(this).enqueueUniqueWork(
            "daily_limit_reset",
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
