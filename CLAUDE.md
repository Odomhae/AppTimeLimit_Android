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

A foreground-service Android app that enforces daily per-app usage limits. The service polls `UsageStatsManager` via event replay, and draws a `WindowManager` overlay when a limit is exceeded.

### Data flow

```
AppLimitApplication (startup)
  ├─ MobileAds.initialize()          ← AdMob SDK init
  └─ schedules DailyResetWorker via WorkManager (midnight, KEEP policy)

UsageMonitorService (foreground, START_STICKY)
  └─ immediate monitorOnce() on every onStartCommand (no waiting for first tick)
  └─ poll loop (screen on only):
       → AppDatabase.getEnabledLimits()
       → getTodayUsageMs()            ← queryEvents() replay from startOfDay to now
       → effectiveMinutes = (rawMs / 60_000 - entity.usageAtResetMinutes).coerceAtLeast(0)
       → getForegroundApp()           ← queryEvents() 10-min window state machine
       → at 80%: UsageNotifier.sendWarning()  (once per day via lastWarningDate)
       → at 100% + foreground: BlockingOverlayManager.show()
       → navigated away: BlockingOverlayManager.hide()
       → nextPollInterval(): 3s if any app ≥80% used, 10s otherwise

DailyResetWorker (midnight)
  └─ clears lastWarningDate + lastBlockedDate in Room
  └─ self-reschedules for next midnight

BlockingOverlayManager (when limit hit)
  └─ show(): two-layer LinearLayout
       → innerContent (weight=1, gravity=center) — app name, status, "Open App Limit" button
       → AdView (WRAP_CONTENT) — banner ad pinned to bottom
  └─ tapping button or background → starts MainActivity with EXTRA_FROM_BLOCKER=true

MainActivity (launchMode=singleTop)
  └─ onCreate / onNewIntent: if EXTRA_FROM_BLOCKER → tryShowAd()
  └─ loadInterstitialAd() pre-loads next interstitial after each dismiss
```

### Key design decisions

- **`UsageStatsManager.queryEvents()` everywhere** — both `UsageMonitorService.getTodayUsageMs()` and `AppLimitViewModel.rawUsageMs()` replay MOVE_TO_FOREGROUND/MOVE_TO_BACKGROUND events from startOfDay to now, then add `now - lastForegroundMs` for the still-open session. `queryUsageStats()` is never used — it only commits a session after the app backgrounds, making it useless for real-time display and blocking.
- **`usageMap` stores milliseconds, not minutes** — `AppLimitViewModel._usageMap` is `MutableStateFlow<Map<String, Long>>`. Storing minutes caused `StateFlow` to deduplicate within the same minute (structural equality), freezing the UI. Milliseconds change on every 3-second poll, bypassing deduplication. `HomeScreen` converts: `((usageMap[pkg] ?: 0L) / 60_000L).toInt()`. The `init` coroutine on `Dispatchers.IO` drives the polling — no `flatMapLatest`.
- **Non-Hilt components**: `UsageMonitorService`, `BootReceiver`, `BlockingOverlayManager`, and `DailyResetWorker` all get dependencies manually (via `AppDatabase.getInstance(context)` companion singleton). Only the ViewModel/UI layer uses Hilt injection.
- **Blocking overlay** uses `TYPE_APPLICATION_OVERLAY` (requires `SYSTEM_ALERT_WINDOW`). Drawn on main thread via `withContext(Dispatchers.Main)`. Window flags must be `FLAG_LAYOUT_IN_SCREEN` **only** — do not add `FLAG_NOT_FOCUSABLE` or `FLAG_NOT_TOUCH_MODAL`. The inner `BlockingLayout` class overrides `dispatchTouchEvent` (returns `true` after calling `super` so Button clicks still work) and `dispatchKeyEvent` (returns `true` always, blocking Back/volume/menu). This is the only reliable way to prevent touch leakage to the app below.
- **Overlay → AppLimit launch** — tapping the overlay (button or background) calls `openAppLimit()` which starts `MainActivity` with `FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP | FLAG_ACTIVITY_NEW_TASK` and `EXTRA_FROM_BLOCKER=true`. The overlay auto-hides within the next poll cycle (≤3 s) when the service detects AppLimit is in the foreground. `MainActivity.launchMode=singleTop` ensures `onNewIntent` is called if it's already running.
- **AdMob** — `MobileAds.initialize()` runs in `AppLimitApplication.onCreate()`. `MainActivity` pre-loads an `InterstitialAd`; when `EXTRA_FROM_BLOCKER=true` is received it calls `tryShowAd()` which either shows the loaded ad immediately or sets `pendingShowAd=true` to show it when loading finishes. After dismiss, the next interstitial pre-loads. `BlockingOverlayManager` and `HomeScreen` each show a banner (`AdSize.BANNER`) — the overlay uses a direct `AdView` in its layout; the home screen uses `AndroidView` inside a `BannerAd()` composable set as `Scaffold.bottomBar`. **All ad unit IDs in the code are Google test IDs — replace before publishing.**
- **App picker** uses `queryIntentActivities(ACTION_MAIN + CATEGORY_LAUNCHER)` — not `getInstalledApplications()`. Pre-installed apps (YouTube, Instagram) have `FLAG_SYSTEM` set and are missed by a flag-based filter; a launcher-intent query returns exactly what appears in the app drawer. The manifest `<queries>` block declares this intent for Android 11+ visibility. `QUERY_ALL_PACKAGES` is not used (Play Store restricted).
- **Usage reset baseline** — `AppLimitEntity.usageAtResetMinutes` stores the raw minutes (`rawUsageMs / 60_000`) at the moment the user taps ↺. Both service and ViewModel compute `effectiveMs = rawMs - (baseline * 60_000)`. `DailyResetWorker` does **not** clear `usageAtResetMinutes` — it becomes irrelevant at midnight when `UsageStatsManager` resets naturally. **DB is version 2**; `MIGRATION_1_2` adds the column via `ALTER TABLE`. Always bump the version and add a migration when changing the schema.
- **`foregroundServiceType = specialUse`** is required for `targetSdk = 36`. The manifest must include the `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property.

### Dependency versions (libs.versions.toml)

All new versions live in the version catalog. Notable pinned versions:
- `ksp` must match `kotlin` exactly: currently both `2.0.21` / `ksp = "2.0.21-1.0.27"`. If Kotlin is upgraded, bump KSP first.
- `composeBom = "2024.09.00"` → Material3 1.3.0. `LinearProgressIndicator` uses the `progress: () -> Float` lambda API, not the deprecated `Float` param.
- `playServicesAds = "23.3.0"` — Google Mobile Ads SDK.
- Room and Hilt annotation processors use `ksp(...)`, not `kapt`.

### Permission onboarding

`NavGraph` checks all three permissions at startup via `checkAllPermissionsGranted()` (defined in `PermissionSetupScreen.kt`, package-visible). If any are missing, it routes to `PermissionSetupScreen` first.

### Daily notification deduplication

`AppLimitEntity` stores `lastWarningDate` and `lastBlockedDate` as `"yyyy-MM-dd"` strings. The service compares against `todayString()` before sending a notification and before updating the DB, so each notification fires at most once per calendar day. `DailyResetWorker` clears both fields at midnight.
