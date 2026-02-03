# Integration Notes — openclaw_dash.html Main GUI (V5)

This pack provides an actual dashboard implementation + Kotlin wiring skeleton.

## What you get
- `openclaw_dash.html` with:
  - required IDs: btnConnect, btnDisconnect, btnStatus, btnCamsnap
  - required functions: connectGateway(), disconnectGateway(), requestStatus(), triggerCamsnap()
  - hard-fail if OpenClawBridge is missing
  - truthful UI state pills
- `MainActivity.kt` that loads:
  - `file:///android_asset/openclaw_dash.html`
- `OpenClawBridge.kt` exposing the required methods to JS
- `OpenClawForegroundService.kt` skeleton that:
  - starts foreground immediately
  - logs required markers
  - accepts UI commands via queue (wiring target)
- `UiState.kt` and `UiCommand.kt` models

## How to apply to your repo
1) Copy `android/app/src/main/assets/openclaw_dash.html` into your Android app module assets.
2) Ensure your launcher activity is the provided MainActivity or loads the asset URL exactly.
3) Ensure WebView adds `OpenClawBridge` JavascriptInterface with name `OpenClawBridge`.
4) Implement real gateway/camsnap logic inside the ForegroundService.

## Required proof once integrated
- logcat contains:
  - OPENCLAW_UI: GUI_LOADED openclaw_dash.html
  - OPENCLAW_UI: UI_EVENT connectGateway
  - OPENCLAW_SERVICE: SERVICE_CALL connectGateway
