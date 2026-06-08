<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# theme

## Purpose
Material3 theme definition for the app. Provides the `AppLimitTheme` composable used as the root wrapper in `MainActivity`, plus color and typography tokens.

## Key Files

| File | Description |
|------|-------------|
| `Theme.kt` | `AppLimitTheme` composable — supports dynamic color (Android 12+), dark/light, and a static fallback color scheme |
| `Color.kt` | Static color constants (`Purple80`, `PurpleGrey80`, `Pink80`, `Purple40`, `PurpleGrey40`, `Pink40`) used in the fallback color scheme |
| `Type.kt` | `Typography` object — overrides `bodyLarge`; other styles use Material3 defaults |

## For AI Agents

### Working In This Directory
- Dynamic color (`dynamicDarkColorScheme` / `dynamicLightColorScheme`) is enabled by default on Android 12+ — the static fallback colors in `Color.kt` only apply on older OS versions
- `AppLimitTheme` is called once in `MainActivity.setContent` — do not apply it inside individual screens
- `composeBom = "2024.09.00"` pins Material3 to 1.3.0; use the `progress: () -> Float` lambda form of `LinearProgressIndicator`, not the deprecated `Float` parameter

## Dependencies

### External
- Material3 (`MaterialTheme`, `darkColorScheme`, `lightColorScheme`, `dynamicDarkColorScheme`, `dynamicLightColorScheme`, `Typography`)

<!-- MANUAL: -->
