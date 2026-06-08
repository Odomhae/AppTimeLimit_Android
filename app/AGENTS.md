<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# app

## Purpose
The single Android application module. Contains all app source code, resources, and the module-level build script. `minSdk = 26`, `targetSdk = 36`, package `com.odom.applimit`.

## Key Files

| File | Description |
|------|-------------|
| `build.gradle.kts` | Module build script — declares dependencies, Compose config, compileSdk 36 |
| `proguard-rules.pro` | ProGuard/R8 rules for release builds (minify currently disabled) |
| `src/main/AndroidManifest.xml` | Permissions, activity/service/receiver declarations |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `src/` | All source sets: main, test, androidTest (see `src/AGENTS.md`) |

## For AI Agents

### Working In This Directory
- All dependency versions come from the version catalog (`gradle/libs.versions.toml`) — do not hardcode versions
- Room and Hilt annotation processors use `ksp(...)`, not `kapt`
- `compileSdk` uses the `release(36)` DSL function — do not change to plain integer form

### Key Manifest Declarations
- `UsageMonitorService` — `foregroundServiceType="specialUse"` with `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property (required for targetSdk 36)
- `MainActivity` — `launchMode="singleTop"` so `onNewIntent` fires when overlay launches it while already running
- `BootReceiver` — exported, listens for `BOOT_COMPLETED`
- `<queries>` block — declares `ACTION_MAIN + CATEGORY_LAUNCHER` intent for Android 11+ package visibility without `QUERY_ALL_PACKAGES`

### Permissions Required
| Permission | Purpose |
|------------|---------|
| `PACKAGE_USAGE_STATS` | Read app usage events via `UsageStatsManager` |
| `SYSTEM_ALERT_WINDOW` | Draw blocking overlay over other apps |
| `POST_NOTIFICATIONS` | Send warning/blocked notifications (runtime on API 33+) |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | Run persistent monitor service |
| `RECEIVE_BOOT_COMPLETED` | Auto-start service after reboot |
| `INTERNET` + `ACCESS_NETWORK_STATE` | AdMob SDK |

## Dependencies

### Internal
- Source code at `src/main/java/com/odom/applimit/` (see `src/AGENTS.md`)

### External
- Jetpack Compose + Material3, Navigation Compose, Hilt, Room, WorkManager, Coroutines, Google Mobile Ads

<!-- MANUAL: -->
