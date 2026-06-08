<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# ui

## Purpose
Jetpack Compose UI layer. Contains the navigation graph, all screens, the shared `AppLimitViewModel`, and the Material3 theme. All screens are composable functions; state is hoisted into the ViewModel via `StateFlow`.

## Key Files

| File | Description |
|------|-------------|
| `AppLimitViewModel.kt` | `@HiltViewModel` — owns `limits` Flow from Room, `usageMap` (milliseconds per package), `isLoadingUsage`; drives a 3-second IO poll loop; exposes `upsertLimit`, `deleteLimit`, `resetUsage`, `getInstalledApps` |
| `NavGraph.kt` | `AppNavGraph` composable — checks permissions on startup, routes to `PermissionSetupScreen` or `HomeScreen`; manages 3-destination back stack |
| `HomeScreen.kt` | Main screen — `LazyColumn` of `LimitCard` items with progress bars; FAB to add; inline `EditLimitDialog` for editing limits; back-press exit dialog with banner ad |
| `AddLimitScreen.kt` | Two-step flow: app picker (search + list) → limit setter (slider 5–240 min in 5-min steps); loads app list on IO |
| `PermissionSetupScreen.kt` | Onboarding screen — shows status of all 3 required permissions, opens system settings for each; auto-advances when all granted |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `theme/` | Material3 color scheme, typography, and `AppLimitTheme` composable (see `theme/AGENTS.md`) |

## For AI Agents

### Key Design Decisions
- **`usageMap` stores milliseconds, not minutes** — `StateFlow` uses structural equality; storing minutes causes deduplication within the same minute, freezing the UI. Milliseconds change every 3-second poll. `HomeScreen` converts: `((usageMap[pkg] ?: 0L) / 60_000L).toInt()`
- **`init` coroutine on `Dispatchers.IO`** drives the ViewModel's poll loop — no `flatMapLatest`, no LiveData
- **App picker uses `queryIntentActivities(ACTION_MAIN + CATEGORY_LAUNCHER)`** — not `getInstalledApplications()`. Pre-installed apps (`FLAG_SYSTEM`) are missed by flag-based filters; the launcher query returns exactly what appears in the app drawer

### Screen Navigation
```
start → checkAllPermissionsGranted?
          yes → "home"    (HomeScreen)
          no  → "permissions" (PermissionSetupScreen)
                    ↓ all granted
                "home" (popUpTo "permissions" inclusive)
"home" → FAB → "add_limit" (AddLimitScreen)
```

### `checkAllPermissionsGranted` (package-visible, defined in `PermissionSetupScreen.kt`)
Checks all three permissions: `PACKAGE_USAGE_STATS` (via `AppOpsManager`), `SYSTEM_ALERT_WINDOW` (via `Settings.canDrawOverlays`), `POST_NOTIFICATIONS` (runtime, API 33+).

### Ad Integration
- `HomeScreen`: `BannerAd()` composable as `Scaffold.bottomBar`; exit dialog also embeds a banner
- `EditLimitDialog` confirm and reset actions call `onShowAd()` → `MainActivity.tryShowAd()` → interstitial
- All ad unit IDs come from `strings.xml` (test IDs — replace before publishing)

### `formatMinutes` (internal, `HomeScreen.kt`)
Shared formatting helper: `< 60` → `"X min"`, `% 60 == 0` → `"X hr"`, else `"X hr Y min"`. Uses `strings.xml` resources.

### Common Patterns
- Composable screens receive a `viewModel: AppLimitViewModel = hiltViewModel()` default parameter — do not pass the VM down manually
- Slider range: 5–240 min, snapped to 5-min steps via `(it / 5f).roundToInt() * 5`
- App icons are loaded with `packageManager.getApplicationIcon(pkg).toBitmap().asImageBitmap()` inside `remember(packageName)` to avoid re-loading on recomposition

## Dependencies

### Internal
- `data/AppLimitEntity`, `data/AppLimitRepository`
- `service/UsageMonitorService` (started by `HomeScreen` via `LaunchedEffect`)

### External
- Hilt Navigation Compose (`hiltViewModel`)
- Navigation Compose (`rememberNavController`, `NavHost`)
- Material3 (`Scaffold`, `Card`, `LinearProgressIndicator`, `AlertDialog`, `Slider`, `TopAppBar`)
- Google Mobile Ads (`AdView`, `InterstitialAd`)
- `UsageStatsManager` (ViewModel reads usage directly)

<!-- MANUAL: -->
