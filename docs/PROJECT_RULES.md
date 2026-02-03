# Project Rules — OpenClaw Android Companion (V4)

This project is a **device node** for OpenClaw. It must register, advertise capabilities, execute them, and return results.

## 1) NON‑NEGOTIABLES
1. ForegroundService owns execution (gateway + capabilities + state).
2. MainActivity hosts GUI only. No networking, no camera, no protocol logic.
3. `openclaw_dash.html` is the ONLY GUI and must be the first page shown on launch.
4. UI must be truthful: it displays ONLY service state, never guesses.
5. No mock data. No simulated success. No placeholder returns.
6. Every action must have: UI trigger → handler → service method → log trace → response.
7. Missing wiring must hard-fail visibly (disabled UI + error banner + logs).

## 2) REQUIRED FILES (EXACT)
These files MUST exist (names exact). If your structure differs, you must still provide these entrypoints.
- MainActivity.kt
- OpenClawForegroundService.kt
- OpenClawGateway.kt
- CapabilityRegistry.kt
- CamsnapCapability.kt
- UiState.kt
- UiEventController.kt

## 3) REQUIRED GUI LOCATION
- app/src/main/assets/openclaw_dash.html

No alternate dashboards. No “bootstrap page” that hides the real dashboard.
If you need a bootstrap, it must immediately redirect to openclaw_dash.html and be invisible.

## 4) REQUIRED JS API (MUST EXIST IN openclaw_dash.html)
The dashboard MUST call these functions (names exact):
- connectGateway()
- disconnectGateway()
- triggerCamsnap()
- requestStatus()

Each MUST route into UiEventController.kt and then into the ForegroundService.

## 5) REQUIRED LOG TAGS (MUST APPEAR IN LOGCAT)
- OPENCLAW_SERVICE
- OPENCLAW_GATEWAY
- OPENCLAW_PROTOCOL
- OPENCLAW_UI
- OPENCLAW_CAMSNAP

If logs aren’t present, the feature does not exist.

## 6) REQUIRED RESPONSE ENVELOPE (UI AND CONTROLLER)
All internal UI responses MUST use:
{ "ok": true|false, "code": "STRING", "message": "STRING", "data": { ...optional... } }

## 7) FORBIDDEN
- Swallowing exceptions without logs
- “TODO” placeholders in critical paths
- UI buttons that do nothing
- Activity-owned coroutines for gateway/camera
