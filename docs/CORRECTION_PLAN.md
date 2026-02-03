
# OpenClaw Android Companion – Phased Correction Blueprint

This document defines the **only acceptable path** to a functioning OpenClaw Android Companion.
Progression between phases is forbidden without proof artifacts.

---

## Phase 0 – Baseline Reality Check
**Objective:** Prove the app does anything real.

Required:
- Physical Android device (model + OS logged)
- `adb logcat` capture during launch

Proof:
- ForegroundService start log
- Notification visible within 5 seconds
- No ANRs

Completion Mark:
- `PHASE_0_COMPLETE.md` with logs attached

---

## Phase 1 – Foreground Service & Lifecycle Control
**Objective:** Establish a survivable execution core.

Tasks:
- Implement single authoritative ForegroundService
- Call `startForeground()` within Android timing limits
- Handle stop/restart explicitly

Proof:
- Log of service surviving screen-off + background
- Battery optimization warning handled

Completion Mark:
- Video + logs

---

## Phase 2 – Node Identity & Registration
**Objective:** Become a real OpenClaw node.

Tasks:
- Generate persistent node ID
- WebSocket connect + registration handshake
- Explicit ACK handling

Proof:
- Serialized registration payload
- Server ACK response
- Reconnect test

---

## Phase 3 – Capability Execution
**Objective:** Execute and report real capabilities.

Tasks:
- Implement `device_info`, `network_status`, `camsnap`
- Permission validation
- Failure propagation

Proof:
- Logs showing request → execution → response
- Controller receipt

---

## Phase 4 – UI Truth Enforcement
**Objective:** Eliminate fake UI.

Tasks:
- UI reflects service state only
- Disable buttons when backend unavailable

Proof:
- Screenshots with disconnected state
- Logs backing UI claims
