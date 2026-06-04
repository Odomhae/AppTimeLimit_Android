package com.odom.applimit.service

import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.PowerManager
import com.odom.applimit.data.AppDatabase
import com.odom.applimit.notification.UsageNotifier
import com.odom.applimit.overlay.BlockingOverlayManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class UsageMonitorService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var notifier: UsageNotifier
    private lateinit var overlayManager: BlockingOverlayManager
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var powerManager: PowerManager

    override fun onCreate() {
        super.onCreate()
        notifier = UsageNotifier(this)
        overlayManager = BlockingOverlayManager(this)
        usageStatsManager = getSystemService(UsageStatsManager::class.java)
        powerManager = getSystemService(PowerManager::class.java)
        startForeground(UsageNotifier.NOTIFICATION_ID_SERVICE, notifier.buildServiceNotification())
        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Run an immediate check so the overlay appears without waiting for the first poll cycle
        scope.launch {
            try { monitorOnce() } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        overlayManager.hide()
        super.onDestroy()
    }

    private fun startMonitoring() {
        scope.launch {
            while (isActive) {
                if (powerManager.isInteractive) {
                    try {
                        monitorOnce()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // skip this poll cycle on transient errors (e.g. permission revoked)
                    }
                }
                // Poll every 3s when any limit is near/over (≥80%), 10s otherwise.
                // Keeps battery impact low during normal usage while tightening the
                // blocking window to ≤3s once the user is close to their limit.
                delay(nextPollInterval())
            }
        }
    }

    private suspend fun nextPollInterval(): Long {
        val dao = AppDatabase.getInstance(applicationContext).appLimitDao()
        val limits = dao.getEnabledLimits()
        if (limits.isEmpty()) return 10_000L
        val nearLimit = limits.any { limit ->
            val usedMs = getTodayUsageMs(limit.packageName) ?: 0L
            val usedMinutes = ((usedMs / 60_000).toInt() - limit.usageAtResetMinutes).coerceAtLeast(0)
            usedMinutes.toFloat() / limit.limitMinutes >= 0.8f
        }
        return if (nearLimit) 3_000L else 10_000L
    }

    private suspend fun monitorOnce() {
        val dao = AppDatabase.getInstance(applicationContext).appLimitDao()
        val limits = dao.getEnabledLimits()
        if (limits.isEmpty()) {
            withContext(Dispatchers.Main) { overlayManager.hide() }
            return
        }

        val today = todayString()
        val foregroundPackage = getForegroundApp()

        for (limit in limits) {
            val usedMs = getTodayUsageMs(limit.packageName) ?: continue
            // Subtract the baseline recorded at the last reset so the service
            // respects the same "effective usage" the UI shows.
            val usedMinutes = ((usedMs / 60_000).toInt() - limit.usageAtResetMinutes).coerceAtLeast(0)
            val limitMinutes = limit.limitMinutes
            val remainingMinutes = (limitMinutes - usedMinutes).coerceAtLeast(0)

            when {
                usedMinutes >= limitMinutes -> {
                    if (limit.lastBlockedDate != today) {
                        dao.update(limit.copy(lastBlockedDate = today))
                        val appName = getAppName(limit.packageName)
                        notifier.sendBlocked(limit.packageName, appName)
                    }
                    if (foregroundPackage == limit.packageName) {
                        val appName = getAppName(limit.packageName)
                        withContext(Dispatchers.Main) {
                            overlayManager.show(limit.packageName, appName, usedMinutes, limitMinutes)
                        }
                    }
                }
                usedMinutes.toFloat() / limitMinutes >= 0.8f -> {
                    if (limit.lastWarningDate != today) {
                        dao.update(limit.copy(lastWarningDate = today))
                        val appName = getAppName(limit.packageName)
                        notifier.sendWarning(limit.packageName, appName, remainingMinutes)
                    }
                }
            }
        }

        // Hide overlay when user has navigated away from the blocked app
        if (overlayManager.isShowing() && overlayManager.currentPackage != foregroundPackage) {
            withContext(Dispatchers.Main) { overlayManager.hide() }
        }
    }

    // Returns today's foreground ms via event replay so the current session is
    // included in real-time (queryUsageStats only commits after app backgrounds).
    private fun getTodayUsageMs(packageName: String): Long? {
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

    private fun getForegroundApp(): String? {
        val now = System.currentTimeMillis()
        // Use a 10-minute window so we detect apps that moved to foreground before the last poll.
        // We track foreground/background transitions to determine the current top app.
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
        return currentForeground
    }

    @Suppress("DEPRECATION")
    private fun getAppName(packageName: String): String {
        return try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun todayString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
}
