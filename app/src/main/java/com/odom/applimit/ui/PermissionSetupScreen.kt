package com.odom.applimit.ui

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

data class PermissionStatus(
    val id: String,
    val title: String,
    val description: String,
    val granted: Boolean
)

fun checkAllPermissionsGranted(context: Context): Boolean =
    hasUsageStatsPermission(context) &&
            Settings.canDrawOverlays(context) &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED)

fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(AppOpsManager::class.java)
    return appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        android.os.Process.myUid(),
        context.packageName
    ) == AppOpsManager.MODE_ALLOWED
}

private fun buildPermissionList(context: Context): List<PermissionStatus> = listOf(
    PermissionStatus(
        id = "usage_stats",
        title = "Usage Access",
        description = "Tracks how long each app is used per day",
        granted = hasUsageStatsPermission(context)
    ),
    PermissionStatus(
        id = "overlay",
        title = "Draw Over Other Apps",
        description = "Shows a blocking screen when the limit is reached",
        granted = Settings.canDrawOverlays(context)
    ),
    PermissionStatus(
        id = "notifications",
        title = "Notifications",
        description = "Sends warnings when approaching your limit",
        granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
    )
)

@Composable
fun PermissionSetupScreen(onAllGranted: () -> Unit) {
    val context = LocalContext.current
    var permissions by remember { mutableStateOf(buildPermissionList(context)) }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { permissions = buildPermissionList(context) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissions = buildPermissionList(context) }

    LaunchedEffect(permissions) {
        if (permissions.all { it.granted }) onAllGranted()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Setup Required", style = MaterialTheme.typography.headlineMedium)
        Text(
            "App Limit needs the following permissions to monitor and block apps.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(permissions) { perm ->
                PermissionRow(
                    status = perm,
                    onGrant = {
                        when (perm.id) {
                            "usage_stats" ->
                                settingsLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            "overlay" ->
                                settingsLauncher.launch(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            "notifications" ->
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                        }
                    }
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onAllGranted,
            modifier = Modifier.fillMaxWidth(),
            enabled = permissions.all { it.granted }
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun PermissionRow(status: PermissionStatus, onGrant: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (status.granted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (status.granted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(status.title, style = MaterialTheme.typography.titleSmall)
                Text(status.description, style = MaterialTheme.typography.bodySmall)
            }
            if (!status.granted) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onGrant) { Text("Grant") }
            }
        }
    }
}
