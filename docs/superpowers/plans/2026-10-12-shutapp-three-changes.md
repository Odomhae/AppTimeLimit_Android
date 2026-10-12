# ShutApp 3가지 변경 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 차단 오버레이의 15분 추가 버튼 제거, 권한 설정을 버튼 하나짜리 자동 마법사로 변경, 활성 제한 앱이 없을 때 포그라운드 알림을 띄우지 않음.

**Architecture:** 기존 구조 유지. (1) `BlockingOverlayManager`/`UsageMonitorService`에서 스누즈 UI·배선만 제거, (2) `PermissionSetupScreen`을 단일 버튼 + 생명주기 재확인 기반 자동 진행 마법사로 재작성, (3) `UsageMonitorService`가 활성 제한 0개면 `stopForeground`+`stopSelf`로 스스로 종료하고 `AppLimitViewModel.upsertLimit`가 앱 추가 시 서비스를 재기동.

**Tech Stack:** Kotlin, Jetpack Compose (Material3, compose-bom 2024.09.00), Android Foreground Service, Room(수정 없음), Hilt.

## Global Constraints

- `minSdk = 26`, `targetSdk = compileSdk = 36`, JVM target **Java 11** — API 26 초과 호출은 버전 체크로 가드.
- DB 스키마 **변경 금지**: `AppLimitEntity`, `AppLimitDao`, `AppDatabase`(version 4), `DailyResetWorker` 모두 수정하지 않음. `snoozedMinutes` 필드·SQL·`resetUsage`의 `snoozedMinutes = 0` 유지.
- 사용자 노출 문자열은 `values/strings.xml`(영문)과 `values-ko/strings.xml`(한글) 양쪽 모두에 추가/삭제. 하드코딩 금지.
- 빌드 명령은 Windows 기준 `./gradlew.bat`. 컴파일 게이트는 `./gradlew.bat compileDebugKotlin`, 전체 확인은 `./gradlew.bat assembleDebug`.
- 이 프로젝트에는 의미 있는 단위 테스트 하비스가 없다(서비스/오버레이/권한은 기기 동작). 각 태스크의 검증은 **컴파일 통과 + 명시된 수동 확인**으로 한다.
- 작업 브랜치: `revision_261012`.

---

### Task 1: 차단 오버레이에서 15분 추가(스누즈) 버튼 제거

**Files:**
- Modify: `app/src/main/java/com/odom/applimit/overlay/BlockingOverlayManager.kt`
- Modify: `app/src/main/java/com/odom/applimit/service/UsageMonitorService.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-ko/strings.xml`

**Interfaces:**
- Produces: `BlockingOverlayManager.show(packageName: String, appName: String, usedMinutes: Int, limitMinutes: Int)` — `onSnooze` 파라미터 제거된 새 시그니처. Task 3의 서비스 호출부가 이 시그니처를 사용.

- [ ] **Step 1: `BlockingOverlayManager.show` 시그니처에서 `onSnooze` 제거**

`app/src/main/java/com/odom/applimit/overlay/BlockingOverlayManager.kt` 37번째 줄:

변경 전:
```kotlin
    fun show(packageName: String, appName: String, usedMinutes: Int, limitMinutes: Int, onSnooze: () -> Unit) {
```
변경 후:
```kotlin
    fun show(packageName: String, appName: String, usedMinutes: Int, limitMinutes: Int) {
```

- [ ] **Step 2: 스누즈 버튼 `addView` 블록 제거**

같은 파일 110~118번째 줄의 다음 블록 전체를 삭제:
```kotlin
            addView(Button(context).apply {
                text = context.getString(R.string.snooze_button)
                setBackgroundColor(Color.TRANSPARENT)
                setTextColor(Color.argb(200, 200, 200, 200))
                setOnClickListener {
                    onSnooze()       // DB +15분 업데이트
                    openAppLimit()   // MainActivity → 전면광고
                }
            })
```
(바로 위의 "Open ShutApp" 버튼 `addView(Button(context).apply { ... setOnClickListener { openAppLimit() } })` 블록은 유지.)

- [ ] **Step 3: 서비스의 `show(...)` 호출에서 `onSnooze` 람다 제거**

