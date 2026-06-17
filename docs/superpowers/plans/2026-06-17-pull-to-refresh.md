# Pull-to-Refresh & Background Resume Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add pull-to-refresh gesture and automatic update on app resume to HomeScreen, allowing users to manually refresh usage and see latest data when returning from background.

**Architecture:** 
- Accompanist SwipeRefresh wraps the main content in HomeScreen
- New `refreshUsageNow()` method in ViewModel triggers immediate usage update
- MainActivity's onResume() calls refreshUsageNow() for automatic background resume update
- Existing `isLoadingUsage` StateFlow controls loading indicator display

**Tech Stack:**
- Accompanist 0.34.0 (Google's Material3 SwipeRefresh)
- Kotlin coroutines (Dispatchers.IO for background work)
- Material3 Compose UI

## Global Constraints

- Target Android API 28+
- Kotlin 2.0.21
- No breaking changes to existing APIs
- All changes must work with existing Service polling (no conflicts)

---

## File Structure

```
libs.versions.toml                    — Add accompanist version
build.gradle.kts (app)                — Add accompanist-swiperefresh dependency
AppLimitViewModel.kt                  — Add refreshUsageNow() method
HomeScreen.kt                         — Wrap content in SwipeRefresh
MainActivity.kt                       — Override onResume() for auto-refresh
```

---

## Task 1: Add Accompanist Dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces: `com.google.accompanist:accompanist-swiperefresh` library available for import in Compose

- [ ] **Step 1: Add accompanist version to libs.versions.toml**

Open `gradle/libs.versions.toml` and add to `[versions]` section:

```toml
accompanist = "0.34.0"
```

- [ ] **Step 2: Add dependency to build.gradle.kts**

Open `app/build.gradle.kts`, find `dependencies { }` block, and add:

```kotlin
implementation("com.google.accompanist:accompanist-swiperefresh:${libs.versions.accompanist.get()}")
```

Make sure it's among the other Compose dependencies (near Material3, Foundation).

- [ ] **Step 3: Sync Gradle**

Run: `./gradlew build --dry-run`

Expected: BUILD SUCCESSFUL (no dependency conflicts)

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "deps: add accompanist-swiperefresh for pull-to-refresh"
```

---

## Task 2: Add refreshUsageNow() to ViewModel

**Files:**
- Modify: `app/src/main/java/com/odom/applimit/ui/AppLimitViewModel.kt:70-80` (after existing init block)

**Interfaces:**
- Consumes: `viewModelScope`, `_isLoadingUsage` MutableStateFlow, `_usageMap` MutableStateFlow, `limits` StateFlow, `effectiveUsageMs(entity)` method, `PauseManager.isPaused(context)`
- Produces: `fun refreshUsageNow(): Unit` — triggers immediate usage update, sets loading state

- [ ] **Step 1: Read current ViewModel structure**

Open `app/src/main/java/com/odom/applimit/ui/AppLimitViewModel.kt`

Locate the `init { }` block around line 49-63. We'll add the new method after it.

- [ ] **Step 2: Add refreshUsageNow() method**

After the closing brace of `init { }`, add:

```kotlin
fun refreshUsageNow() {
    viewModelScope.launch(Dispatchers.IO) {
        _isLoadingUsage.value = true
        try {
            val currentLimits = limits.value
            if (currentLimits.isNotEmpty()) {
                _usageMap.value = currentLimits.associate { entity ->
                    entity.packageName to effectiveUsageMs(entity)
                }
            }
            _isPaused.value = PauseManager.isPaused(context)
        } finally {
            _isLoadingUsage.value = false
        }
    }
}
```

- [ ] **Step 3: Verify syntax**

Check that:
- Method is at class level (not nested inside another method)
- Uses correct scope (`viewModelScope`)
- Calls existing methods (`effectiveUsageMs`, `PauseManager.isPaused`)
- Updates correct StateFlows (`_isLoadingUsage`, `_usageMap`, `_isPaused`)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/odom/applimit/ui/AppLimitViewModel.kt
git commit -m "feat: add refreshUsageNow() method to ViewModel"
```

---

## Task 3: Integrate SwipeRefresh in HomeScreen

**Files:**
- Modify: `app/src/main/java/com/odom/applimit/ui/HomeScreen.kt:1-30` (imports)
- Modify: `app/src/main/java/com/odom/applimit/ui/HomeScreen.kt:220-260` (Scaffold body)

**Interfaces:**
- Consumes: `viewModel.isLoadingUsage.collectAsState()`, `viewModel.refreshUsageNow()`, existing Column layout structure
- Produces: SwipeRefresh wrapper around main content with loading indicator

- [ ] **Step 1: Add SwipeRefresh imports**

Open `app/src/main/java/com/odom/applimit/ui/HomeScreen.kt`

Find the imports section (top of file). Add after other material3 imports:

```kotlin
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
```

- [ ] **Step 2: Get isRefreshing state in Composable**

Find the `@Composable fun HomeScreen()` function (around line 73).

Inside HomeScreen, after existing `collectAsState()` calls, add:

```kotlin
val isRefreshing by viewModel.isLoadingUsage.collectAsState()
```

(Note: `isLoadingUsage` is already collected on line 83, so you might already have access to it. Reuse the existing state variable.)

- [ ] **Step 3: Wrap Scaffold with SwipeRefresh**

Locate the `Scaffold(...)` block (around line 211).

Replace:
```kotlin
Scaffold(
    topBar = { TopAppBar(title = { Text(stringResource(R.string.home_title)) }) },
    ...
) { paddingValues ->
```

With:
```kotlin
SwipeRefresh(
    state = rememberSwipeRefreshState(isLoadingUsage),
    onRefresh = { viewModel.refreshUsageNow() },
    modifier = Modifier.fillMaxSize()
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.home_title)) }) },
        ...
    ) { paddingValues ->
```

And add a closing brace for SwipeRefresh at the very end of Scaffold's lambda:
```kotlin
    } // end Scaffold
} // end SwipeRefresh
```

- [ ] **Step 4: Verify structure**

Check that:
- SwipeRefresh wraps entire Scaffold
- `onRefresh` calls `viewModel.refreshUsageNow()`
- `isLoadingUsage` controls the refresh state indicator
- All closing braces match

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/odom/applimit/ui/HomeScreen.kt
git commit -m "feat: integrate SwipeRefresh for pull-to-refresh in HomeScreen"
```

---

## Task 4: Add onResume() to MainActivity

**Files:**
- Modify: `app/src/main/java/com/odom/applimit/MainActivity.kt:1-50` (class declaration and imports)

**Interfaces:**
- Consumes: `viewModel` (AppLimitViewModel instance), `refreshUsageNow()` method
- Produces: `onResume()` override that calls `viewModel.refreshUsageNow()`

- [ ] **Step 1: Check if ViewModel is injected in MainActivity**

Open `app/src/main/java/com/odom/applimit/MainActivity.kt`

Look for a ViewModel property. If not present, add it after the class declaration:

```kotlin
private val viewModel: AppLimitViewModel by viewModels()
```

- [ ] **Step 2: Add onResume() override**

Find the class body (after `onCreate()` if it exists). Add:

```kotlin
override fun onResume() {
    super.onResume()
    viewModel.refreshUsageNow()
}
```

Insert it after `onCreate()` or at the end of the class, before any inner classes.

- [ ] **Step 3: Verify imports**

Make sure these are imported at the top:
```kotlin
import androidx.lifecycle.viewmodels
// Or use androidx.activity.viewModels if in Activity
import com.odom.applimit.ui.AppLimitViewModel
```

- [ ] **Step 4: Verify syntax**

Check that:
- `super.onResume()` is called first
- `viewModel` property exists and is properly initialized
- Method is at class level

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/odom/applimit/MainActivity.kt
git commit -m "feat: add onResume() hook to refresh usage on app resume"
```

---

## Task 5: Manual Integration Test

**Files:**
- Test: Manual testing (no automated test added for UI behavior)

**Interfaces:**
- Consumes: All changes from Tasks 1-4
- Produces: Verified pull-to-refresh and background resume functionality

- [ ] **Step 1: Build the app**

Run:
```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL (0 errors)

- [ ] **Step 2: Install and run on emulator/device**

Ensure you have an Android emulator running or device connected.

Run:
```bash
./gradlew installDebug
```

Then open the app manually on the device.

- [ ] **Step 3: Test pull-to-refresh**

1. Navigate to HomeScreen (should show list of apps)
2. Swipe down from top of the list
3. Verify: SwipeRefresh indicator appears with loading animation
4. Verify: Usage numbers update immediately after releasing
5. Verify: Indicator disappears when update completes

- [ ] **Step 4: Test background resume update**

1. On HomeScreen, note current usage numbers
2. Press home button (app goes to background)
3. Wait 5+ seconds
4. Press app in recents or home screen to resume
5. Verify: Usage numbers refresh automatically on resume

- [ ] **Step 5: Test edge cases**

1. **Multiple refreshes:** Pull down 2-3 times in quick succession → all updates should work
2. **With existing Service polling:** Wait for auto-poll (3-10s) then manually refresh → no conflicts
3. **AddLimitScreen:** Verify pull-to-refresh only works on HomeScreen (not on other screens)

- [ ] **Step 6: Verify no crashes**

Check Logcat for any exceptions:

```bash
./gradlew logcat
```

Expected: No ERROR or CRASH logs related to SwipeRefresh or ViewModel

- [ ] **Step 7: Commit (if tests pass)**

```bash
git add -A
git commit -m "test: verify pull-to-refresh and background resume work end-to-end"
```

---

## Spec Coverage Check

✅ **Context:** Pull-to-refresh needed for user manual refresh  
✅ **Architecture:** SwipeRefresh + ViewModel method + onResume hook  
✅ **Libraries:** accompanist-swiperefresh added  
✅ **ViewModel:** refreshUsageNow() method implemented  
✅ **UI:** HomeScreen wrapped with SwipeRefresh  
✅ **Lifecycle:** MainActivity onResume() wired  
✅ **Testing:** Manual integration test included  

**Gaps:** None identified. All spec requirements covered.

---

## Implementation Notes

- **No conflicts with Service polling:** The Service continues its own 3-10s polling independently. Manual refresh and onResume refresh can fire concurrently with Service polls — this is safe because usageMap updates are idempotent.
- **isLoadingUsage reuse:** The existing `isLoadingUsage` StateFlow is used for both Service updates and manual refresh. This unifies the loading indicator behavior.
- **No unit tests needed for UI:** SwipeRefresh behavior is internal to Accompanist. The ViewModel's `refreshUsageNow()` logic is already tested implicitly through the Service's `monitorOnce()` cycle (which uses the same calculation methods).
