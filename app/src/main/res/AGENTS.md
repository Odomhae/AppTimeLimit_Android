<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# res

## Purpose
Android application resources. Includes localized strings (English default + Korean), app icon mipmaps at all densities, vector drawables for the adaptive icon, and XML configuration files for backup and data extraction rules.

## Key Files

| File | Description |
|------|-------------|
| `values/strings.xml` | All English UI strings, notification copy, AdMob test IDs |
| `values-ko/strings.xml` | Korean translations of all strings |
| `values/colors.xml` | Color resources (supplementary — theme uses dynamic/Material3 colors) |
| `values/themes.xml` | App theme declaration referencing Material3 |
| `drawable/ic_launcher_background.xml` | Adaptive icon background layer |
| `drawable/ic_launcher_foreground.xml` | Adaptive icon foreground layer |
| `mipmap-anydpi/ic_launcher.xml` | Adaptive icon descriptor (all densities) |
| `xml/backup_rules.xml` | Cloud backup inclusion/exclusion rules |
| `xml/data_extraction_rules.xml` | Android 12+ data extraction policy |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `drawable/` | Vector XML drawables for adaptive launcher icon |
| `mipmap-anydpi/` | Adaptive icon XML descriptors |
| `mipmap-hdpi/` … `mipmap-xxxhdpi/` | Raster launcher icon WebP at each density |
| `values/` | Default (English) string, color, and theme resources |
| `values-ko/` | Korean string overrides |
| `xml/` | Backup and data extraction configuration |

## For AI Agents

### Working In This Directory
- **AdMob IDs in `strings.xml` are Google test IDs** — replace `TEST_admob_app_id`, `TEST_admob_interstitial_id`, `TEST_admob_banner_id` before publishing to the Play Store
- When adding a new UI string, add it to both `values/strings.xml` (English) and `values-ko/strings.xml` (Korean) to avoid missing-translation lint warnings
- String formatting uses positional placeholders (`%1$d`, `%2$s`) — do not use bare `%d`/`%s` in strings that take arguments

### Common Patterns
- Time display strings (`time_minutes`, `time_hours`, `time_hours_minutes`) are used by `HomeScreen.formatMinutes()` — keep their argument order stable
- Notification strings are consumed by `UsageNotifier` via `context.getString(R.string.xxx)` — all hardcoded strings were migrated here

<!-- MANUAL: -->
