# UI ↔ Backend Contract — openclaw_dash.html (V4)

This file defines EXACTLY what the dashboard must call, and what the app must implement.

## 1) UI Triggers (MUST exist in openclaw_dash.html)
| UI Element | Identifier | JS Function | Payload | Expected Response |
|---|---|---|---|---|
| Connect button | #btnConnect | connectGateway() | none | {ok,code,message,data{connected,registered}} |
| Disconnect button | #btnDisconnect | disconnectGateway() | none | {ok,code,message,data{connected:false}} |
| Camsnap button | #btnCamsnap | triggerCamsnap() | {quality?,maxBytes?} | {ok,code,message,data{imageId?,bytes?,mime?}} |
| Status refresh | #btnStatus | requestStatus() | none | {ok,code,message,data{state}} |

If your HTML uses different IDs, update THIS table and the HTML together. No drift allowed.

## 2) Backend Surface (choose ONE)
### Option A (preferred): Local HTTP/WS
- GET  /ui/state  → returns UiState envelope
- POST /ui/connect → connect gateway
- POST /ui/disconnect → disconnect gateway
- POST /ui/camsnap → execute camsnap
- WS   /ui/events → pushes state updates

### Option B: JS Bridge (WebView addJavascriptInterface)
Expose `OpenClawBridge` with methods:
- connectGateway()
- disconnectGateway()
- triggerCamsnap(jsonArgs)
- requestStatus()

Each must forward into OpenClawForegroundService and return envelope JSON.

## 3) Mandatory Wiring Proof (per action)
For EACH action above you must produce a log trace showing:
OPENCLAW_UI: UI_EVENT <name>
OPENCLAW_SERVICE: SERVICE_CALL <name>
OPENCLAW_*: RESULT <ok/fail> <code>