`app/src/main/java/com/odom/applimit/service/UsageMonitorService.kt` 125~136번째 줄:

변경 전:
```kotlin
                    if (foregroundPackage == limit.packageName) {
                        val appName = getAppName(limit.packageName)
                        withContext(Dispatchers.Main) {
                            overlayManager.show(limit.packageName, appName, usedMinutes, effectiveLimit) {
                                // Snooze: add 15 minutes to limit, persist to DB.
                                scope.launch(Dispatchers.IO) {
                                    val snoozed = limit.copy(snoozedMinutes = limit.snoozedMinutes + SNOOZE_MINUTES)
                                    dao.update(snoozed)
                                }
                            }
                        }
                    }
```
변경 후:
```kotlin
                    if (foregroundPackage == limit.packageName) {
                        val appName = getAppName(limit.packageName)
                        withContext(Dispatchers.Main) {
                            overlayManager.show(limit.packageName, appName, usedMinutes, effectiveLimit)
                        }
                    }
```

- [ ] **Step 4: 사용되지 않는 `SNOOZE_MINUTES` 상수 제거**

같은 파일 34~36번째 줄의 `companion object` 제거(해당 블록에 다른 멤버 없음):
```kotlin
    companion object {
        private const val SNOOZE_MINUTES = 15
    }
```
전체 삭제. (`effectiveLimit = limit.limitMinutes + limit.snoozedMinutes` 계산식 2곳은 **그대로 둔다** — DB 무변경 원칙.)

- [ ] **Step 5: `snooze_button` 문자열 리소스 제거 (영문)**

`app/src/main/res/values/strings.xml` 62~63번째 줄 제거:
```xml
    <!-- Snooze -->
    <string name="snooze_button">Allow 15 min more</string>
```

- [ ] **Step 6: `snooze_button` 문자열 리소스 제거 (한글)**

`app/src/main/res/values-ko/strings.xml` 62~63번째 줄 제거:
```xml
    <!-- Snooze -->
    <string name="snooze_button">15분 더 허용</string>
```

- [ ] **Step 7: 컴파일 확인**

Run: `./gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL. `onSnooze`/`SNOOZE_MINUTES`/`R.string.snooze_button` 미해결 참조 없음.

- [ ] **Step 8: 수동 확인 (기기/에뮬레이터)**

제한을 초과한 앱을 열어 차단 오버레이를 띄운다. 오버레이에 **"셧앱 열기" 버튼과 ✕ 닫기 버튼만** 보이고 "15분 더 허용" 버튼은 없어야 한다. (불가 시 생략하고 다음 단계 진행.)

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/odom/applimit/overlay/BlockingOverlayManager.kt app/src/main/java/com/odom/applimit/service/UsageMonitorService.kt app/src/main/res/values/strings.xml app/src/main/res/values-ko/strings.xml
git commit -m "Remove 15-min snooze button from blocking overlay"
```

---

### Task 2: 권한 설정 자동 마법사로 재작성

**Files:**
- Modify: `app/src/main/java/com/odom/applimit/ui/PermissionSetupScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-ko/strings.xml`

**Interfaces:**
- Consumes: `checkAllPermissionsGranted(context)`, `hasUsageStatsPermission(context)`, `buildPermissionList(context): List<PermissionStatus>`, `PermissionStatus(id, title, description, granted)` — 기존 그대로 재사용.
- Produces: `PermissionSetupScreen(onAllGranted: () -> Unit)` — 시그니처 불변(호출부 `NavGraph` 수정 불필요).

- [ ] **Step 1: 마법사 문자열 추가 (영문)**

`app/src/main/res/values/strings.xml` 의 `<!-- Permission setup screen -->` 블록 안(101번째 줄 `perm_notif_desc` 다음, `</resources>` 앞)에 추가:
```xml
    <string name="perm_grant_all">Grant permissions</string>
    <string name="perm_all_done">All set</string>
    <string name="perm_progress">%1$d / %2$d granted</string>
```

- [ ] **Step 2: 마법사 문자열 추가 (한글)**

`app/src/main/res/values-ko/strings.xml` 의 동일 위치(100번째 줄 `perm_notif_desc` 다음)에 추가:
```xml
    <string name="perm_grant_all">권한 허용</string>
    <string name="perm_all_done">모두 완료</string>
    <string name="perm_progress">%1$d / %2$d 허용됨</string>
```

