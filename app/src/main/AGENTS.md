<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# main

## Purpose
Main production source set. Contains the Android manifest, all Kotlin source files, and all app resources (drawables, strings, XML configs).

## Key Files

| File | Description |
|------|-------------|
| `AndroidManifest.xml` | Declares permissions, `MainActivity`, `UsageMonitorService`, `BootReceiver`; includes `<queries>` for launcher-intent visibility on API 30+ |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `java/com/odom/applimit/` | All Kotlin source code organized by feature package (see `java/com/odom/applimit/AGENTS.md`) |
| `res/` | Android resources: strings (EN + KO), drawables, mipmap icons, XML configs (see `res/AGENTS.md`) |

## For AI Agents

### Working In This Directory
- The manifest's `<queries>` block is required — removing it breaks app-picker on API 30+ by hiding system apps like YouTube/Instagram from `queryIntentActivities`
- `UsageMonitorService` must keep `foregroundServiceType="specialUse"` with the `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property or the service will crash on targetSdk 36

<!-- MANUAL: -->
