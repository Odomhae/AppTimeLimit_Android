<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# di

## Purpose
Hilt dependency injection module. Provides `AppDatabase` and `AppLimitDao` as `@Singleton` bindings installed in `SingletonComponent`, making them available for injection throughout the Hilt component hierarchy.

## Key Files

| File | Description |
|------|-------------|
| `AppModule.kt` | `@Module @InstallIn(SingletonComponent)` — provides `AppDatabase` via `AppDatabase.getInstance()` and `AppLimitDao` via `db.appLimitDao()` |

## For AI Agents

### Working In This Directory
- `AppDatabase` and `AppLimitDao` are provided here for Hilt-injected components only (ViewModel, Repository)
- Non-Hilt components (`UsageMonitorService`, `BlockingOverlayManager`, `DailyResetWorker`, `BootReceiver`) call `AppDatabase.getInstance(context)` directly — do not try to inject into them via Hilt
- `AppLimitRepository` uses `@Inject constructor` and is provided automatically by Hilt via constructor injection — no `@Provides` needed for it

## Dependencies

### Internal
- `data/AppDatabase`, `data/AppLimitDao`

### External
- Hilt (`@Module`, `@InstallIn`, `@Provides`, `@Singleton`, `SingletonComponent`, `@ApplicationContext`)

<!-- MANUAL: -->
