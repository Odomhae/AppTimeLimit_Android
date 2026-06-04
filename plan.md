# App Usage Limiter — Android App Plan

## Context

The user wants to build an Android app (targeting Google Play Store) that:
1. Lets the user set a daily time limit per app
2. Sends a **warning notification** when approaching/hitting the limit
3. **Blocks re-entry** to the app after the limit is exceeded (overlay or redirect)

Target: personal self-control use case, similar to Android's built-in Digital Wellbeing but as a standalone Play Store app.

---

## Technical Feasibility

**Yes, this is fully feasible on Android.** Several production apps (StayFocusd, AppBlock, ActionDash) use the same approach. iOS would require restricted Apple entitlements — Android is the right platform choice.

---

## Required Android Permissions

| Permission | Purpose | How Granted |
|---|---|---|
| `PACKAGE_USAGE_STATS` | Read per-app usage time | User must manually grant in Settings (not a runtime dialog) |
| `SYSTEM_ALERT_WINDOW` | Draw a blocking overlay over other apps | Prompt user to grant via Settings intent |
| `POST_NOTIFICATIONS` (Android 13+) | Send warning notifications | Standard runtime permission |
| `FOREGROUND_SERVICE` | Keep monitor running in background | Declared in manifest, no prompt |
| `RECEIVE_BOOT_COMPLETED` | Restart service after reboot | Declared in manifest |

All of these are used by legitimate Play Store apps. No special Play Store approval is needed beyond standard review.

---

## Architecture

### Core Components

```
AppUsageLimiterApp
├── ui/                         # Jetpack Compose screens
│   ├── HomeScreen              # List of tracked apps + limits
│   ├── AddLimitScreen          # Pick app, set daily limit
│   └── PermissionSetupScreen   # Onboarding: grant all permissions
├── service/
│   └── UsageMonitorService     # Foreground service, polls every 10s
├── receiver/
│   └── BootReceiver            # Restarts service after reboot
├── data/
│   ├── AppLimitDao             # Room DAO
│   ├── AppLimitEntity          # package name + daily limit (minutes)
│   └── AppDatabase             # Room database
├── overlay/
│   └── BlockingOverlayManager  # Draws/removes SYSTEM_ALERT_WINDOW overlay
└── notification/
    └── UsageNotifier           # Sends warning + blocked notifications
```

### Data Flow

```
UsageMonitorService (every 10s)
  → UsageStatsManager.queryUsageStats()   # get today's usage per package
  → compare against stored limits
  → if usage >= 80% of limit:
      UsageNotifier.sendWarning()
  → if usage >= 100% of limit:
      check if that app is currently in foreground
        YES → BlockingOverlayManager.show(packageName)
              UsageNotifier.sendBlocked()
        NO  → BlockingOverlayManager.hide()
  → store last-checked usage in Room
```

### Blocking Strategy

Use `SYSTEM_ALERT_WINDOW` overlay (preferred, Play Store safe):
- When the target app is detected as foreground (via `UsageStatsManager.queryEvents` or polling), inflate a full-screen `WindowManager` overlay
- Overlay shows: app name, time used, "You've hit your limit" message, and a dismiss/unlock button (optional: require waiting 5 min to unlock)
- When user navigates away, hide the overlay

Alternative (simpler but less precise): on limit hit, use `Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)` to redirect to launcher — no overlay needed.

---

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Background**: Foreground Service + `WorkManager` (daily reset of usage counters at midnight)
- **Database**: Room (store limits + daily usage cache)
- **DI**: Hilt
- **Min SDK**: API 23 (Android 6.0) — `UsageStatsManager` stable since API 21

---

## Implementation Phases

### Phase 1 — Project Setup & Permissions
- Create Kotlin + Compose project
- Declare all permissions in `AndroidManifest.xml`
- Build `PermissionSetupScreen`: check each permission status, deep-link to correct Settings screens

### Phase 2 — Data Layer
- Define `AppLimitEntity` (packageName, limitMinutes, isEnabled)
- Room database + DAO (CRUD)
- Repository pattern wrapping DAO

### Phase 3 — Usage Monitoring Service
- `UsageMonitorService` as a foreground service with persistent notification
- Poll `UsageStatsManager.queryUsageStats(INTERVAL_DAILY, ...)` every 10 seconds
- Emit events when 80% and 100% thresholds are crossed

### Phase 4 — Blocking Overlay
- `BlockingOverlayManager` using `WindowManager` + `SYSTEM_ALERT_WINDOW`
- Full-screen overlay with app icon, usage info, lock message
- Detects foreground app using `UsageStatsManager.queryEvents` (most recent event of type MOVE_TO_FOREGROUND)

### Phase 5 — Notifications
- Warning notification at 80% usage (e.g., "5 minutes left on Instagram")
- Block notification when limit hit
- Use `NotificationChannel` (required Android 8+)

### Phase 6 — UI
- Home screen: list of all apps with limits, daily usage progress bars
- Add/edit limit: app picker from installed apps, time picker (15 min increments)
- Daily usage reset at midnight via `WorkManager`

### Phase 7 — Play Store prep
- Privacy policy (required — app accesses usage stats)
- App icon, screenshots
- Data safety form: no data leaves the device

---

## Key Risks & Mitigations

| Risk | Mitigation |
|---|---|
| Battery drain from polling | Poll every 10s only while screen is on; use `PowerManager.isInteractive()` to pause when screen off |
| `UsageStatsManager` returns stale data on some OEMs (Samsung, Xiaomi) | Cache last known foreground app; use AccessibilityService as fallback if needed |
| Play Store rejection for `SYSTEM_ALERT_WINDOW` | Justify in store listing as "app blocker overlay" — this is accepted for screen time apps |
| Users bypass overlay by force-stopping the monitor service | Show persistent foreground notification; add device admin option as power-user feature |

---

## Verification Plan

1. Grant all permissions → verify each is recognized by the permission setup screen
2. Set a 1-minute limit on a test app → use the app for 1 minute → confirm warning notification appears at 0:48 remaining
3. Exceed the limit → switch to the blocked app → confirm overlay appears within 10 seconds
4. Reboot device → confirm monitoring service restarts automatically
5. Verify daily reset: usage counters clear at midnight (or manually trigger WorkManager task)
6. Build release APK → run `./gradlew lint` → 0 errors before Play Store submission
