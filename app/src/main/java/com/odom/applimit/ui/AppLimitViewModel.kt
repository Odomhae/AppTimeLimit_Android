package com.odom.applimit.ui

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odom.applimit.data.AppLimitEntity
import com.odom.applimit.data.AppLimitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class InstalledApp(val packageName: String, val label: String)

@HiltViewModel
class AppLimitViewModel @Inject constructor(
    private val repository: AppLimitRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val limits: StateFlow<List<AppLimitEntity>> = repository.getAllLimits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Stores milliseconds so StateFlow sees a new value on every poll (minutes
    // would deduplicate within the same minute and freeze the UI).
    private val _usageMap = MutableStateFlow<Map<String, Long>>(emptyMap())
    val usageMap: StateFlow<Map<String, Long>> = _usageMap.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val currentLimits = limits.value
                if (currentLimits.isNotEmpty()) {
                    _usageMap.value = currentLimits.associate { entity ->
                        entity.packageName to effectiveUsageMs(entity)
                    }
                }
                delay(3_000L)
            }
        }
    }

    fun effectiveUsageMs(entity: AppLimitEntity): Long =
        (rawUsageMs(entity.packageName) - entity.usageAtResetMinutes * 60_000L).coerceAtLeast(0L)

    fun effectiveUsageMinutes(entity: AppLimitEntity): Int =
        (effectiveUsageMs(entity) / 60_000L).toInt()

    private fun rawUsageMs(packageName: String): Long {
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = System.currentTimeMillis()
        return try {
            val usm = context.getSystemService(UsageStatsManager::class.java)
            val events = usm.queryEvents(startOfDay, now)
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
            0L
        }
    }

    fun resetUsage(entity: AppLimitEntity) {
        viewModelScope.launch {
            val baselineMinutes = (rawUsageMs(entity.packageName) / 60_000L).toInt()
            repository.upsert(
                entity.copy(
                    usageAtResetMinutes = baselineMinutes,
                    lastWarningDate = "",
                    lastBlockedDate = ""
                )
            )
        }
    }

    fun getInstalledApps(): List<InstalledApp> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }
        return activities
            .map { it.activityInfo.packageName }
            .toSet()
            .filter { it != context.packageName }
            .mapNotNull { pkg ->
                try {
                    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getApplicationInfo(pkg, 0)
                    }
                    InstalledApp(pkg, pm.getApplicationLabel(info).toString())
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                }
            }
            .sortedBy { it.label.lowercase() }
    }

    fun upsertLimit(packageName: String, limitMinutes: Int) {
        viewModelScope.launch {
            repository.upsert(AppLimitEntity(packageName = packageName, limitMinutes = limitMinutes))
        }
    }

    fun deleteLimit(entity: AppLimitEntity) {
        viewModelScope.launch { repository.delete(entity) }
    }
}
