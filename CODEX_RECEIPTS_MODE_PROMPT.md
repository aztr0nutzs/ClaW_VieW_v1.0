# Codex Prompt — Implement “Receipts Mode” to Unblock Phases 0–2 (Phone-Only Workflow)

You are Codex. Stop blocking on missing adb logcat/screenshots. We are operating phone-only, so we must generate receipts INSIDE the app.

Implement an in-app receipts pipeline that satisfies Phase 0–2 proof requirements without external tools.

## Required deliverables

### 1) ReceiptLogger
- Ring buffer in memory (e.g., last 5000 lines)
- `append(tag, message)` API
- Each append also writes to Android Log.* (so devs still see logs)
- On export, persist logs to: `OpenClaw/receipts/phaseX_logs.txt`

### 2) TranscriptWriter
- Write JSONL transcripts to: `OpenClaw/receipts/phase2/handshake.jsonl`
- Format per line:
  ```json
  {"ts":"ISO8601","dir":"TX|RX","channel":"operator|node","type":"<event>","payload":{...},"note":"optional"}
  ```
- Include connect attempts, close codes/reasons, and any ACKs.

### 3) Receipts Export Surface (Bridge)
Implement:
- `OpenClawBridge.exportReceipts(jsonArgs)` where jsonArgs includes `{ "phase": 0|1|2 }`
  - Returns envelope:
    `{ "ok": true|false, "code": "STRING", "message": "STRING", "data": { "exportDir": "...", "files": ["..."] } }`
- `OpenClawBridge.getReceiptsStatus(jsonArgs)`
  - Returns last export timestamps, nodeId, connection status, and last error.

### 4) Dashboard UI (openclaw_dash.html)
Add a “Receipts” side-menu item/screen with buttons:
- Export Phase 0 receipts
- Export Phase 1 receipts
- Export Phase 2 receipts
- Copy last logs (via clipboard API if available)
- Show export path and last export time

UI must call the bridge methods above and render results.

## Updated Acceptance Criteria (Phase 0–2, phone-only)

### Phase 0
Exported file exists and contains:
- `OPENCLAW_UI: GUI_LOADED openclaw_dash.html`
- `OPENCLAW_SERVICE: SERVICE_START`
- `OPENCLAW_SERVICE: START_FOREGROUND`

### Phase 1
Exported `phase1_node.json` contains nodeId AND UI shows nodeId.
Logs prove nodeId is stable across two launches.

### Phase 2
`receipts/phase2/handshake.jsonl` exists.
It must include at least:
- TX connect attempt with url
- RX close/error with code+reason if it fails
Only mark success if a documented ACK is received.

## Strict rules
- Do NOT mark handshake success unless ACK is explicitly received and logged/transcribed.
- If handshake fails, export the failure transcript and show a blocking UI error with code/reason.
- Commit changes and list all files created/modified.

Proceed now.
