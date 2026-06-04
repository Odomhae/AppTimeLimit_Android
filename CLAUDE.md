# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Assemble debug APK
./gradlew assembleDebug

# Assemble release APK
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run a single test class
./gradlew :app:testDebugUnitTest --tests "com.odom.applimit.ExampleUnitTest"

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedDebugAndroidTest

# Lint check
./gradlew lint

# Clean build
./gradlew clean assembleDebug
```

On Windows use `gradlew.bat` instead of `./gradlew`.

## Architecture

This is a foreground-service-based Android app that enforces daily per-app usage limits. The service polls `UsageStatsManager` every 10 seconds and draws a `WindowManager` overlay when a limit is exceeded.

### Data flow

```
AppLimitApplication (startup)
  └─ schedules DailyResetWorker via WorkManager (midnight, KEEP policy)

UsageMonitorService (foreground, START_STICKY)
  └─ immediate monitorOnce() on every onStartCommand (no waiting for first tick)
  └─ poll loop (screen on only):
       → AppDatabase.getEnabledLimits()
       → UsageStatsManager.queryUsageStats()  ← today's ms per package
       → effectiveMinutes = rawMinutes - entity.usageAtResetMinutes  ← post-reset baseline
       → UsageStatsManager.queryEvents()      ← 10-min window for foreground app
       → at 80%: UsageNotifier.sendWarning()  (once per day via lastWarningDate)
       → at 100% + foreground: BlockingOverlayManager.show()
       → navigated away: BlockingOverlayManager.hide()
       → nextPollInterval(): 3s if any app ≥80% used, 10s otherwise

DailyResetWorker (midnight)
  └─ clears lastWarningDate + lastBlockedDate in Room
  └─ self-reschedules for next midnight
```

### Key design decisions

- **`UsageStatsManager` requires manual permission** (`PACKAGE_USAGE_STATS`). This is not a runtime dialog — the app deep-links to `Settings.ACTION_USAGE_ACCESS_SETTINGS`. Same for `SYSTEM_ALERT_WINDOW`.
- **Non-Hilt components**: `UsageMonitorService`, `BootReceiver`, `BlockingOverlayManager`, and `DailyResetWorker` all get their dependencies manually (via `AppDatabase.getInstance(context)` companion singleton), bypassing Hilt. Only the ViewModel/UI layer uses Hilt injection.
- **Blocking overlay** uses `TYPE_APPLICATION_OVERLAY` (requires `SYSTEM_ALERT_WINDOW`). Drawn on main thread via `withContext(Dispatchers.Main)`. Window flags must be `FLAG_LAYOUT_IN_SCREEN` **only** — do not add `FLAG_NOT_FOCUSABLE` or `FLAG_NOT_TOUCH_MODAL`. The inner `BlockingLayout` class overrides `dispatchTouchEvent` (returns `true` after calling `super` so Button clicks still work) and `dispatchKeyEvent` (returns `true` always, blocking Back/volume/menu). This is the only reliable way to prevent touch leakage to the app below.
- **Foreground detection** queries a 10-minute event window and replays `MOVE_TO_FOREGROUND`/`MOVE_TO_BACKGROUND` events as a state machine to find the current foreground app — necessary because the user may have launched the app before the last poll.
- **App picker** uses `queryIntentActivities(ACTION_MAIN + CATEGORY_LAUNCHER)` — not `getInstalledApplications()`. Pre-installed apps (YouTube, Instagram) have `FLAG_SYSTEM` set and are missed by a flag-based filter; a launcher-intent query returns exactly what appears in the app drawer. The manifest `<queries>` block declares this intent for Android 11+ visibility. `QUERY_ALL_PACKAGES` is not used (Play Store restricted).
- **Live usage display** — `AppLimitViewModel.usageMap` is a `StateFlow<Map<String, Int>>` built with `flatMapLatest` over `limits`: the inner `flow { while(true) { emit(...); delay(5_000) } }` re-queries `UsageStatsManager` every 5 seconds. `flatMapLatest` restarts the ticker whenever an app is added or removed. `HomeScreen` collects this map; do not call `queryUsageMinutes` directly from a composable.
- **Usage reset baseline** — `AppLimitEntity.usageAtResetMinutes` stores the raw `UsageStatsManager` reading at the moment the user taps ↺. Both the service and ViewModel compute `effectiveMinutes = (raw - baseline).coerceAtLeast(0)`. Tapping reset snapshots the current raw value as the new baseline and clears `lastWarningDate`/`lastBlockedDate`. `DailyResetWorker` does **not** clear `usageAtResetMinutes` — it becomes irrelevant at midnight when `UsageStatsManager` resets naturally. **DB is version 2**; `MIGRATION_1_2` adds the column via `ALTER TABLE`. Always bump the version and add a migration when changing the schema.
- **`foregroundServiceType = specialUse`** is required for `targetSdk = 36`. The manifest must include the `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property.

### Dependency versions (libs.versions.toml)

All new versions live in the version catalog. Notable pinned versions:
- `ksp` must match `kotlin` exactly: currently both `2.0.21` / `ksp = "2.0.21-1.0.27"`. If Kotlin is upgraded, bump KSP first.
- `composeBom = "2024.09.00"` → Material3 1.3.0. `LinearProgressIndicator` uses the `progress: () -> Float` lambda API, not the deprecated `Float` param.
- Room and Hilt annotation processors use `ksp(...)`, not `kapt`.

### Permission onboarding

`NavGraph` checks all three permissions at startup via `checkAllPermissionsGranted()` (defined in `PermissionSetupScreen.kt`, package-visible). If any are missing, it routes to `PermissionSetupScreen` first.

### Daily notification deduplication

`AppLimitEntity` stores `lastWarningDate` and `lastBlockedDate` as `"yyyy-MM-dd"` strings. The service compares against `todayString()` before sending a notification and before updating the DB, so each notification fires at most once per calendar day. `DailyResetWorker` clears both fields at midnight.
