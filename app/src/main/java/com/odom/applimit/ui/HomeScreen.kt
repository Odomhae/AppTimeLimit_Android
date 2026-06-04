package com.odom.applimit.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.odom.applimit.data.AppLimitEntity
import com.odom.applimit.service.UsageMonitorService
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddLimit: () -> Unit,
    viewModel: AppLimitViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val limits by viewModel.limits.collectAsState()
    val usageMap by viewModel.usageMap.collectAsState()

    // Non-null when the edit-limit dialog is open for a specific app
    var editingEntity by remember { mutableStateOf<AppLimitEntity?>(null) }

    LaunchedEffect(Unit) {
        val intent = Intent(context, UsageMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    editingEntity?.let { entity ->
        EditLimitDialog(
            appName = resolveAppName(context.packageManager, entity.packageName),
            currentMinutes = entity.limitMinutes,
            onDismiss = { editingEntity = null },
            onConfirm = { newMinutes ->
                viewModel.upsertLimit(entity.packageName, newMinutes)
                editingEntity = null
            }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("App Limit") }) },
        bottomBar = { BannerAd() },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddLimit) {
                Icon(Icons.Default.Add, contentDescription = "Add limit")
            }
        }
    ) { paddingValues ->
        if (limits.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No limits set yet.\nTap + to add your first app limit.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(limits, key = { it.packageName }) { limit ->
                    LimitCard(
                        limit = limit,
                        usedMinutes = ((usageMap[limit.packageName] ?: 0L) / 60_000L).toInt(),
                        appName = resolveAppName(context.packageManager, limit.packageName),
                        onEditLimit = { editingEntity = limit },
                        onReset = { viewModel.resetUsage(limit) },
                        onDelete = { viewModel.deleteLimit(limit) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EditLimitDialog(
    appName: String,
    currentMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var minutes by remember { mutableIntStateOf(currentMinutes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(appName) },
        text = {
            Column {
                Text(
                    formatMinutes(minutes),
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = minutes.toFloat(),
                    onValueChange = { minutes = (it / 5f).roundToInt() * 5 },
                    valueRange = 5f..240f,
                    steps = 46,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("5 min", style = MaterialTheme.typography.bodySmall)
                    Text("4 hrs", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(minutes) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun LimitCard(
    limit: AppLimitEntity,
    usedMinutes: Int,
    appName: String,
    onEditLimit: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit
) {
    val progress = (usedMinutes.toFloat() / limit.limitMinutes).coerceIn(0f, 1f)
    val exceeded = usedMinutes >= limit.limitMinutes

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(appName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "$usedMinutes / ${formatMinutes(limit.limitMinutes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (exceeded) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEditLimit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit limit")
                }
                IconButton(onClick = onReset) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset usage")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove limit")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = if (exceeded) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun BannerAd() {
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { ctx ->
            AdView(ctx).apply {
                // Replace with your real banner ad unit ID before publishing
                setAdSize(AdSize.BANNER)
                adUnitId = "ca-app-pub-3940256099942544/6300978111"
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}

private fun formatMinutes(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} hr"
    else -> "${minutes / 60} hr ${minutes % 60} min"
}

@Suppress("DEPRECATION")
private fun resolveAppName(pm: PackageManager, packageName: String): String =
    try {
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }
