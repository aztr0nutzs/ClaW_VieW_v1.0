# Copilot / Codex Repo Instructions — OpenClaw Android Companion (V4)

You are building a REAL Android node.

## You MUST generate code that:
- Uses a ForegroundService for all execution (gateway + capabilities).
- Provides explicit, structured logging for every important step.
- Implements strict JSON envelopes and validates ACKs.
- Implements reconnect with backoff + jitter.
- Handles runtime permission denial as a first-class outcome.

## You MUST NOT:
- Invent controller endpoints. If unknown, implement explicit NOT_SUPPORTED failures.
- Simulate success or return fake data.
- Add UI without wiring to a service method and log trace.
- Hide errors. No silent catch blocks.

## Output standard:
If you cannot prove it with receipts, mark it FAILED.
