package com.odom.applimit.util

import android.content.Context
import java.util.Calendar

object PauseManager {
    private const val PREFS_NAME = "app_limit_prefs"
    private const val KEY_WEEKEND_PAUSE_ENABLED = "weekend_pause_enabled"
    private const val KEY_PAUSED_UNTIL_LEGACY = "paused_until_ms"

    /** True only while the pause actually applies: switch on AND today is Sat/Sun. */
    fun isPaused(context: Context): Boolean =
        isPauseEnabled(context) && isWeekend()

    /** The raw switch state, shown in the UI regardless of the current day. */
    fun isPauseEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_WEEKEND_PAUSE_ENABLED, false)

    fun setPauseEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_WEEKEND_PAUSE_ENABLED, enabled)
            .remove(KEY_PAUSED_UNTIL_LEGACY)
            .apply()
    }

    private fun isWeekend(): Boolean {
        val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return day == Calendar.SATURDAY || day == Calendar.SUNDAY
    }
}
