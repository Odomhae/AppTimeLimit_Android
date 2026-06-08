package com.odom.applimit.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.odom.applimit.MainActivity
import com.odom.applimit.R

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
            NotificationChannel(
                CHANNEL_WARNING,
                context.getString(R.string.notif_channel_warning_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = context.getString(R.string.notif_channel_warning_desc) }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_BLOCKED,
                context.getString(R.string.notif_channel_blocked_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = context.getString(R.string.notif_channel_blocked_desc) }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE,
                context.getString(R.string.notif_channel_service_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = context.getString(R.string.notif_channel_service_desc) }
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
            .setContentTitle(context.getString(R.string.notif_warning_title, appName))
            .setContentText(context.getString(R.string.notif_warning_text, remainingMinutes))
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
            .setContentTitle(context.getString(R.string.notif_blocked_title, appName))
            .setContentText(context.getString(R.string.notif_blocked_text, appName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID_BLOCKED_BASE + packageName.hashCode(), notification)
    }

    fun buildServiceNotification(): Notification =
        NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(context.getString(R.string.notif_service_title))
            .setContentText(context.getString(R.string.notif_service_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
}
