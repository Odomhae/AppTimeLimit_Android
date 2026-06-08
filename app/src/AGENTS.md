<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# src

## Purpose
Android Gradle source sets for the app module. `main` holds all production code and resources; `test` and `androidTest` hold unit and instrumented tests respectively.

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `main/` | Production source code and resources (see `main/AGENTS.md`) |
| `test/` | JVM unit tests — run without a device |
| `androidTest/` | Instrumented tests — require a connected device or emulator |

## For AI Agents

### Testing Requirements
- Unit tests: `gradlew.bat :app:testDebugUnitTest`
- Instrumented tests: `gradlew.bat connectedDebugAndroidTest` (device required)

<!-- MANUAL: -->
