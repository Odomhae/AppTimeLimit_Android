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
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.odom.applimit.R

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
        id = "notifications",
        title = context.getString(R.string.perm_notif_title),
        description = context.getString(R.string.perm_notif_desc),
        granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
    ),
    PermissionStatus(
        id = "usage_stats",
        title = context.getString(R.string.perm_usage_title),
        description = context.getString(R.string.perm_usage_desc),
        granted = hasUsageStatsPermission(context)
    ),
    PermissionStatus(
        id = "overlay",
        title = context.getString(R.string.perm_overlay_title),
        description = context.getString(R.string.perm_overlay_desc),
        granted = Settings.canDrawOverlays(context)
    )
)

@Composable
fun PermissionSetupScreen(onAllGranted: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissions by remember { mutableStateOf(buildPermissionList(context)) }
    // true 동안에만 권한 변경 시 다음 단계로 자동 진행한다.
    var running by remember { mutableStateOf(false) }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { permissions = buildPermissionList(context) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissions = buildPermissionList(context) }

    // 설정 화면에서 돌아올 때 ON_RESUME 으로 상태 재확인.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissions = buildPermissionList(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun requestNext() {
        val next = permissions.firstOrNull { !it.granted }
        when (next?.id) {
            "notifications" ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            "usage_stats" ->
                settingsLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            "overlay" ->
                settingsLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
        }
    }

    // 권한 상태가 바뀔 때마다: 전부 허용이면 완료, 아니면 진행 중일 때 다음 단계 요청.
    // buildPermissionList 결과가 내용상 동일하면 state 가 갱신되지 않아 재진입/루프가 없다.
    LaunchedEffect(permissions) {
        if (permissions.all { it.granted }) {
            running = false
            onAllGranted()
        } else if (running) {
            requestNext()
        }
    }

    val grantedCount = permissions.count { it.granted }
    val allGranted = grantedCount == permissions.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.perm_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.perm_body),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            stringResource(R.string.perm_progress, grantedCount, permissions.size),
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(permissions) { perm -> PermissionRow(status = perm) }
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = {
                running = true
                requestNext()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !allGranted
        ) {
            Text(
                stringResource(
                    if (allGranted) R.string.perm_all_done else R.string.perm_grant_all
                )
            )
        }
    }
}

@Composable
private fun PermissionRow(status: PermissionStatus) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (status.granted) Icons.Default.CheckCircle
                else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (status.granted) Color(0xFF4CAF50)
                else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(status.title, style = MaterialTheme.typography.titleSmall)
                Text(status.description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
