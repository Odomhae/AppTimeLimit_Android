package com.odom.applimit.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odom.applimit.data.AppLimitEntity
import com.odom.applimit.data.AppLimitRepository
import com.odom.applimit.util.PauseManager
import com.odom.applimit.util.UsageStatsHelper
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
import javax.inject.Inject

data class InstalledApp(val packageName: String, val label: String)

@HiltViewModel
class AppLimitViewModel @Inject constructor(
    private val repository: AppLimitRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val usageStatsHelper = UsageStatsHelper(context)

    val limits: StateFlow<List<AppLimitEntity>> = repository.getAllLimits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Stores milliseconds so StateFlow sees a new value on every poll (minutes
    // would deduplicate within the same minute and freeze the UI).
    private val _usageMap = MutableStateFlow<Map<String, Long>>(emptyMap())
    val usageMap: StateFlow<Map<String, Long>> = _usageMap.asStateFlow()

    private val _isLoadingUsage = MutableStateFlow(true)
    val isLoadingUsage: StateFlow<Boolean> = _isLoadingUsage.asStateFlow()

    private val _isPaused = MutableStateFlow(PauseManager.isPaused(context))
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val currentLimits = limits.value
                if (currentLimits.isNotEmpty()) {
                    _usageMap.value = currentLimits.associate { entity ->
                        entity.packageName to effectiveUsageMs(entity)
                    }
                    _isLoadingUsage.value = false
                }
                _isPaused.value = PauseManager.isPaused(context)
                delay(3_000L)
            }
        }
    }

    fun refreshUsageNow() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingUsage.value = true
            try {
                val currentLimits = limits.value
                if (currentLimits.isNotEmpty()) {
                    _usageMap.value = currentLimits.associate { entity ->
                        entity.packageName to effectiveUsageMs(entity)
                    }
                }
                _isPaused.value = PauseManager.isPaused(context)
            } finally {
                _isLoadingUsage.value = false
            }
        }
    }

    fun effectiveUsageMs(entity: AppLimitEntity): Long =
        ((usageStatsHelper.getTodayUsageMs(entity.packageName) ?: 0L) - entity.usageAtResetMinutes * 60_000L)
            .coerceAtLeast(0L)

    fun effectiveUsageMinutes(entity: AppLimitEntity): Int =
        (effectiveUsageMs(entity) / 60_000L).toInt()

    fun togglePause() {
        if (PauseManager.isPaused(context)) {
            PauseManager.resume(context)
        } else {
            PauseManager.pause(context)
        }
        _isPaused.value = PauseManager.isPaused(context)
    }

    fun resetUsage(entity: AppLimitEntity) {
        viewModelScope.launch {
            val baselineMinutes = ((usageStatsHelper.getTodayUsageMs(entity.packageName) ?: 0L) / 60_000L).toInt()
            repository.upsert(
                entity.copy(
                    usageAtResetMinutes = baselineMinutes,
                    lastWarningDate = "",
                    lastBlockedDate = "",
                    snoozedMinutes = 0
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
