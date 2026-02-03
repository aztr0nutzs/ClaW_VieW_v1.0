# OpenClaw Android Companion – CODEX Multi‑Phase Build Blueprint

This blueprint is written **explicitly for Codex execution**. It is not advisory. It is a **contract**. Codex must not proceed to a later phase until all completion marks for the current phase are satisfied and proven.

If proof is missing, the phase is **FAILED**.

---

## GLOBAL CODEX RULES (APPLY TO ALL PHASES)

- Assume **hostile Android conditions** at all times
- Prefer **explicit failure** over silent success
- No UI‑only changes count as progress
- No mock data, no simulated responses
- Every phase must produce **receipts** (logs, diffs, artifacts)

Codex must stop and report failure if any rule is violated.

---

## PHASE 0 — EXECUTION BASELINE (REALITY CHECK)

### Objective
Prove the app executes **anything real** on a physical Android device.

### Tasks
1. Build and install on a **physical Android device**
2. Launch app
3. Start ForegroundService within Android time limits
4. Display persistent notification

### Mandatory Proof
- Device model + Android version
- `adb logcat` showing:
  - Service creation
  - `startForeground()` called < 5s
  - No ANR
- Screenshot of foreground notification

### Completion Mark
`PHASE_0_COMPLETE`

---

## PHASE 1 — FOREGROUND SERVICE AUTHORITY

### Objective
Establish a **single authoritative execution core**.

### Tasks
1. Implement one ForegroundService that owns:
   - Networking
   - Capabilities
   - Gateway state
2. Explicit lifecycle handling:
   - onStartCommand
   - onDestroy
3. Survive:
   - Screen off
   - App background

### Mandatory Proof
- Log evidence of service surviving background
- Battery optimization prompt handled or logged

### Completion Mark
`PHASE_1_COMPLETE`

---

## PHASE 2 — NODE IDENTITY & PERSISTENCE

### Objective
Become a **stable OpenClaw node**.

### Tasks
1. Generate persistent Node ID
2. Store in durable storage
3. Reload same ID after app restart

### Mandatory Proof
- Log showing same Node ID across restarts
- Storage file/DB entry

### Completion Mark
`PHASE_2_COMPLETE`

---

## PHASE 3 — GATEWAY CONNECTION & HANDSHAKE

### Objective
Prove real communication with an OpenClaw controller.

### Tasks
1. WebSocket client implementation
2. Explicit registration handshake
3. Capability manifest transmission
4. Server ACK validation

### Mandatory Proof
- Serialized registration payload
- Server ACK log
- Reconnect test after forced disconnect

### Completion Mark
`PHASE_3_COMPLETE`

---

## PHASE 4 — HEARTBEAT & RESILIENCE

### Objective
Prove the node remains alive under instability.

### Tasks
1. Heartbeat implementation
2. Timeout detection
3. Reconnect with backoff

### Mandatory Proof
- Heartbeat send/receive logs
- Forced network drop recovery

### Completion Mark
`PHASE_4_COMPLETE`

---

## PHASE 5 — CAPABILITY FRAMEWORK

### Objective
Execute **real on‑device capabilities**.

### Tasks
1. Capability registry
2. Permission mapping per capability
3. Structured success/failure responses

### Mandatory Proof
- Capability execution logs
- Permission denial test

### Completion Mark
`PHASE_5_COMPLETE`

---

## PHASE 6 — CAMSNAP (CameraX)

### Objective
Prove real camera capture and transmission.

### Tasks
1. CameraX setup in ForegroundService
2. Runtime permission enforcement
3. Capture → encode → transmit

### Mandatory Proof
- Camera permission grant log
- Image capture log
- Server receipt confirmation

### Completion Mark
`PHASE_6_COMPLETE`

---

## PHASE 7 — MAIN GUI INTEGRATION (openclaw_dash.html)

### Objective
Make **openclaw_dash.html** the app’s **single source of truth GUI** and ensure every UI action is **wired to real backend execution paths**.

