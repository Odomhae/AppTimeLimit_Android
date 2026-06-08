<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# worker

## Purpose
WorkManager background workers. Currently contains `DailyResetWorker`, which fires at midnight to clear per-day notification deduplication flags and re-schedule itself for the next midnight.

## Key Files

| File | Description |
|------|-------------|
| `DailyResetWorker.kt` | `CoroutineWorker` — calls `resetDailyNotificationFlags()` then re-enqueues itself for next midnight using `ExistingWorkPolicy.REPLACE` |

## For AI Agents

### Working In This Directory
- **Not Hilt-injected** — uses `AppDatabase.getInstance(applicationContext)` directly
- Uses `ExistingWorkPolicy.REPLACE` for self-rescheduling (vs. `KEEP` used in `AppLimitApplication`) so the chain always advances to the true next midnight
- `AppLimitApplication` schedules the first run with `KEEP` — this prevents duplicate scheduling on each app restart while the chain is already alive
- The worker intentionally does **not** clear `usageAtResetMinutes` — that column becomes irrelevant when `UsageStatsManager` resets naturally at midnight

### Reset Scope
`resetDailyNotificationFlags()` sets `lastWarningDate = ''` and `lastBlockedDate = ''` for every row. This allows the service to send each notification type once again on the new calendar day.

## Dependencies

### Internal
- `data/AppDatabase`

### External
- WorkManager (`CoroutineWorker`, `OneTimeWorkRequestBuilder`, `ExistingWorkPolicy`)

<!-- MANUAL: -->
