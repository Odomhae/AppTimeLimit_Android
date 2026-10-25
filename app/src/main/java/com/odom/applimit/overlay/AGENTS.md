<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# overlay

## Purpose
Draws a full-screen blocking overlay over the app that has exceeded its daily limit using `TYPE_APPLICATION_OVERLAY` (`SYSTEM_ALERT_WINDOW`). The overlay is a true modal: it captures all touch and key events so the user cannot interact with the blocked app beneath it.

## Key Files

| File | Description |
|------|-------------|
| `BlockingOverlayManager.kt` | Manages overlay lifecycle (`show`, `hide`, `isShowing`); contains the inner `BlockingLayout` class that overrides event dispatch |

## For AI Agents

### Working In This Directory
- **Do not add `FLAG_NOT_FOCUSABLE`, `FLAG_NOT_TOUCH_MODAL`, or `FLAG_NOT_TOUCHABLE`** to the `WindowManager.LayoutParams` — these flags would let touch events leak to the app below. Only `FLAG_LAYOUT_IN_SCREEN` is set
- `BlockingLayout.dispatchTouchEvent` calls `super` first (so child Button clicks fire), then returns `true` to consume the event
- `BlockingLayout.dispatchKeyEvent` always returns `true` — Back, volume, menu are all blocked
- Overlay is shown on `Dispatchers.Main` via `withContext` from the service's IO coroutine
- `show()` is idempotent for the same `packageName` — it returns early if the overlay for that package is already visible

### Buttons
There is no snooze / "+15 min" button (removed). The overlay has two actions:
- **"Open ShutApp"** (`R.string.overlay_open_app`) → `openAppLimit()` launches `MainActivity` with `EXTRA_FROM_BLOCKER=true` → triggers interstitial ad. The overlay hides on the next service poll once the blocked app is no longer in the foreground.
- **✕** (top-right) → `closeAll()`: goes to the home launcher, calls `killBackgroundProcesses(blockedPkg)`, and hides the overlay.

### Countdown Timer
The overlay shows a live countdown to midnight using `CountDownTimer(millisUntilMidnight(), 1_000L)`. The timer is cancelled in `hide()` to prevent leaks.

### Layout Structure
```
BlockingLayout (LinearLayout, MATCH_PARENT × MATCH_PARENT)
  ├── contentFrame (FrameLayout, weight=1)
  │     ├── innerContent (LinearLayout, gravity=CENTER)
  │     │     ├── app icon (ImageView, 160px)
  │     │     ├── app name (TextView, 30sp)
  │     │     ├── "Daily limit reached" (TextView, 20sp, red)
  │     │     ├── "X / Y minutes used" (TextView, 16sp)
  │     │     ├── countdown to midnight (TextView, 48sp)
  │     │     ├── "resets at midnight" subtitle (TextView, 14sp)
  │     │     └── "Open ShutApp" Button (gradient)
  │     └── ✕ close Button (Gravity.TOP|END)
  └── AdView (BANNER, WRAP_CONTENT) — pinned to bottom
```

## Dependencies

### Internal
- `MainActivity` (overlay → app launch intent)

### External
- `WindowManager`, `TYPE_APPLICATION_OVERLAY`, `AdView` (Google Mobile Ads)

<!-- MANUAL: -->
