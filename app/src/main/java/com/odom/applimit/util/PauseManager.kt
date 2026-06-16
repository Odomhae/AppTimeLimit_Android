package com.odom.applimit.util

import android.content.Context
import java.util.Calendar

object PauseManager {
    private const val PREFS_NAME = "app_limit_prefs"
    private const val KEY_PAUSED_UNTIL = "paused_until_ms"

    fun isPaused(context: Context): Boolean =
        System.currentTimeMillis() < getPausedUntil(context)

    fun pause(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_PAUSED_UNTIL, nextMondayMidnight()).apply()
    }

    fun resume(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_PAUSED_UNTIL, 0L).apply()
    }

    private fun getPausedUntil(context: Context): Long =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_PAUSED_UNTIL, 0L)

    private fun nextMondayMidnight(): Long {
        val cal = Calendar.getInstance()
        val daysUntilMonday = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 7
            Calendar.TUESDAY -> 6
            Calendar.WEDNESDAY -> 5
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 3
            Calendar.SATURDAY -> 2
            else -> 1 // SUNDAY
        }
        cal.add(Calendar.DAY_OF_YEAR, daysUntilMonday)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
