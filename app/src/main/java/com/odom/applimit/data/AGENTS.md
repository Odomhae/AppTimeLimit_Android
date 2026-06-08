<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# data

## Purpose
Room persistence layer. Defines the database schema (`AppLimitEntity`), the DAO interface, the database singleton, and the repository that the ViewModel and service consume.

## Key Files

| File | Description |
|------|-------------|
| `AppLimitEntity.kt` | Room `@Entity` for table `app_limits` — primary key is `packageName`; stores limit, enable flag, daily notification dedup dates, and reset baseline |
| `AppLimitDao.kt` | DAO interface — `getAllLimits()` as `Flow`, `getEnabledLimits()` as `suspend`, upsert/delete/update, `resetDailyNotificationFlags()` |
| `AppDatabase.kt` | `RoomDatabase` singleton at version 2; includes `MIGRATION_1_2` adding `usageAtResetMinutes` column |
| `AppLimitRepository.kt` | `@Singleton` thin wrapper over the DAO; injected by Hilt into the ViewModel |

## For AI Agents

### Working In This Directory
- **Always bump `@Database(version = ...)` and add a `Migration` object** when changing the schema — fallback destructive migration is not configured
- Current migration: `MIGRATION_1_2` adds `usageAtResetMinutes INTEGER NOT NULL DEFAULT 0`
- `AppDatabase.getInstance(context)` is a thread-safe companion-object singleton used by non-Hilt components (service, worker); Hilt components use `@Inject AppLimitRepository`
- `DailyResetWorker` calls `resetDailyNotificationFlags()` — this sets `lastWarningDate` and `lastBlockedDate` to `''`; it intentionally does **not** clear `usageAtResetMinutes`

### Schema: app_limits

| Column | Type | Notes |
|--------|------|-------|
| `packageName` | TEXT PK | Android package identifier |
| `limitMinutes` | INTEGER | Daily limit in minutes |
| `isEnabled` | INTEGER (bool) | Whether the limit is active |
| `lastWarningDate` | TEXT | `"yyyy-MM-dd"` of last 80% warning, or `""` |
| `lastBlockedDate` | TEXT | `"yyyy-MM-dd"` of last block notification, or `""` |
| `usageAtResetMinutes` | INTEGER | Raw minutes at last user-triggered reset (baseline) |

### Common Patterns
- Effective usage = `currentRawMinutes - usageAtResetMinutes` (floored at 0)
- The service reads `getEnabledLimits()` (suspend, returns list) for the poll loop; the ViewModel observes `getAllLimits()` (Flow) for the UI

## Dependencies

### External
- Room (`@Entity`, `@Dao`, `@Database`, `RoomDatabase`, `Flow`, `@Insert`, `@Update`, `@Delete`, `@Query`)
- Hilt (`@Singleton`, `@Inject`)

<!-- MANUAL: -->
