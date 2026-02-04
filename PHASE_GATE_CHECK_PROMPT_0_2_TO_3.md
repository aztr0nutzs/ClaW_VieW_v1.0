# Codex Prompt — Verify Phases 0–2 via In-App Receipts, Then Start Phase 3

You are Codex. Verify Phases 0–2 using IN-APP exported receipts files (phone-only workflow).

## Phase 0 verification
Must find and read:
- `OpenClaw/receipts/phase0_logs.txt`
Must contain:
- OPENCLAW_UI: GUI_LOADED openclaw_dash.html
- OPENCLAW_SERVICE: SERVICE_START
- OPENCLAW_SERVICE: START_FOREGROUND

## Phase 1 verification
Must find and read:
- `OpenClaw/receipts/phase1_node.json`
- `OpenClaw/receipts/phase1_logs.txt`
Node ID must be stable across 2 launches.

## Phase 2 verification
Must find and read:
- `OpenClaw/receipts/phase2/handshake.jsonl`
Must contain at least:
- TX ws_connect_attempt with url
- RX ws_close or hello_ok/register_ack

If any proof is missing: output FAIL and STOP.

If all verified: proceed to Phase 3 and implement ONE menu item end-to-end (recommended: Logs tail or Chat history) including:
- UI trigger → bridge → WS RPC → response render
- transcript saved to `OpenClaw/receipts/phase3/<menu>.jsonl`
- logcat tags OPENCLAW_RPC and OPENCLAW_PROTOCOL used

Proceed.
