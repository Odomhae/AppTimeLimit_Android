<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# service

## Purpose
`UsageMonitorService` — the heart of the app. A `START_STICKY` foreground service that runs a coroutine poll loop while the screen is on, compares each app's effective daily usage against its limit, shows/hides the blocking overlay, and sends warning/blocked notifications.

## Key Files

| File | Description |
|------|-------------|
| `UsageMonitorService.kt` | Foreground service with IO coroutine scope; contains `monitorOnce()`, `getTodayUsageMs()`, `getForegroundApp()`, `nextPollInterval()` |

## For AI Agents

### Working In This Directory
- **Not Hilt-injected** — all dependencies (`AppDatabase`, `UsageNotifier`, `BlockingOverlayManager`) are constructed manually in `onCreate()`
- `onStartCommand` launches an immediate `monitorOnce()` check so the overlay appears without waiting for the first poll tick
- Poll interval: **3 s** when any enabled app is ≥80% used; **10 s** otherwise
- Screen-off aware: `powerManager.isInteractive` check skips poll cycles while the screen is off

### Usage Calculation
```
rawMs       = queryEvents(startOfDay, now) replay of FOREGROUND/BACKGROUND events
              + (now - lastForegroundMs) if app is currently open
effectiveMs = rawMs - (entity.usageAtResetMinutes * 60_000)  (floored at 0)
usedMinutes = effectiveMs / 60_000
```
`queryUsageStats()` is never called — it only commits a session after the app backgrounds and is useless for real-time blocking.

### Foreground Detection
`getForegroundApp()` uses a 10-minute `queryEvents` window, tracking the last `MOVE_TO_FOREGROUND`/`ACTIVITY_RESUMED` and clearing on `MOVE_TO_BACKGROUND`/`ACTIVITY_PAUSED` to identify the current top app.

### Blocking Logic (per limit, per poll)
| Condition | Action |
|-----------|--------|
| `usedMinutes >= limitMinutes` AND `lastBlockedDate != today` | Update DB, send blocked notification |
| `usedMinutes >= limitMinutes` AND foreground == blocked pkg | Show overlay (with snooze lambda) |
| `usedMinutes / limitMinutes >= 0.8f` AND `lastWarningDate != today` | Update DB, send warning notification |
| Overlay showing AND foreground ≠ blocked pkg | Hide overlay |

### Snooze
`SNOOZE_MINUTES = 15`. Snooze lambda (called from overlay) increments `limitMinutes` by 15 and clears `lastBlockedDate` in the DB. The overlay hides on the next poll when `MainActivity` is detected in the foreground.

## Dependencies

### Internal
- `data/AppDatabase` (direct singleton access)
- `notification/UsageNotifier`
- `overlay/BlockingOverlayManager`

### External
- `UsageStatsManager`, `PowerManager`, Coroutines (`Dispatchers.IO`, `Dispatchers.Main`, `SupervisorJob`)

<!-- MANUAL: -->
