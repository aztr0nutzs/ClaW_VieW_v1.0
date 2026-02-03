
# Project Rules – OpenClaw Android Companion

## Absolute Rules
1. ForegroundService is mandatory for all gateway logic
2. No UI action exists without backend wiring
3. Capabilities must fail loudly
4. Permissions are validated at runtime

## Architecture Rules
- UI observes, never commands directly
- Services own execution
- Protocol layer is versioned and validated

## Networking Rules
- No localhost assumptions
- TLS failures must surface
- Reconnect backoff required

## Device Reality Rules
- OEM kill policies assumed hostile
- Doze assumed active
- Camera can be revoked anytime

Violations invalidate the feature.
