<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# AppTimeLimit_Android

## Purpose
Android app that enforces daily per-app usage limits. A foreground service polls `UsageStatsManager` via event replay, and draws a `WindowManager` overlay when a limit is exceeded. Supports per-app snooze, AdMob monetization, and midnight automatic reset.

## Key Files

| File | Description |
|------|-------------|
| `build.gradle.kts` | Root build script — declares plugins (Hilt, KSP, Compose) without applying them |
| `settings.gradle.kts` | Module inclusion and repository declarations |
| `gradle.properties` | JVM args, AndroidX, Kotlin code-gen flags |
| `gradlew` / `gradlew.bat` | Gradle wrapper launchers (use `.bat` on Windows) |
| `CLAUDE.md` | AI-agent guidance: build commands, architecture decisions, design rules |
| `plan.md` | Informal task/planning scratch file |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `app/` | The single Android application module (see `app/AGENTS.md`) |
| `gradle/` | Gradle wrapper JARs and properties (see `gradle/AGENTS.md`) |

## For AI Agents

### Working In This Directory
- **Windows only**: use `gradlew.bat` instead of `./gradlew` for all build commands
- Plugin versions are declared in `gradle/libs.versions.toml`; always bump both `kotlin` and `ksp` together
- `CLAUDE.md` is the canonical source of architecture decisions — read it before making structural changes

### Build Commands
```
gradlew.bat assembleDebug          # debug APK
gradlew.bat assembleRelease        # release APK
gradlew.bat test                   # unit tests
gradlew.bat lint                   # lint
gradlew.bat clean assembleDebug    # clean build
```

### Testing Requirements
- Unit tests: `gradlew.bat :app:testDebugUnitTest`
- Instrumented tests require a connected device: `gradlew.bat connectedDebugAndroidTest`

## Dependencies

### External
- Kotlin 2.0.21 / KSP 2.0.21-1.0.27
- Compose BOM 2024.09.00 → Material3 1.3.0
- Room (KSP annotation processor), Hilt (KSP annotation processor)
- WorkManager, Google Mobile Ads SDK 23.3.0

<!-- MANUAL: -->
