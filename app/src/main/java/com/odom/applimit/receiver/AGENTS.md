<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# receiver

## Purpose
Broadcast receivers for system events. Currently contains only `BootReceiver`, which restarts the foreground usage monitor service after device reboot.

## Key Files

| File | Description |
|------|-------------|
| `BootReceiver.kt` | `BroadcastReceiver` for `ACTION_BOOT_COMPLETED` — starts `UsageMonitorService` using `startForegroundService` on API 26+ |

## For AI Agents

### Working In This Directory
- `BootReceiver` is not Hilt-injected — it gets no dependencies at construction time
- The receiver is declared `exported="true"` in the manifest (required for system broadcast)
- Uses `startForegroundService` on API 26+ and `startService` below — do not collapse to a single call

## Dependencies

### Internal
- `service/UsageMonitorService`

<!-- MANUAL: -->
