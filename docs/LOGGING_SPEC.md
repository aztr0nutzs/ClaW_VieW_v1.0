# Logging Spec — Required Lines (V4)

Logging is the proof system. If logs are missing, the feature does not exist.

## Required tags
- OPENCLAW_SERVICE
- OPENCLAW_GATEWAY
- OPENCLAW_PROTOCOL
- OPENCLAW_UI
- OPENCLAW_CAMSNAP

## Required log lines (minimum)
### Service
- OPENCLAW_SERVICE: SERVICE_START intent=<...>
- OPENCLAW_SERVICE: START_FOREGROUND notificationId=<id>
- OPENCLAW_SERVICE: STATE connected=<t/f> registered=<t/f> lastError=<...>

### Gateway
- OPENCLAW_GATEWAY: WS_CONNECT url=<...>
- OPENCLAW_GATEWAY: WS_CONNECTED
- OPENCLAW_PROTOCOL: TX register <json>
- OPENCLAW_PROTOCOL: RX register_ack <json>
- OPENCLAW_GATEWAY: HEARTBEAT_SENT
- OPENCLAW_GATEWAY: WS_DISCONNECTED reason=<...>
- OPENCLAW_GATEWAY: RECONNECT scheduledInMs=<...>

### UI wiring
- OPENCLAW_UI: GUI_LOADED openclaw_dash.html
- OPENCLAW_UI: UI_EVENT connectGateway
- OPENCLAW_UI: UI_EVENT triggerCamsnap args=<...>

### Camsnap
- OPENCLAW_CAMSNAP: PERMISSION camera=<granted/denied>
- OPENCLAW_CAMSNAP: CAPTURE_START
- OPENCLAW_CAMSNAP: CAPTURE_OK bytes=<n> mime=image/jpeg
- OPENCLAW_CAMSNAP: SEND_OK controllerAck=<...>
- OPENCLAW_CAMSNAP: CAPTURE_FAIL code=<...> message=<...>