- [ ] **Step 3: `PermissionSetupScreen.kt` 재작성**

`app/src/main/java/com/odom/applimit/ui/PermissionSetupScreen.kt` 전체를 아래로 교체. (상단 `PermissionStatus`, `checkAllPermissionsGranted`, `hasUsageStatsPermission`, `buildPermissionList`는 그대로 유지하고, `@Composable fun PermissionSetupScreen`과 `PermissionRow`만 아래 내용으로 대체.)

```kotlin
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
```

주요 변경점:
- `buildPermissionList` 순서를 notifications → usage_stats → overlay 로 재정렬(마법사 진행 순서와 일치). 런타임 팝업을 먼저 처리한 뒤 설정 화면 2개로 이어짐.
- 항목별 `onGrant` 버튼 제거(`PermissionRow` 파라미터에서 `onGrant` 삭제), 하단 단일 버튼으로 통합.
- `Icons.Default.Warning` → `Icons.Default.RadioButtonUnchecked`(미허용 표시). `material-icons-extended` 의존성에 포함됨.

- [ ] **Step 4: 컴파일 확인**

Run: `./gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL. `R.string.perm_grant_all` / `perm_all_done` / `perm_progress` / `RadioButtonUnchecked` / `LocalLifecycleOwner` 미해결 참조 없음.

- [ ] **Step 5: 수동 확인 (기기/에뮬레이터)**

앱 데이터를 지우거나 권한을 모두 해제한 상태로 앱을 실행 → 권한 화면 진입. **"권한 허용" 버튼 1회 탭** 후:
1. 알림 권한 팝업이 뜬다(Android 13+). 허용.
2. 자동으로 "사용 기록 접근" 설정 화면이 열린다. 허용 후 뒤로가기.
3. 자동으로 "다른 앱 위에 그리기" 설정 화면이 열린다. 허용 후 뒤로가기.
4. 세 권한 모두 ✓ 가 되면 자동으로 홈 화면으로 이동.
또한 일부만 허용된 상태로 재진입 시 이미 허용된 항목은 건너뛰고 남은 것만 요청되는지 확인. (불가 시 생략.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/odom/applimit/ui/PermissionSetupScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-ko/strings.xml
git commit -m "Rewrite permission setup as single-button auto-advancing wizard"
```

---

### Task 3: 활성 제한 0개일 때 포그라운드 서비스 자동 종료 + 앱 추가 시 재기동

**Files:**
- Modify: `app/src/main/java/com/odom/applimit/service/UsageMonitorService.kt`
- Modify: `app/src/main/java/com/odom/applimit/ui/AppLimitViewModel.kt`

**Interfaces:**
- Consumes: `UsageMonitorService` (Task 1 에서 수정된 상태), `AppDatabase.getInstance(...).appLimitDao().getEnabledLimits()`.
- Produces: `AppLimitViewModel.upsertLimit` 가 제한 추가/갱신 후 `UsageMonitorService` 를 기동.

- [ ] **Step 1: 서비스에 "비면 종료" 헬퍼 추가**

`app/src/main/java/com/odom/applimit/service/UsageMonitorService.kt` 클래스 내부(예: `getAppName` 근처)에 헬퍼 추가:
```kotlin
    /** 활성 제한이 하나도 없으면 포그라운드 알림을 제거하고 서비스를 종료한다. */
    private fun stopIfNoLimits(hasLimits: Boolean): Boolean {
        if (hasLimits) return false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        return true
    }
```
참고: `STOP_FOREGROUND_REMOVE` 는 `Service` 의 상수로 API 24+ 에서 사용 가능(minSdk 26 이므로 안전).

- [ ] **Step 2: `monitorOnce()` 의 빈 제한 분기에서 서비스 종료**

같은 파일 `monitorOnce()` 의 다음 분기(현재 100~105번째 줄 부근):

