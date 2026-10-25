package com.odom.applimit.service

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.odom.applimit.data.AppDatabase
import com.odom.applimit.notification.UsageNotifier
import com.odom.applimit.overlay.BlockingOverlayManager
import com.odom.applimit.util.PauseManager
import com.odom.applimit.util.UsageStatsHelper
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
import java.util.Date
import java.util.Locale

class UsageMonitorService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var notifier: UsageNotifier
    private lateinit var overlayManager: BlockingOverlayManager
    private lateinit var usageStatsHelper: UsageStatsHelper
    private lateinit var powerManager: PowerManager

    override fun onCreate() {
        super.onCreate()
        notifier = UsageNotifier(this)
        overlayManager = BlockingOverlayManager(this)
        usageStatsHelper = UsageStatsHelper(this)
        powerManager = getSystemService(PowerManager::class.java)
        startForeground(UsageNotifier.NOTIFICATION_ID_SERVICE, notifier.buildServiceNotification())
        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            try {
                val dao = AppDatabase.getInstance(applicationContext).appLimitDao()
                if (stopIfNoLimits(dao.getEnabledLimits().isNotEmpty())) return@launch
                monitorOnce()
            } catch (e: CancellationException) { throw e } catch (_: Exception) {}
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
                delay(nextPollInterval())
            }
        }
    }

    private suspend fun nextPollInterval(): Long {
        val dao = AppDatabase.getInstance(applicationContext).appLimitDao()
        val limits = dao.getEnabledLimits()
        if (limits.isEmpty()) return 10_000L
        val nearLimit = limits.any { limit ->
            val usedMs = usageStatsHelper.getTodayUsageMs(limit.packageName) ?: 0L
            val usedMinutes = ((usedMs / 60_000).toInt() - limit.usageAtResetMinutes).coerceAtLeast(0)
            val effectiveLimit = limit.limitMinutes + limit.snoozedMinutes
            usedMinutes.toFloat() / effectiveLimit >= 0.8f
        }
        return if (nearLimit) 3_000L else 10_000L
    }

    private suspend fun monitorOnce() {
        if (PauseManager.isPaused(applicationContext)) {
            withContext(Dispatchers.Main) { overlayManager.hide() }
            return
        }

        val dao = AppDatabase.getInstance(applicationContext).appLimitDao()
        val limits = dao.getEnabledLimits()
        if (limits.isEmpty()) {
            withContext(Dispatchers.Main) { overlayManager.hide() }
            stopIfNoLimits(false)
            return
        }

        val today = todayString()
        val foregroundPackage = usageStatsHelper.getForegroundApp()
        val exceededPackages = mutableSetOf<String>()

        for (limit in limits) {
            val usedMs = usageStatsHelper.getTodayUsageMs(limit.packageName) ?: continue
            val usedMinutes = ((usedMs / 60_000).toInt() - limit.usageAtResetMinutes).coerceAtLeast(0)
            val effectiveLimit = limit.limitMinutes + limit.snoozedMinutes
            val remainingMinutes = (effectiveLimit - usedMinutes).coerceAtLeast(0)

            when {
                usedMinutes >= effectiveLimit -> {
                    exceededPackages += limit.packageName
                    if (limit.lastBlockedDate != today) {
                        dao.update(limit.copy(lastBlockedDate = today))
                        val appName = getAppName(limit.packageName)
                        notifier.sendBlocked(limit.packageName, appName)
                    }
                    if (foregroundPackage == limit.packageName) {
                        val appName = getAppName(limit.packageName)
                        withContext(Dispatchers.Main) {
                            overlayManager.show(limit.packageName, appName, usedMinutes, effectiveLimit)
                        }
                    }
                }
                usedMinutes.toFloat() / effectiveLimit >= 0.8f -> {
                    if (limit.lastWarningDate != today) {
                        dao.update(limit.copy(lastWarningDate = today))
                        val appName = getAppName(limit.packageName)
                        notifier.sendWarning(limit.packageName, appName, remainingMinutes)
                    }
                }
            }
        }

        // Hide overlay when the user has navigated away from the blocked app,
        // or when the shown app is no longer over its limit (midnight reset,
        // manual reset, limit raised, or limit deleted/disabled).
        val shownPackage = overlayManager.currentPackage
        if (overlayManager.isShowing() &&
            (shownPackage != foregroundPackage || shownPackage !in exceededPackages)
        ) {
            withContext(Dispatchers.Main) { overlayManager.hide() }
        }
    }

    /** 활성 제한이 하나도 없으면 포그라운드 알림을 제거하고 서비스를 종료한다. */
    private fun stopIfNoLimits(hasLimits: Boolean): Boolean {
        if (hasLimits) return false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        return true
    }

    private fun getAppName(packageName: String): String {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0)
            }
            packageManager.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun todayString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
}
