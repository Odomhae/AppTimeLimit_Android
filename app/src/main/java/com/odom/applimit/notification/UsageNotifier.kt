package com.odom.applimit.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.odom.applimit.MainActivity

class UsageNotifier(private val context: Context) {
    companion object {
        const val CHANNEL_WARNING = "usage_warning"
        const val CHANNEL_BLOCKED = "usage_blocked"
        const val CHANNEL_SERVICE = "monitor_service"
        const val NOTIFICATION_ID_SERVICE = 9999
        private const val NOTIFICATION_ID_WARNING_BASE = 1000
        private const val NOTIFICATION_ID_BLOCKED_BASE = 2000
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_WARNING, "Usage Warning", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Warns when nearing app time limit" }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_BLOCKED, "App Blocked", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Notifies when app limit is exceeded" }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Usage Monitor", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Background usage monitor" }
        )
    }

    fun sendWarning(packageName: String, appName: String, remainingMinutes: Int) {
        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_WARNING)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("$appName – Time limit approaching")
            .setContentText("$remainingMinutes minute(s) remaining today")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID_WARNING_BASE + packageName.hashCode(), notification)
    }

    fun sendBlocked(packageName: String, appName: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_BLOCKED)
            .setSmallIcon(android.R.drawable.ic_delete)
            .setContentTitle("$appName – Daily limit reached")
            .setContentText("You've used your daily allowance for $appName")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID_BLOCKED_BASE + packageName.hashCode(), notification)
    }

    fun buildServiceNotification(): Notification =
        NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("App Limit Monitor")
            .setContentText("Monitoring app usage in the background")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
}
