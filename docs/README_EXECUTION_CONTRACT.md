# OpenClaw Android Companion — Execution Contract (V4)

If you want “guided and instructive,” here it is: this is the project’s exact contract.

## REQUIRED FILES (EXACT NAMES)
MainActivity.kt
- Hosts WebView
- Loads openclaw_dash.html only
- No business logic

OpenClawForegroundService.kt
- ForegroundService
- Owns gateway + capabilities + state
- Calls startForeground() fast (service dies otherwise)

OpenClawGateway.kt
- WebSocket client
- register + ACK validation
- heartbeat
- command dispatch

CapabilityRegistry.kt
- Maps capability → executor
- Rejects unknown capabilities

CamsnapCapability.kt
- CameraX capture
- Permission enforcement
- Encode + send result

UiState.kt
- Single truth state model

UiEventController.kt
- Receives UI events (JS) and forwards to service

## REQUIRED GUI (ONLY)
app/src/main/assets/openclaw_dash.html

## REQUIRED JS API (NAMES EXACT)
connectGateway()
disconnectGateway()
triggerCamsnap()
requestStatus()

Each must call into UiEventController (JS bridge or HTTP/WS to local server).

## REQUIRED LOG TAGS
OPENCLAW_SERVICE
OPENCLAW_GATEWAY
OPENCLAW_PROTOCOL
OPENCLAW_UI
OPENCLAW_CAMSNAP

## REQUIRED PROTOCOL ENVELOPES (MINIMUM)
### Register (client → controller)
{
  "type": "register",
  "nodeId": "<persistent>",
  "platform": "android",
  "appVersion": "<semver>",
  "capabilities": ["device_info","network_status","camsnap"]
}

### Register ACK (controller → client)
{
  "type": "register_ack",
  "nodeId": "<same-id>",
  "ok": true,
  "sessionId": "<string>"
}

No ACK = NOT REGISTERED.

### Heartbeat
{ "type": "heartbeat", "nodeId": "<same-id>", "sessionId": "<sessionId>" }

## BUILD ACCEPTANCE (BINARY)
Accepted ONLY if:
1) Foreground notification appears
2) Logcat shows register → register_ack
3) CAMSNAP button triggers:
   - permission check
   - capture
   - send
   - controller receipt log

No exceptions.
