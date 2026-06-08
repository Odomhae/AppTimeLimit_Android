<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# notification

## Purpose
Manages Android notification channels and builds/sends the three notification types used by the app: 80%-warning, limit-exceeded, and the persistent foreground-service notification.

## Key Files

| File | Description |
|------|-------------|
| `UsageNotifier.kt` | Creates notification channels on instantiation; exposes `sendWarning()`, `sendBlocked()`, and `buildServiceNotification()` |

## For AI Agents

### Working In This Directory
- `UsageNotifier` is instantiated manually in `UsageMonitorService.onCreate()` — it is not Hilt-injected
- Channels are created in the `init` block; channel creation is idempotent on Android O+ so repeated instantiation is safe
- Notification IDs use hash-based offsets (`NOTIFICATION_ID_WARNING_BASE + packageName.hashCode()`) so each app gets a stable, unique notification ID

### Notification Channels

| Channel ID | Importance | Purpose |
|------------|------------|---------|
| `usage_warning` | HIGH | 80% usage warning — fires at most once per day per app (guarded by `lastWarningDate`) |
| `usage_blocked` | HIGH | Limit exceeded alert — fires at most once per day per app (guarded by `lastBlockedDate`) |
| `monitor_service` | LOW | Persistent foreground service notification |

### Deduplication
Daily send-once logic lives in `UsageMonitorService.monitorOnce()`, not here. `UsageNotifier` always sends when called — the caller is responsible for checking `lastWarningDate`/`lastBlockedDate` against today before calling.

### Common Patterns
- All strings come from `strings.xml` via `context.getString(R.string.xxx)` — do not hardcode notification copy here

## Dependencies

### Internal
- `MainActivity` (for the warning notification tap `PendingIntent`)

### External
- `NotificationCompat`, `NotificationChannel`, `NotificationManager`, `PendingIntent`

<!-- MANUAL: -->
