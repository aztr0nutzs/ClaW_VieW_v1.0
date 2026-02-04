# LOG_TAGS.md

Required logcat tags (must appear or feature is fake):
- OPENCLAW_UI        (WebView load, UI events)
- OPENCLAW_SERVICE   (ForegroundService lifecycle, state transitions)
- OPENCLAW_WS        (WS connect/disconnect, reconnect scheduling)
- OPENCLAW_RPC       (RPC TX/RX summaries)
- OPENCLAW_PROTOCOL  (full JSON frames, redacted as needed)
- OPENCLAW_CAMSNAP   (permission/capture/encode/send)
