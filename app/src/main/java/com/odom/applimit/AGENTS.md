<!-- Parent: ../../../../../main/AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# com.odom.applimit

## Purpose
Root application package. Contains the `Application` subclass and `MainActivity`, plus eight feature sub-packages covering data, DI, notifications, overlay, receivers, service, UI, and workers.

## Key Files

| File | Description |
|------|-------------|
| `AppLimitApplication.kt` | `@HiltAndroidApp` Application — initializes AdMob SDK and schedules `DailyResetWorker` for next midnight on startup |
| `MainActivity.kt` | `@AndroidEntryPoint` single-activity host — manages interstitial ad lifecycle, handles `EXTRA_FROM_BLOCKER` intent from the overlay, sets Compose content tree |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `data/` | Room database, DAO, entity, repository (see `data/AGENTS.md`) |
| `di/` | Hilt `@Module` providing DB and DAO singletons (see `di/AGENTS.md`) |
| `notification/` | Notification channel setup and send helpers (see `notification/AGENTS.md`) |
| `overlay/` | `TYPE_APPLICATION_OVERLAY` blocking screen drawn over the locked app (see `overlay/AGENTS.md`) |
| `receiver/` | `BroadcastReceiver` for auto-start after device reboot (see `receiver/AGENTS.md`) |
| `service/` | Foreground `UsageMonitorService` polling loop (see `service/AGENTS.md`) |
| `ui/` | Jetpack Compose screens and `AppLimitViewModel` (see `ui/AGENTS.md`) |
| `worker/` | `DailyResetWorker` that clears notification flags at midnight (see `worker/AGENTS.md`) |

## For AI Agents

### Architecture Rules
- **Non-Hilt components** (`UsageMonitorService`, `BootReceiver`, `BlockingOverlayManager`, `DailyResetWorker`) get dependencies manually via `AppDatabase.getInstance(context)` — do not add `@AndroidEntryPoint` to these
- Only the ViewModel/UI layer uses Hilt `@Inject`
- `UsageStatsManager.queryEvents()` is used everywhere for real-time usage — `queryUsageStats()` is intentionally never used (it only commits after an app backgrounds, making it useless for live display)

### Key Constants
- `MainActivity.EXTRA_FROM_BLOCKER = "from_blocker"` — intent extra that triggers interstitial ad on overlay → app launch
- `DailyResetWorker.WORK_NAME = "daily_limit_reset"` — unique work name preventing duplicate resets

### Testing Requirements
- Changes to `AppLimitApplication` require a clean install to verify WorkManager scheduling
- Interstitial ad flow in `MainActivity` should be tested against a real device (AdMob test IDs are pre-configured)

## Dependencies

### Internal
- All sub-packages listed above

### External
- Hilt (`@HiltAndroidApp`, `@AndroidEntryPoint`)
- Google Mobile Ads SDK (`MobileAds`, `InterstitialAd`)
- WorkManager (`OneTimeWorkRequestBuilder`, `ExistingWorkPolicy.KEEP`)

<!-- MANUAL: -->