변경 전:
```kotlin
        val dao = AppDatabase.getInstance(applicationContext).appLimitDao()
        val limits = dao.getEnabledLimits()
        if (limits.isEmpty()) {
            withContext(Dispatchers.Main) { overlayManager.hide() }
            return
        }
```
변경 후:
```kotlin
        val dao = AppDatabase.getInstance(applicationContext).appLimitDao()
        val limits = dao.getEnabledLimits()
        if (limits.isEmpty()) {
            withContext(Dispatchers.Main) { overlayManager.hide() }
            stopIfNoLimits(false)
            return
        }
```

- [ ] **Step 3: `onStartCommand` 에서 시작 직후 빈 상태면 즉시 종료**

같은 파일 `onStartCommand`(현재 48~53번째 줄):

변경 전:
```kotlin
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            try { monitorOnce() } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        }
        return START_STICKY
    }
```
변경 후:
```kotlin
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            try {
                val dao = AppDatabase.getInstance(applicationContext).appLimitDao()
                if (stopIfNoLimits(dao.getEnabledLimits().isNotEmpty())) return@launch
                monitorOnce()
            } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        }
        return START_STICKY
    }
```
설명: `onCreate` 가 `startForeground` 로 알림을 띄운 직후, 활성 제한이 없으면 알림을 제거하고 종료한다(빈 홈에서 알림이 잠깐 깜빡 후 사라짐 — 설계상 허용).

- [ ] **Step 4: `AppLimitViewModel.upsertLimit` 가 서비스를 기동하도록 수정**

`app/src/main/java/com/odom/applimit/ui/AppLimitViewModel.kt` 상단 import 에 추가:
```kotlin
import com.odom.applimit.service.UsageMonitorService
```

`upsertLimit`(현재 137~148번째 줄)을 수정:

변경 전:
```kotlin
    fun upsertLimit(packageName: String, limitMinutes: Int) {
        viewModelScope.launch {
            val existing = repository.getLimitForPackage(packageName)
            val entity = existing?.copy(limitMinutes = limitMinutes)
                ?: AppLimitEntity(
                    packageName = packageName,
                    limitMinutes = limitMinutes,
                    sortOrder = (limits.value.maxOfOrNull { it.sortOrder } ?: -1) + 1
                )
            repository.upsert(entity)
        }
    }
```
변경 후:
```kotlin
    fun upsertLimit(packageName: String, limitMinutes: Int) {
        viewModelScope.launch {
            val existing = repository.getLimitForPackage(packageName)
            val entity = existing?.copy(limitMinutes = limitMinutes)
                ?: AppLimitEntity(
                    packageName = packageName,
                    limitMinutes = limitMinutes,
                    sortOrder = (limits.value.maxOfOrNull { it.sortOrder } ?: -1) + 1
                )
            repository.upsert(entity)
            startUsageMonitorService()
        }
    }

    private fun startUsageMonitorService() {
        val intent = Intent(context, UsageMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
```
참고: `context`(`@ApplicationContext`), `Intent`, `Build` 는 이 파일에 이미 import 되어 있다.

- [ ] **Step 5: 컴파일 확인**

Run: `./gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL. `stopIfNoLimits`/`STOP_FOREGROUND_REMOVE`/`UsageMonitorService` 미해결 참조 없음.

- [ ] **Step 6: 전체 빌드 확인**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: 수동 확인 (기기/에뮬레이터)**

1. 제한 앱을 모두 삭제한 상태에서 앱 실행 → 상단 "사용량 모니터" 알림이 잠깐 떴다가 **사라지고 다시 뜨지 않는다**.
2. 앱을 추가(제한 설정) → 알림이 **다시 나타나고** 감시가 재개된다.
3. 마지막 제한 앱을 삭제 → 다음 폴링 주기(최대 10초) 안에 알림이 사라진다.
(불가 시 생략.)

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/odom/applimit/service/UsageMonitorService.kt app/src/main/java/com/odom/applimit/ui/AppLimitViewModel.kt
git commit -m "Stop foreground service (and notification) when no enabled limits"
```

---

## 완료 기준

- `./gradlew.bat assembleDebug` 성공.
- 차단 오버레이에 스누즈 버튼 없음.
- 권한 화면이 버튼 하나로 3개 권한을 순차 자동 진행.
- 활성 제한 0개면 포그라운드 알림이 유지되지 않고, 앱 추가 시 서비스/알림 재개.
- DB 스키마·마이그레이션·`DailyResetWorker` 무변경.
