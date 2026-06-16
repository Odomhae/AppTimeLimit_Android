package com.odom.applimit.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

class UsageStatsHelper(context: Context) {
    private val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)

    fun getTodayUsageMs(packageName: String): Long? {
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = System.currentTimeMillis()
        return try {
            val events = usageStatsManager.queryEvents(startOfDay, now)
            val event = UsageEvents.Event()
            var totalMs = 0L
            var lastForegroundMs = -1L
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.packageName != packageName) continue
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND,
                    UsageEvents.Event.ACTIVITY_RESUMED ->
                        lastForegroundMs = event.timeStamp
                    UsageEvents.Event.MOVE_TO_BACKGROUND,
                    UsageEvents.Event.ACTIVITY_PAUSED -> {
                        if (lastForegroundMs >= 0) {
                            totalMs += event.timeStamp - lastForegroundMs
                            lastForegroundMs = -1L
                        }
                    }
                }
            }
            if (lastForegroundMs >= 0) totalMs += now - lastForegroundMs
            totalMs
        } catch (_: Exception) {
            null
        }
    }

    fun getForegroundApp(): String? {
        val now = System.currentTimeMillis()
        return try {
            val events = usageStatsManager.queryEvents(now - 600_000L, now)
            val event = UsageEvents.Event()
            var currentForeground: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND,
                    UsageEvents.Event.ACTIVITY_RESUMED ->
                        currentForeground = event.packageName
                    UsageEvents.Event.MOVE_TO_BACKGROUND,
                    UsageEvents.Event.ACTIVITY_PAUSED ->
                        if (currentForeground == event.packageName) currentForeground = null
                }
            }
            currentForeground
        } catch (_: Exception) {
            null
        }
    }
}
