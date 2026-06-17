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

# Install debug APK directly to connected device
./gradlew installDebug
```

On Windows use `gradlew.bat` instead of `./gradlew`.

## Architecture

A foreground-service Android app that enforces daily per-app usage limits. The service polls `UsageStatsManager` via event replay, and draws a `WindowManager` overlay when a limit is exceeded.

### Package structure

```
com.odom.applimit
├── AppLimitApplication.kt       — AdMob init, WorkManager scheduling
├── MainActivity.kt              — singleTop, interstitial ad host
├── data/                        — Room: AppLimitEntity, AppLimitDao, AppLimitRepository, AppDatabase
├── di/                          — AppModule (Hilt bindings for AppLimitRepository)
├── notification/                — UsageNotifier (foreground notification + warning/blocked channels)
├── overlay/                     — BlockingOverlayManager + inner BlockingLayout
├── receiver/                    — BootReceiver (restarts UsageMonitorService on device boot)
├── service/                     — UsageMonitorService (foreground, START_STICKY)
├── ui/                          — Composable screens, AppLimitViewModel (Hilt)
│   ├── NavGraph.kt
│   ├── HomeScreen.kt
│   ├── AddLimitScreen.kt
│   └── PermissionSetupScreen.kt
├── ui/theme/                    — Material3 theme, colors, typography
├── util/                        — UsageStatsHelper (queryEvents wrapper), PauseManager (SharedPrefs)
└── worker/                      — DailyResetWorker (midnight, via WorkManager)
```

### Required permissions

Three permissions are checked at startup by `checkAllPermissionsGranted()` (`PermissionSetupScreen.kt`):

| Permission | How checked |
|---|---|
| `PACKAGE_USAGE_STATS` (Usage Access) | `AppOpsManager.checkOpNoThrow(OPSTR_GET_USAGE_STATS)` |
| `SYSTEM_ALERT_WINDOW` (Draw over apps) | `Settings.canDrawOverlays(context)` |
| `POST_NOTIFICATIONS` (Android 13+ only) | `checkSelfPermission(POST_NOTIFICATIONS)` |

If any are missing, `NavGraph` routes to `PermissionSetupScreen` before `HomeScreen`.

### Navigation routes

```
"permissions"  → PermissionSetupScreen (popped inclusive on grant)
"home"         → HomeScreen
"add_limit"    → AddLimitScreen
```

### Data flow

```
AppLimitApplication (startup)
  ├─ MobileAds.initialize()
  └─ DailyResetWorker.scheduleNext() via WorkManager (ExistingWorkPolicy.REPLACE)

BootReceiver
  └─ onReceive(BOOT_COMPLETED) → startForegroundService(UsageMonitorService)

UsageMonitorService (foreground, START_STICKY)
  └─ immediate monitorOnce() on every onStartCommand (no waiting for first tick)
  └─ poll loop (screen-on only):
       → PauseManager.isPaused()      ← if true, hide overlay and skip everything
       → AppDatabase.getEnabledLimits()
       → UsageStatsHelper.getTodayUsageMs()   ← queryEvents() replay from startOfDay to now
       → effectiveMinutes = (rawMs / 60_000 - entity.usageAtResetMinutes).coerceAtLeast(0)
       → UsageStatsHelper.getForegroundApp()  ← queryEvents() 10-min window state machine
       → at 80%: UsageNotifier.sendWarning()  (once per day via lastWarningDate)
       → at 100% + foreground: BlockingOverlayManager.show()
       → navigated away: BlockingOverlayManager.hide()
       → nextPollInterval(): 3s if any app ≥80% used (against effectiveLimit), 10s otherwise
       → snooze button: DB update snoozedMinutes += 15 min  (persisted)

DailyResetWorker (midnight, ExistingWorkPolicy.REPLACE, self-reschedules)
  └─ resetDailyNotificationFlags(): clears lastWarningDate + lastBlockedDate only
     (does NOT clear usageAtResetMinutes — irrelevant after UsageStatsManager resets at midnight)

BlockingOverlayManager (when limit hit)
  └─ show(): two-layer LinearLayout
       → contentFrame (FrameLayout, weight=1) — innerContent + ✕ close button (Gravity.TOP|END)
           → innerContent — app name, countdown to midnight, "Open 포커스온" button, snooze button
       → AdView (WRAP_CONTENT) — banner ad pinned to bottom
  └─ "Open 포커스온" / background tap → MainActivity with EXTRA_FROM_BLOCKER=true
  └─ ✕ button → closeAll(): home launcher + killBackgroundProcesses(blockedPkg) + hide

