# ClaW_VieW_v1.0
OpenClaw Android Companion - Real Android Node Implementation

## Overview

This is a **production-ready Android node application**, not a demo. It implements real functionality with comprehensive error handling and logging.

## Key Features

### 1. ✅ ForegroundService Implementation
- **Real** foreground service that runs continuously
- Persistent notification to keep service alive
- Wake lock management to prevent system sleep
- Proper lifecycle management
- Service can be started/stopped from UI

**Location**: `app/src/main/java/com/openclaw/clawview/service/NodeForegroundService.kt`

### 2. ✅ WebSocket Gateway with Heartbeat
- Real WebSocket connection using OkHttp
- Automatic heartbeat every 30 seconds
- Automatic reconnection on failure
- Message queuing and delivery
- **NEVER** invents server endpoints - requires explicit URL
- **NEVER** simulates success - reports all failures

**Location**: `app/src/main/java/com/openclaw/clawview/network/WebSocketGateway.kt`

### 3. ✅ Permission-Aware CameraX Usage
- Real camera preview using CameraX
- Explicit permission checking before any camera operation
- **REFUSES** to operate without CAMERA permission
- Comprehensive error handling
- Image capture capability

**Location**: `app/src/main/java/com/openclaw/clawview/camera/CameraManager.kt`

### 4. ✅ Persistent Storage for Node Identity
- Uses Android DataStore for persistent storage
- Generates unique node ID using UUID
- Stores server URL configuration
- Reactive updates using Kotlin Flow
- Data survives app restarts

**Location**: `app/src/main/java/com/openclaw/clawview/storage/NodeIdentityStorage.kt`

## Design Principles

This implementation follows strict production principles:

- ✅ **Prefer failure over silence** - All errors are logged and reported
- ✅ **Logs before UI** - Every operation logs first, then updates UI
- ✅ **Never invent server endpoints** - Requires explicit server URL
- ✅ **Never simulate success** - All operations are real, failures are reported

## Architecture

```
MainActivity
├── NodeIdentityStorage (Persistent node ID & config)
├── CameraManager (Permission-aware camera access)
└── Controls NodeForegroundService
    ├── WebSocketGateway (Real-time communication)
    └── CameraManager (Optional camera access)
```

## Required Permissions

The app declares and requests these permissions:

- `CAMERA` - For camera preview and capture
- `INTERNET` - For WebSocket communication
- `FOREGROUND_SERVICE` - For running as foreground service
- `FOREGROUND_SERVICE_CAMERA` - Camera access in foreground service
- `POST_NOTIFICATIONS` - For service notification (Android 13+)
- `WAKE_LOCK` - To keep service alive

## Usage

1. **Launch the app** - Grants necessary permissions when requested
2. **Enter WebSocket server URL** - Must start with `ws://` or `wss://`
3. **Start Node Service** - Runs in foreground with persistent notification
4. **Monitor logs** - Real-time log output shown at bottom of screen

## WebSocket Protocol

The node communicates with the server using JSON messages:

### Registration Message
```json
{
  "type": "register",
  "nodeId": "node_<uuid>",
  "timestamp": 1234567890
}
```

### Heartbeat Message
```json
{
  "type": "heartbeat",
  "nodeId": "node_<uuid>",
  "timestamp": 1234567890
}
```

### Server Acknowledgment
```json
{
  "type": "heartbeat_ack"
}
```

## Logging

All components implement comprehensive logging:

- **MainActivity**: User interactions, permission results, lifecycle events
- **NodeForegroundService**: Service lifecycle, connection status
- **WebSocketGateway**: Connection events, messages, errors
- **CameraManager**: Camera initialization, errors, permission checks
- **NodeIdentityStorage**: Storage operations, node ID generation

Logs appear in:
1. **Logcat** (for development)
2. **UI Log View** (real-time in-app display)

## Building

This project requires:
- Android Studio Arctic Fox or later
- Android SDK 34
- Minimum Android 7.0 (API 24)
- Kotlin 1.9.20

Open the project in Android Studio and build normally. The Gradle wrapper is included.

## Testing

To test the node:

1. Set up a WebSocket server that accepts connections
2. Configure the server URL in the app
3. Start the node service
4. Monitor logs for connection status
5. Send messages from server to verify reception

## Security Considerations

- All permissions are requested at runtime
- No hardcoded credentials or endpoints
- WebSocket supports both `ws://` and `wss://` (secure)
- Wake lock released when service stops
- Proper cleanup of all resources

## Failure Modes

The application handles these failure scenarios:

1. **No camera permission** - Shows error, refuses to start camera
2. **Invalid server URL** - Shows error, refuses to connect
3. **WebSocket connection failure** - Logs error, attempts reconnection
4. **Service crash** - Android restarts service automatically (START_STICKY)
5. **Network loss** - Heartbeat detects failure, attempts reconnection

## Code Quality

- ✅ Null safety throughout
- ✅ Exception handling on all I/O operations
- ✅ Proper resource cleanup (cameras, sockets, wake locks)
- ✅ No simulated operations
- ✅ Comprehensive documentation
- ✅ Production-ready error handling

## License

See LICENSE file for details.
 
