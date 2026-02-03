# OpenClaw Android Companion — CODEX Multi‑Phase Build Blueprint (V4, Explicit)

This blueprint is written to stop Codex from doing the usual thing: polishing UI while nothing works.

## GLOBAL STOP RULE
Codex may not advance a phase without the receipts listed for that phase.
If you cannot produce receipts, output `PHASE_X_FAILED` and stop.

## Receipts allowed
- git diff / commit hash
- adb logcat excerpts containing required tags
- screenshots/video (describe required content)
- protocol transcript (TX/RX JSON payloads)

---

## PHASE 0 — Boot + Foreground Reality
### Build Goal
Prove ForegroundService starts correctly and GUI loads openclaw_dash.html.

### Implement
1. Ensure MainActivity loads `file:///android_asset/openclaw_dash.html` first.
2. On create, start OpenClawForegroundService (startForegroundService).
3. In service, call startForeground() immediately with visible notification.
4. Emit log: `OPENCLAW_UI: GUI_LOADED openclaw_dash.html` when WebView finishes loading.

### Receipts
- Logcat showing:
  - OPENCLAW_SERVICE: SERVICE_START
  - OPENCLAW_SERVICE: START_FOREGROUND
  - OPENCLAW_UI: GUI_LOADED openclaw_dash.html
- Screenshot: notification visible + dashboard visible.
### Completion Mark
`PHASE_0_COMPLETE`

---

## PHASE 1 — Persistent Node Identity
### Build Goal
Node ID is stable across restarts.

### Implement
- NodeStore with DataStore/SharedPreferences (choose one, document it).
- Generate nodeId once (UUID), persist, reuse.
- Log: OPENCLAW_SERVICE: NODE_ID <id>

### Receipts
- Two app restarts, same NODE_ID log line.
### Completion Mark
`PHASE_1_COMPLETE`

---

## PHASE 2 — WebSocket Connect + Register + ACK
### Build Goal
Gateway connects and receives register_ack.

### Implement
- OpenClawGateway with:
  - connect(url)
  - sendRegister()
  - parse register_ack
  - maintain sessionId
- Log TX/RX in OPENCLAW_PROTOCOL.

### Receipts
- OPENCLAW_PROTOCOL: TX register {...}
- OPENCLAW_PROTOCOL: RX register_ack {... ok:true ...}
### Completion Mark
`PHASE_2_COMPLETE`

---

## PHASE 3 — Heartbeat + Reconnect Discipline
### Build Goal
Heartbeat runs; disconnect triggers backoff reconnect.

### Implement
- heartbeat schedule in service or gateway
- timeout -> disconnect -> reconnect(backoff+jitter)
- ensure no reconnect storm (cap attempts)

### Receipts
- HEARTBEAT_SENT + heartbeat_ack
- WS_DISCONNECTED + RECONNECT scheduledInMs=...
### Completion Mark
`PHASE_3_COMPLETE`

---

## PHASE 4 — UI → Backend Wiring Contract (openclaw_dash.html)
### Build Goal
Dashboard buttons call real service methods and return envelopes.

### Implement
- Choose backend surface (Option A local HTTP/WS, or Option B JS Bridge) and document it.
- Implement handlers for:
  - connectGateway
  - disconnectGateway
  - requestStatus
- Return envelope JSON and update UiState.

### Receipts
For each action:
- OPENCLAW_UI: UI_EVENT <name>
- OPENCLAW_SERVICE: SERVICE_CALL <name>
- OPENCLAW_SERVICE: STATE connected=... registered=...
### Completion Mark
`PHASE_4_COMPLETE`

---

## PHASE 5 — Capability Framework + device_info + network_status
### Build Goal
Controller can request a capability and get a real result or explicit failure.

### Implement
- CapabilityRegistry dispatch
- Implement DeviceInfoCapability + NetworkStatusCapability
- Implement capability_request -> capability_result

### Receipts
- RX capability_request
- TX capability_result ok=true with real data
### Completion Mark
`PHASE_5_COMPLETE`

---

## PHASE 6 — CAMSNAP (CameraX) End‑to‑End
### Build Goal
Camsnap produces real bytes and sends them.

### Implement
- CamsnapCapability using CameraX
- Permission checks with explicit PERMISSION_DENIED failure
- Encode JPEG, enforce maxBytes, return imageId/bytes/mime

### Receipts
- OPENCLAW_CAMSNAP: CAPTURE_OK bytes=...
- OPENCLAW_PROTOCOL: TX capability_result ok=true {bytes,mime,imageId}
- Controller receipt noted (log or ack)
### Completion Mark
`PHASE_6_COMPLETE`

---

## PHASE 7 — Truthful UI State Feed
### Build Goal
openclaw_dash.html shows real state, updates live.

### Implement
- Push UiState to UI via:
  - WS /ui/events, OR
  - JS bridge callback, OR
  - polling /ui/state
- Disconnect network: UI flips to disconnected and shows lastError.

### Receipts
- Screenshot disconnected state
- Logs proving state transition
### Completion Mark
`PHASE_7_COMPLETE`

---

## PHASE 8 — Hard Failure Mode Tests
### Build Goal
Explicit failures with correct codes and UI messaging.

### Tests
- deny camera permission -> PERMISSION_DENIED
- controller unreachable -> CONTROLLER_UNREACHABLE
- camera busy -> CAMERA_UNAVAILABLE

### Receipts
- logs + UI error banner screenshot per test
### Completion Mark
`PHASE_8_COMPLETE`
