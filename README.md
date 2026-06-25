# ShutApp

An Android app that enforces daily per-app usage limits. Set a daily time budget for any installed app; ShutApp tracks usage in the background and blocks the app with a full-screen overlay once the limit is reached.

## Features

- **Per-app daily limits** — pick any installed app and set a daily time budget (5 min – 4 hrs)
- **Real-time tracking** — usage is tracked via `UsageStatsManager` event replay, not polling snapshots, so it reflects the current session immediately
- **Warning + blocking** — a notification fires at 80% usage; a full-screen overlay blocks the app at 100%, with a button back to the home launcher
- **Snooze** — grant 15 extra minutes from the blocking overlay when you need a bit more time
- **Weekend pause** — pause all limits until next Monday for planned breaks
- **Manual reset** — reset a single app's usage counter for the day without waiting for midnight
- **Survives reboot** — a `BOOT_COMPLETED` receiver restarts the monitor service after the device restarts
- **Pull-to-refresh** — manually re-sync usage data from the home screen, or it refreshes automatically when the app resumes from background

## Requirements

- Android 8.0 (API 26) or higher
- Three permissions, granted on first launch via an in-app setup screen:
  - **Usage Access** (`PACKAGE_USAGE_STATS`) — to measure per-app usage time
  - **Display over other apps** (`SYSTEM_ALERT_WINDOW`) — to show the blocking overlay
  - **Notifications** (Android 13+) — to send limit warnings

## Building

```bash
# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease

# Install directly to a connected device
./gradlew installDebug
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

## Testing

```bash
# Unit tests
./gradlew test

# Single test class
./gradlew :app:testDebugUnitTest --tests "com.odom.applimit.ExampleUnitTest"

# Instrumented tests (requires a connected device or emulator)
./gradlew connectedDebugAndroidTest
```

## Architecture

A foreground service (`UsageMonitorService`) polls usage stats while the screen is on and draws a `WindowManager` overlay (`BlockingOverlayManager`) when a limit is exceeded. Jetpack Compose + Hilt power the UI layer; Room persists per-app limits; WorkManager runs the midnight reset. See [`CLAUDE.md`](./CLAUDE.md) for the full architecture breakdown, data flow, and key design decisions.

## Tech Stack

- Kotlin, Jetpack Compose (Material3)
- Room (persistence), Hilt (DI), WorkManager (scheduled reset)
- `UsageStatsManager` for usage tracking, `WindowManager` for the blocking overlay
- Google Mobile Ads SDK (banner + interstitial)

## License

No license file is currently included in this repository.
