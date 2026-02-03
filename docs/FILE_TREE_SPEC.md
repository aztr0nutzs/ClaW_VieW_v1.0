# File Tree Spec — OpenClaw Android Companion (V4)

This is the expected repo structure for the Android app module.

```
app/
  src/main/
    AndroidManifest.xml
    assets/
      openclaw_dash.html
    java/.../
      MainActivity.kt
      OpenClawForegroundService.kt
      UiEventController.kt
      ui/UiState.kt
      gateway/OpenClawGateway.kt
      gateway/Protocol.kt
      capabilities/CapabilityRegistry.kt
      capabilities/DeviceInfoCapability.kt
      capabilities/NetworkStatusCapability.kt
      capabilities/CamsnapCapability.kt
      camera/CameraXController.kt
      storage/NodeStore.kt
      logging/Log.kt
```

Notes:
- You may add more files, but these entrypoints must exist.
- Activity must NOT own gateway/camera logic.
