package com.odom.applimit.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_limits")
data class AppLimitEntity(
    @PrimaryKey val packageName: String,
    val limitMinutes: Int,
    val isEnabled: Boolean = true,
    val lastWarningDate: String = "",
    val lastBlockedDate: String = "",
    // Minutes already used when the user last hit "Reset".
    // Effective usage = currentUsage - usageAtResetMinutes.
    val usageAtResetMinutes: Int = 0,
    // Minutes added via snooze button; resets to 0 daily at midnight.
    val snoozedMinutes: Int = 0
)