MainActivity (launchMode=singleTop)
  └─ onCreate / onNewIntent: if EXTRA_FROM_BLOCKER → tryShowAd()
  └─ loadInterstitialAd() pre-loads next interstitial after each dismiss
```

### Key design decisions

- **Snooze adds 15 minutes to the limit** — `AppLimitEntity.snoozedMinutes` stores extra minutes. Snoozing updates `snoozedMinutes += 15` in DB. `nextPollInterval()` and `monitorOnce()` use `effectiveLimit = limitMinutes + snoozedMinutes` for all calculations. `DailyResetWorker` clears `snoozedMinutes = 0` at midnight alongside `lastWarningDate` / `lastBlockedDate`.

- **`PauseManager` pauses everything until next Monday midnight** — stored in SharedPreferences (`app_limit_prefs` / `paused_until_ms`). `monitorOnce()` checks `PauseManager.isPaused()` as the very first step and hides the overlay + returns early. Used for weekend/vacation exemptions.

- **`UsageStatsManager.queryEvents()` everywhere** — both `UsageStatsHelper.getTodayUsageMs()` and `AppLimitViewModel.rawUsageMs()` replay MOVE_TO_FOREGROUND/MOVE_TO_BACKGROUND events from startOfDay to now, then add `now - lastForegroundMs` for the still-open session. `queryUsageStats()` is never used — it only commits a session after the app backgrounds, making it useless for real-time display and blocking.

- **`usageMap` stores milliseconds, not minutes** — `AppLimitViewModel._usageMap` is `MutableStateFlow<Map<String, Long>>`. Storing minutes caused `StateFlow` to deduplicate within the same minute (structural equality), freezing the UI. Milliseconds change on every 3-second poll, bypassing deduplication. `HomeScreen` converts: `((usageMap[pkg] ?: 0L) / 60_000L).toInt()`.

- **Non-Hilt components** — `UsageMonitorService`, `BootReceiver`, `BlockingOverlayManager`, and `DailyResetWorker` get dependencies manually (via `AppDatabase.getInstance(context)` companion singleton). Only the ViewModel/UI layer uses Hilt injection.

- **Blocking overlay window flags** — `TYPE_APPLICATION_OVERLAY` with `FLAG_LAYOUT_IN_SCREEN` **only**. Do not add `FLAG_NOT_FOCUSABLE` or `FLAG_NOT_TOUCH_MODAL`. The inner `BlockingLayout` overrides `dispatchTouchEvent` (calls super so Button clicks fire, then returns `true`) and `dispatchKeyEvent` (returns `true` always). This is the only reliable way to prevent touch/key leakage to the app below while keeping buttons working.

- **App picker** uses `queryIntentActivities(ACTION_MAIN + CATEGORY_LAUNCHER)` — not `getInstalledApplications()`. Pre-installed apps (YouTube, Instagram) have `FLAG_SYSTEM` set and are missed by flag-based filtering; a launcher-intent query returns exactly what appears in the app drawer. The manifest `<queries>` block declares this intent for Android 11+ visibility.

- **Usage reset baseline** — `AppLimitEntity.usageAtResetMinutes` stores raw minutes at the moment the user taps ↺. Both service and ViewModel compute `effectiveMs = rawMs - (baseline * 60_000)`. `DailyResetWorker` does **not** clear this field.

- **DB is version 2** — `MIGRATION_1_2` adds `usageAtResetMinutes` via `ALTER TABLE`. Always bump `version` in `@Database` and add a named `Migration` object when changing the schema.

- **`foregroundServiceType = specialUse`** — required for `targetSdk = 36`. The manifest must include the `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property.

- **AdMob ad unit IDs** — all IDs in the code reference `R.string.TEST_admob_banner_id` and `R.string.TEST_admob_interstitial_id` (Google test IDs). Replace with real unit IDs in `strings.xml` before publishing. A shared `confirmAdView` instance in `HomeScreen` is reused across the reset and delete confirmation dialogs (they cannot appear simultaneously).

### Dependency versions (libs.versions.toml)

All versions live in the version catalog. Notable constraints:
- `ksp` must match `kotlin` exactly: currently `kotlin = "2.0.21"` / `ksp = "2.0.21-1.0.27"`. Bump KSP first when upgrading Kotlin.
- `composeBom = "2024.09.00"` → Material3 1.3.0. `LinearProgressIndicator` uses the `progress: () -> Float` lambda API, not the deprecated `Float` param.
- `playServicesAds = "23.3.0"` — Google Mobile Ads SDK.
- Room and Hilt annotation processors use `ksp(...)`, not `kapt`.
