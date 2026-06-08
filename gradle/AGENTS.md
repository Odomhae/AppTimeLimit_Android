<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# gradle

## Purpose
Gradle build infrastructure — wrapper binaries and the version catalog that centralizes all dependency and plugin versions for the project.

## Key Files

| File | Description |
|------|-------------|
| `libs.versions.toml` | Version catalog: all dependency versions, library aliases, plugin aliases |
| `wrapper/gradle-wrapper.properties` | Gradle distribution URL and wrapper settings |
| `wrapper/gradle-wrapper.jar` | Gradle wrapper bootstrap JAR (do not edit) |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `wrapper/` | Gradle wrapper binaries |

## For AI Agents

### Working In This Directory
- `libs.versions.toml` is the only place to add or bump dependency versions
- **Critical constraint**: `ksp` version must match `kotlin` exactly — currently `kotlin = "2.0.21"` and `ksp = "2.0.21-1.0.27"`. Bumping Kotlin requires bumping KSP first
- `composeBom = "2024.09.00"` pins Material3 to 1.3.0; `LinearProgressIndicator` uses the `progress: () -> Float` lambda API

### Common Patterns
- Plugin aliases referenced in build scripts as `alias(libs.plugins.xxx)`
- Library aliases referenced as `implementation(libs.xxx)` or `ksp(libs.xxx)`

<!-- MANUAL: -->
