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

## PHASE 7 — UI TRUTH ENFORCEMENT

### Objective
Ensure UI cannot lie.

### Tasks
1. UI observes service state only
2. Disable actions when backend unavailable
3. Display last error truthfully

### Mandatory Proof
- Screenshot of disconnected UI
- Logs backing UI state

### Completion Mark
`PHASE_7_COMPLETE`

---

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

