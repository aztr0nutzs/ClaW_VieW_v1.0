# Protocol Spec (Minimum) — OpenClaw Android Companion (V4)

If the real OpenClaw protocol differs, you must update this file AND implement explicit compatibility handling.
Do not guess silently.

## 1) Message envelope
All messages must include:
- type: string
- nodeId: string
- ts: epoch ms (recommended)
- v: protocol version (recommended)

## 2) Registration
Client → Controller (TX):
type=register
Fields:
- nodeId (persistent)
- platform=android
- appVersion
- capabilities: string[]
- device: { model, manufacturer, sdkInt }

Controller → Client (RX):
type=register_ack
Fields:
- nodeId
- ok: boolean
- sessionId: string
- controllerVersion?: string
- error?: { code, message }

Rules:
- Without ok=true and sessionId, client is NOT registered.
- Client must store sessionId in memory and include it in heartbeats.

## 3) Heartbeat
Client TX:
type=heartbeat
Fields:
- nodeId
- sessionId

Controller RX:
type=heartbeat_ack
Fields:
- ok
- ts?

Rules:
- If heartbeat_ack not received within timeout, mark disconnected and reconnect.

## 4) Capability execution
Controller → Client:
type=capability_request
Fields:
- requestId
- capability
- args (object)

Client → Controller:
type=capability_result
Fields:
- requestId
- capability
- ok
- result? (object)
- error? { code, message }

Rules:
- Unknown capability must return ok=false with code=CAPABILITY_UNKNOWN.
- Permission denied must return ok=false with code=PERMISSION_DENIED.