This phase is not "make it look right". This phase is: **buttons trigger real service code, and state displayed is real**.

### Non‑Negotiable Rules
- **openclaw_dash.html must be the main GUI** loaded by the app (no alternate dashboards, no placeholder pages).
- The HTML must be loaded from exactly one canonical location:
  - Either `file:///android_asset/.../openclaw_dash.html` (assets) and the same page may be served by the local server when available.
- The UI must have a single transport to the backend:
  - **Option A:** HTTP/WS to the local gateway server (recommended)
  - **Option B:** `addJavascriptInterface` bridge (allowed only if it routes into the ForegroundService)
- **No UI action exists without a backend handler**. If no handler exists, the button must be disabled and show an explicit error.

### Tasks
1. **Set openclaw_dash.html as the default launch GUI**
   - Update the Android entry activity (or WebView host) to load `openclaw_dash.html` first.
   - Any existing bootstrap/placeholder page must be removed or become a thin redirect to `openclaw_dash.html`.

2. **Define the UI ↔ Backend contract (explicit API map)**
   - Create a single authoritative mapping (code or markdown) of:
     - UI action name (button id / JS function)
     - Backend endpoint or JS bridge method
     - ForegroundService method executed
     - Success/failure response schema
   - Minimum required actions:
     - Connect/Disconnect gateway
     - Show registration status
     - Trigger `camsnap`
     - Show last error
     - Show last heartbeat timestamp

3. **Wire every UI action to the ForegroundService**
   - If HTTP/WS: implement endpoints in the local server that forward into the ForegroundService.
   - If JS bridge: implement a thin `OpenClawBridge` that forwards into the ForegroundService and returns structured responses.
   - Every handler must:
     - Validate service is running
     - Validate required permissions
     - Execute the real action
     - Return a structured response: `{ ok: boolean, code: string, message: string, data?: object }`

4. **Wire real state into the UI (no lies)**
   - The UI must be updated only from actual service state:
     - connected/disconnected
     - registered/unregistered
     - lastHeartbeat
     - lastError
     - lastCapabilityResult
   - Implement a single real-time state feed:
     - **Option A:** WebSocket `/ui/events` emitting state updates
     - **Option B:** Polling `/ui/state` every N seconds (configurable and rate-limited)

5. **Hard-fail for missing wiring**
   - If the UI calls an endpoint/method that does not exist, the UI must:
     - display an explicit error banner
     - log it (Android log and server log)
     - keep the button disabled until wiring exists

### Mandatory Proof
- Evidence that `openclaw_dash.html` is the **first page shown** after launch:
  - Log line: `GUI_LOADED openclaw_dash.html` (or equivalent)
  - Screenshot of the dashboard on a physical device
- For **each required UI action**, provide:
  - The UI trigger identifier (button id / JS function)
  - The backend handler location (file + function)
  - A log trace showing: `UI_EVENT -> SERVICE_CALL -> RESULT`
- Demonstrate **truthful UI**:
  - Disconnect network, show UI flips to disconnected
  - Reconnect, show heartbeat resumes

### Completion Mark

`PHASE_7_COMPLETE`

## PHASE 8 — FAILURE MODES & HARDENING

### Objective
Prove failure handling is real.

### Tasks
1. Permission revocation mid‑run
2. Camera unavailable
3. Server unreachable

### Mandatory Proof
- Explicit failure logs
- No silent success

### Completion Mark
`PHASE_8_COMPLETE`

---

## PHASE 9 — FINAL ACCEPTANCE

### Objective
Declare production readiness.

### Tasks
1. Full restart test
2. Re‑registration
3. Capability execution

### Mandatory Proof
- End‑to‑end log transcript
- Controller confirmation

### Completion Mark
`PHASE_9_COMPLETE`

---

## CODEX STOP CONDITIONS

Codex must STOP if:
- A phase lacks proof
- Logs contradict claims
- UI exists without backend execution

Completion without receipts is invalid.

