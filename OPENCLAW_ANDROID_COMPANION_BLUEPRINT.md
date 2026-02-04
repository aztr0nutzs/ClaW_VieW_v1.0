# OpenClaw Android Companion — Bulletproof Build Blueprint (HTML GUI, No Hallucinations)

This blueprint is designed to keep Codex/Copilot/Gemini from inventing reality.
It assumes your app’s main UI is **openclaw_dash.html** and the side menu mirrors OpenClaw Control UI items.

## Non‑Negotiable Anti‑Hallucination Rules
1. **No RPC name is implemented unless it is explicitly listed in the OpenClaw Control UI docs list.**
2. **Every phase requires receipts**: logcat lines, TX/RX JSON frames, screenshots, commit hash.
3. If pairing/auth fails, UI must show a blocking error including close code/reason (example: pairing required).
4. Android app must work in real networks (not just localhost).
5. No insecure workarounds unless explicitly enabled and documented.

---

## Phase 0 — Spec Freeze: Menu Items + Behavior Matrix
**Goal:** Lock the exact side menu items and what each must do.

**Deliverables:**
- `MENU_SPEC.md` mapping: menu item → RPC(s) → UI widgets → failure states
- `RPC_MAP.md` mapping: JS function/button id → bridge method → gateway RPC → expected response

**Receipts:**
- Screenshot: side menu shows all required items
- Commit hash + file diffs

✅ Completion: `PHASE_0_COMPLETE`

---

## Phase 1 — Android Shell: HTML is the Main GUI
**Goal:** App launches directly into WebView with **openclaw_dash.html**.

**Requirements:**
- Launcher Activity hosts WebView only
- Loads `file:///android_asset/openclaw_dash.html`
- JS bridge exists; exposes only allowed calls

**Receipts:**
- logcat: `OPENCLAW_UI: GUI_LOADED openclaw_dash.html`
- Screenshot: dashboard visible with “Disconnected” state

✅ Completion: `PHASE_1_COMPLETE`

---

## Phase 2 — Gateway Connection: Protocol Handshake
**Goal:** Implement WS connection + authentication + pairing flow with persistent device token.

**Implementation Steps:**
1. Generate and persist device identity (stable node/device id).
2. Connect to gateway WS URL (configurable).
3. Send handshake with protocol version bounds.
4. Handle challenge/nonce flow (sign if required).
5. If pairing required:
   - show UI blocking state with device id and approval instructions
6. Persist returned device token after successful hello/ack.

**Receipts:**
- TX/RX transcript saved: `receipts/phase2/handshake.jsonl`
- Screenshot: pairing required screen when unapproved
- Screenshot: connected/registered state after approval
- logcat: connect → hello/ack → token persisted

✅ Completion: `PHASE_2_COMPLETE`

---

## Phase 3 — Operator Surface: Side Menu Actually Works
**Goal:** Each menu item triggers real documented RPCs and renders results.

**Required menu surfaces (minimum functional):**
- Chat: history/send/abort/inject
- Channels: status/login/config patch flows
- Sessions: list/patch
- Cron: cron.*
- Skills: skills.*
- Nodes: node.list
- Exec Approvals: exec.approvals.*
- Config: config.*
- Debug: status/health/models
- Logs: logs.tail
- Update: update.run

**Receipts (per menu item):**
- Screenshot: data visible in that view
- TX/RX sample: `receipts/phase3/<menu>.jsonl`
- logcat: `OPENCLAW_RPC: TX <method>` and `OPENCLAW_RPC: RX <method>`

✅ Completion: `PHASE_3_COMPLETE`

---

## Phase 4 — Node Mode: Android as Peripheral (Camsnap)
**Goal:** Android connects as a node and exposes capabilities callable by controller/gateway.

**Implementation Steps:**
1. Separate node WS role/session from operator WS.
2. Advertise node capabilities.
3. Implement `camsnap` capability:
   - permission checks
   - CameraX capture
   - encode (JPEG) and return bytes/ref
4. Ensure UI button triggers real pipeline (no fakes).

**Receipts:**
- Node appears in node list
- Capability invoke transcript saved
- logcat: permission → capture → send → controller receipt

✅ Completion: `PHASE_4_COMPLETE`

---

## Phase 5 — Security Hardening
**Goal:** Prevent exposing an admin surface unsafely.

**Requirements:**
- Token-based auth supported
- Remote access guidance documented (VPN/Tailscale recommended)
- In-app warnings for risky configs

**Receipts:**
- Security audit notes saved: `receipts/phase5/security_audit.txt`
- Screenshot: settings show security mode and warnings

✅ Completion: `PHASE_5_COMPLETE`

---

## Phase 6 — Reliability: Background Limits, Reconnect, Truthful State
**Goal:** Survive real Android conditions.

**Requirements:**
- ForegroundService owns networking
- Exponential backoff + jitter reconnect
- State is truthful; UI reflects service state only

**Receipts:**
- Airplane mode test logs + screenshot
- Flaky network reconnect logs

✅ Completion: `PHASE_6_COMPLETE`

---

## Phase 7 — Build + Release: Repeatable Smoke Proof
**Goal:** One-command build and 5-minute smoke test.

**Deliverables:**
- `SMOKE_TEST.md`
- CI build debug APK + run tests

**Receipts:**
- CI artifact
- Completed smoke test screenshots/logs

✅ Completion: `PHASE_7_COMPLETE`

---

## Phase 8 — AI Workflow (Codex/Gemini/Copilot) Rules
- **Codex**: implementation + tests + receipts (primary)
- **Gemini**: edge cases + threat model only
- **Copilot**: boilerplate/refactor only; never protocol logic

Rule: “Quote-before-code” for any new RPC name.
