package com.odom.applimit.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.odom.applimit.R
import com.odom.applimit.data.AppLimitEntity
import com.odom.applimit.service.UsageMonitorService
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Switch
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddLimit: () -> Unit,
    onShowAd: () -> Unit = {},
    viewModel: AppLimitViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val limits by viewModel.limits.collectAsState()
    val usageMap by viewModel.usageMap.collectAsState()
    val isLoadingUsage by viewModel.isLoadingUsage.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()

    val density = LocalDensity.current
    val itemSpacingPx = with(density) { 12.dp.toPx() }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var itemHeightPx by remember { mutableFloatStateOf(0f) }
    var displayList by remember { mutableStateOf(limits) }
    LaunchedEffect(limits) {
        if (draggedIndex == null) displayList = limits
    }

    var editingEntity by remember { mutableStateOf<AppLimitEntity?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }
    var pendingResetEntity by remember { mutableStateOf<AppLimitEntity?>(null) }
    var pendingDeleteEntity by remember { mutableStateOf<AppLimitEntity?>(null) }

    val pullRefreshState = rememberPullToRefreshState()

    val exitAdView = remember {
        AdView(context).apply {
            setAdSize(AdSize.MEDIUM_RECTANGLE)
            adUnitId = context.getString(R.string.TEST_admob_banner_id)
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(exitAdView) {
        onDispose { exitAdView.destroy() }
    }

    val confirmAdView = remember {
        AdView(context).apply {
            setAdSize(AdSize.MEDIUM_RECTANGLE)
            adUnitId = context.getString(R.string.TEST_admob_banner_id)
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(confirmAdView) {
        onDispose { confirmAdView.destroy() }
    }

    BackHandler { showExitDialog = true }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.exit_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.exit_dialog_message))
                    AndroidView(
                        factory = { exitAdView },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { (context as? Activity)?.finish() }) {
                    Text(stringResource(R.string.exit_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    pendingResetEntity?.let { entity ->
        val appName = resolveAppName(context.packageManager, entity.packageName)
        AlertDialog(
            onDismissRequest = { pendingResetEntity = null },
            title = { Text(stringResource(R.string.confirm_reset_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.confirm_reset_message, appName))
                    AndroidView(factory = { confirmAdView }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetUsage(entity)
                    onShowAd()
                    pendingResetEntity = null
                }) { Text(stringResource(R.string.btn_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingResetEntity = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    pendingDeleteEntity?.let { entity ->
        val appName = resolveAppName(context.packageManager, entity.packageName)
        AlertDialog(
            onDismissRequest = { pendingDeleteEntity = null },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.confirm_delete_message, appName))
                    AndroidView(factory = { confirmAdView }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteLimit(entity)
                    pendingDeleteEntity = null
                }) { Text(stringResource(R.string.btn_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteEntity = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

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
                onShowAd()
                editingEntity = null
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullToRefresh(
                state = pullRefreshState,
                isRefreshing = isLoadingUsage,
                onRefresh = { viewModel.refreshUsageNow() }
            )
    ) {
        Scaffold(
            bottomBar = { BannerAd() },
            floatingActionButton = {
                FloatingActionButton(onClick = onAddLimit) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_limit))
                }
            }
        ) { paddingValues ->
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                PauseButton(isPaused = isPaused, onToggle = { viewModel.togglePause() })
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (displayList.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.home_empty),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(displayList, key = { _, item -> item.packageName }) { index, limit ->
                                val isDragging = index == draggedIndex
                                LimitCard(
                                    modifier = Modifier
                                        .then(if (isDragging) Modifier else Modifier.animateItem())
                                        .zIndex(if (isDragging) 1f else 0f)
                                        .graphicsLayer {
                                            translationY = if (isDragging) dragOffsetY else 0f
                                        }
                                        .onSizeChanged {
                                            if (itemHeightPx == 0f) itemHeightPx = it.height.toFloat()
                                        },
                                    limit = limit,
                                    usedMinutes = ((usageMap[limit.packageName] ?: 0L) / 60_000L).toInt(),
                                    appName = resolveAppName(context.packageManager, limit.packageName),
                                    isDragging = isDragging,
                                    onEditLimit = { editingEntity = limit },
                                    onReset = { pendingResetEntity = limit },
                                    onDelete = { pendingDeleteEntity = limit },
                                    onDragStart = { draggedIndex = index },
                                    onDrag = { deltaY ->
                                        dragOffsetY += deltaY
                                        val currentIndex = draggedIndex ?: index
                                        val slotHeightPx = itemHeightPx + itemSpacingPx
                                        if (slotHeightPx > 0f) {
                                            val moveBy = (dragOffsetY / slotHeightPx).roundToInt()
                                            if (moveBy != 0) {
                                                val targetIndex = (currentIndex + moveBy)
                                                    .coerceIn(0, displayList.lastIndex)
                                                if (targetIndex != currentIndex) {
                                                    displayList = displayList.toMutableList().apply {
                                                        add(targetIndex, removeAt(currentIndex))
                                                    }
                                                    draggedIndex = targetIndex
                                                    dragOffsetY -= moveBy * slotHeightPx
                                                }
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        draggedIndex = null
                                        dragOffsetY = 0f
                                        viewModel.reorder(displayList)
                                    }
                                )
                            }
                        }
                    }
                    if (displayList.isNotEmpty() && isLoadingUsage) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                        )
                    }
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
    val context = LocalContext.current
    var minutes by remember { mutableIntStateOf(currentMinutes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(appName) },
        text = {
            Column {
                Text(
                    formatMinutes(context, minutes),
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
                    Text(stringResource(R.string.slider_min), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.slider_max), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(minutes) }) { Text(stringResource(R.string.btn_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}

@Composable
private fun LimitCard(
    modifier: Modifier = Modifier,
    limit: AppLimitEntity,
    usedMinutes: Int,
    appName: String,
    isDragging: Boolean,
    onEditLimit: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val context = LocalContext.current
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val progress = (usedMinutes.toFloat() / limit.limitMinutes).coerceIn(0f, 1f)
    val exceeded = usedMinutes >= limit.limitMinutes
    val icon = remember(limit.packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(limit.packageName).toBitmap().asImageBitmap()
        }.getOrNull()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 8.dp else 1.dp
        )
    ) {
        Column(modifier = Modifier.padding(12.dp, 8.dp, 4.dp, 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                icon?.let {
                    Image(
                        bitmap = it,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(appName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            R.string.usage_progress,
                            formatMinutes(context, usedMinutes),
                            formatMinutes(context, limit.limitMinutes)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (exceeded) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { currentOnDragStart() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    currentOnDrag(dragAmount.y)
                                },
                                onDragEnd = { currentOnDragEnd() },
                                onDragCancel = { currentOnDragEnd() }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = stringResource(R.string.cd_drag_handle),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                color = if (exceeded) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onEditLimit) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.cd_edit_limit))
                }
                IconButton(onClick = onReset) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_reset_usage))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_remove_limit))
                }
            }
        }
    }
}

@Composable
private fun PauseButton(isPaused: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.btn_pause_limits_ko),
            style = MaterialTheme.typography.bodyMedium
        )
        Switch(
            checked = isPaused,
            onCheckedChange = { onToggle() }
        )
    }
}

@Composable
private fun BannerAd() {
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = ctx.getString(R.string.TEST_admob_banner_id)
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}

internal fun formatMinutes(context: Context, minutes: Int): String = when {
    minutes < 60 -> context.getString(R.string.time_minutes, minutes)
    minutes % 60 == 0 -> context.getString(R.string.time_hours, minutes / 60)
    else -> context.getString(R.string.time_hours_minutes, minutes / 60, minutes % 60)
}

private fun resolveAppName(pm: PackageManager, packageName: String): String =
    try {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(packageName, 0)
        }
        pm.getApplicationLabel(info).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }
