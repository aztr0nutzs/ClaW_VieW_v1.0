![Claw View Dashboard Header](/claw_control.png)
# Claw View v1.0 — OpenClaw Android Companion 
<p align="center">
  <a href="https://openclaw.ai/"><img src="https://img.shields.io/badge/Website-openclaw.ai-0ea5e9?style=for-the-badge" alt="OpenClaw Website"></a>
  <a href="https://docs.openclaw.ai/"><img src="https://img.shields.io/badge/Docs-docs.openclaw.ai-22c55e?style=for-the-badge" alt="OpenClaw Docs"></a>
  <a href="https://github.com/openclaw/openclaw"><img src="https://img.shields.io/badge/GitHub-openclaw%2Fopenclaw-111827?style=for-the-badge&logo=github" alt="OpenClaw GitHub"></a>
  <a href="https://discord.gg/clawd"><img src="https://img.shields.io/badge/Discord-Join-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="OpenClaw Discord"></a>
  <a href="https://github.com/openclaw/openclaw/releases"><img src="https://img.shields.io/github/v/release/openclaw/openclaw?style=for-the-badge" alt="OpenClaw Releases"></a>
</p>

This repository is an **Android companion** for **OpenClaw**. It is designed to act as a **device node**: it registers with an OpenClaw Gateway, advertises device capabilities, executes them, and returns results. The Android app’s UI is fully driven by **openclaw_dash.html**, and all actions flow from the UI to the foreground service and back with real logs and responses (no mocks).

> **Important:** The dashboard must call the required JS functions — `connectGateway`, `disconnectGateway`, `triggerCamsnap`, and `requestStatus` — which are mapped to backend bridge methods and gateway RPCs. This is the minimum functional surface required by the repo contract.【F:docs/PROJECT_RULES.md†L24-L41】【F:RPC_MAP.md†L1-L10】

## Table of Contents

- [What this repo provides](#what-this-repo-provides)
- [OpenClaw ecosystem at a glance](#openclaw-ecosystem-at-a-glance)
- [Feature + capability inventory](#feature--capability-inventory)
- [Step-by-step user guide](#step-by-step-user-guide)
- [Dashboard reference](#dashboard-reference)
- [Troubleshooting](#troubleshooting)
- [Safety + operational notes](#safety--operational-notes)

## What this repo provides

- **Android device node** for OpenClaw with a strict architecture: foreground service owns execution, activity hosts only the GUI, and the dashboard must be the first page shown on launch.【F:docs/PROJECT_RULES.md†L1-L33】
- **Truthful UI**: it displays only real service state (no simulated success), and all actions must flow UI → handler → service → log trace → response.【F:docs/PROJECT_RULES.md†L12-L36】
- **Required wiring** for connect, disconnect, status, and camsnap (camera capture) with a defined response envelope `{ ok, code, message, data }`.【F:docs/PROJECT_RULES.md†L34-L52】【F:RPC_MAP.md†L1-L10】

## OpenClaw ecosystem at a glance

OpenClaw is a personal AI assistant that runs locally and connects to real messaging channels, device nodes, tools, and automation via a Gateway control plane. This Android companion is one of those nodes — used to provide device-local capabilities (camera, screen capture, etc.) to the OpenClaw system.

**Primary OpenClaw surfaces and capabilities (from upstream OpenClaw docs/repo):**

- **Local-first Gateway** (control plane + WebSocket for clients and tools).
- **Multi-channel inbox** (WhatsApp, Telegram, Slack, Discord, Google Chat, Signal, iMessage via BlueBubbles, Microsoft Teams, Matrix, Zalo, WebChat, and more).
- **Live Canvas** for agent-driven visual workspaces.
- **Voice Wake + Talk Mode** on macOS/iOS/Android.
- **First-class tools** (browser automation, canvas, nodes, cron, sessions, etc.).
- **Companion apps** (macOS menu bar app + iOS/Android nodes).
- **Skills platform** for extensibility.

For full OpenClaw docs, onboarding, and channel-specific setup, see:
- Website: https://openclaw.ai/
- Docs: https://docs.openclaw.ai/
- GitHub: https://github.com/openclaw/openclaw

![Claw View Dashboard Detail](assets/claw_view_dash3.png)

Additional dashboard imagery: `assets/claw_view_dash3.png`, `assets/claw_view_dash5.png`.

## Feature + capability inventory

This section enumerates **every function, capability, and operational contract** implemented or required by this repo.

### Core architecture (non-negotiable)

- **ForegroundService owns execution** (gateway, capabilities, and state).【F:docs/PROJECT_RULES.md†L7-L18】
- **MainActivity hosts GUI only** (no networking, camera, or protocol logic).【F:docs/PROJECT_RULES.md†L7-L18】
- **openclaw_dash.html is the only GUI** and must be first shown on launch.【F:docs/PROJECT_RULES.md†L12-L33】
- **UI must be truthful** and **no mock data** is allowed.【F:docs/PROJECT_RULES.md†L12-L36】
- **Every action must be fully wired**: UI trigger → handler → service method → log trace → response.【F:docs/PROJECT_RULES.md†L12-L36】
- **Missing wiring must hard-fail** (disabled UI + error banner + logs).【F:docs/PROJECT_RULES.md†L12-L36】

### Required UI → backend functions (minimum working surface)

| UI Action | JS Function | Bridge Method | Gateway RPC | Expected Response | Log Trace |
|---|---|---|---|---|---|
| Connect | `connectGateway()` | `OpenClawBridge.connectGateway` | `(connect/hello)` | `{ok,code,message,data{state}}` | `UI_EVENT → TX → RX` |
| Disconnect | `disconnectGateway()` | `OpenClawBridge.disconnectGateway` | `(disconnect)` | `{ok,code,message,data{state}}` | `UI_EVENT → TX → RX` |
| Status | `requestStatus()` | `OpenClawBridge.requestStatus` | `status` | `{ok,code,message,data{state}}` | `UI_EVENT → STATE` |
| Camsnap | `triggerCamsnap()` | `OpenClawBridge.triggerCamsnap` | `capability invoke` | `{ok,code,message,data{image}}` | `UI_EVENT → CAPTURE → TX` |

(Above mapping is defined in the repo’s RPC map and is mandatory.)【F:RPC_MAP.md†L1-L10】

### OpenClaw Android Companion phased requirements

These phases define the **end-to-end capability progression** for the Android companion (UI, gateway connection, control surface, node capabilities, and security hardening):

1. **Phase 0 — Spec freeze**: menu items + behavior matrix and RPC map locked in.【F:OPENCLAW_ANDROID_COMPANION_BLUEPRINT.md†L1-L31】
2. **Phase 1 — Android shell**: WebView loads `openclaw_dash.html` and exposes the JS bridge.【F:OPENCLAW_ANDROID_COMPANION_BLUEPRINT.md†L33-L52】
3. **Phase 2 — Gateway connection**: handshake, pairing flow, token persistence, and UI for pairing/connection state.【F:OPENCLAW_ANDROID_COMPANION_BLUEPRINT.md†L46-L64】
4. **Phase 3 — Operator surface**: side menu triggers real RPCs with visible results and logs.【F:OPENCLAW_ANDROID_COMPANION_BLUEPRINT.md†L84-L114】
5. **Phase 4 — Node mode**: Android advertises node capabilities; `camsnap` is implemented end-to-end.【F:OPENCLAW_ANDROID_COMPANION_BLUEPRINT.md†L116-L139】
6. **Phase 5 — Security hardening**: token-based auth + safe remote access guidance.【F:OPENCLAW_ANDROID_COMPANION_BLUEPRINT.md†L141-L150】

### Required operator menu surfaces

The dashboard’s operator surface must include at least the following menu categories and corresponding RPC flows (from the blueprint):

- **Chat**: history/send/abort/inject
- **Channels**: status/login/config patch flows
- **Sessions**: list/patch
- **Cron**: `cron.*`
- **Skills**: `skills.*`
- **Nodes**: `node.list`
- **Exec Approvals**: `exec.approvals.*`
- **Config**: `config.*`
- **Debug**: status/health/models
- **Logs**: `logs.tail`
- **Update**: `update.run`

These are required so the Android companion mirrors the OpenClaw Control UI and provides real operational control via gateway RPCs.【F:OPENCLAW_ANDROID_COMPANION_BLUEPRINT.md†L84-L114】

## Step-by-step user guide

This guide walks a new user through installing OpenClaw, running the gateway, and using this Android companion. It is written to be friendly and safe for first-time users.

### 1) Install OpenClaw (Gateway + CLI)

OpenClaw is the control plane that the Android companion connects to. Install the OpenClaw CLI from npm, then run the onboarding wizard.

```bash
npm install -g openclaw@latest
openclaw onboard --install-daemon
```

This installs the Gateway daemon so it stays running in the background.

### 2) Start the OpenClaw Gateway (local)

```bash
openclaw gateway --port 18789 --verbose
```

You should now have a local Gateway listening on `ws://127.0.0.1:18789` (and a web dashboard if enabled).

### 3) Build and run the Android companion

**Assumption (safe default):** The Android app can be built with standard Android Studio/Gradle workflows.

1. Open the `/android` folder in Android Studio.
2. Allow Gradle sync to complete.
3. Select a device or emulator.
4. Run the app.

On launch, the app should open **openclaw_dash.html** immediately, and the bridge should be available to the dashboard.【F:docs/PROJECT_RULES.md†L12-L33】【F:OPENCLAW_ANDROID_COMPANION_BLUEPRINT.md†L33-L52】

### 4) Connect to the Gateway from the dashboard

Use the dashboard’s **Connect** action. It must call `connectGateway()` and route through the bridge to the service and gateway, returning the standard response envelope with current state.【F:docs/PROJECT_RULES.md†L24-L52】【F:RPC_MAP.md†L1-L10】

### 5) Pair the device (if required)

If the Gateway requests pairing, the UI must show a blocking pairing state and you must approve the device from the OpenClaw side. (OpenClaw typically uses a pairing code flow.)【F:OPENCLAW_ANDROID_COMPANION_BLUEPRINT.md†L54-L82】

### 6) Use core actions (Status + Camsnap)

- **Status**: calls `requestStatus()` and returns `{ ok, code, message, data{state} }` for UI rendering.【F:RPC_MAP.md†L1-L10】
- **Camsnap**: calls `triggerCamsnap()` and returns the image payload once the capability executes successfully.【F:RPC_MAP.md†L1-L10】【F:OPENCLAW_ANDROID_COMPANION_BLUEPRINT.md†L116-L139】

### 7) Explore operator menu features

The menu must expose all required operational RPCs (chat, channels, sessions, cron, skills, nodes, exec approvals, config, debug, logs, update) and show real results with logs and receipts.【F:OPENCLAW_ANDROID_COMPANION_BLUEPRINT.md†L84-L114】

## Dashboard reference

Use this section as a quick look-up for the minimum required dashboard behaviors.

- **Dashboard file location**: `android/app/src/main/assets/openclaw_dash.html`. It must be the first page shown at launch and is the only UI allowed.【F:docs/PROJECT_RULES.md†L12-L33】
- **Required JS functions**: `connectGateway`, `disconnectGateway`, `triggerCamsnap`, `requestStatus` — all must be wired to the ForegroundService and log traces must appear.【F:docs/PROJECT_RULES.md†L24-L41】
- **Response envelope**: all responses must be `{ ok, code, message, data }` with accurate state (no mock data).【F:docs/PROJECT_RULES.md†L34-L52】

## Troubleshooting

### Issue: The dashboard does not load or shows a blank screen

**What it means:** The WebView did not load `openclaw_dash.html`, or the UI is not the first page displayed.

**Fix:** Confirm the app launches directly to `android/app/src/main/assets/openclaw_dash.html` and no alternate dashboard is used. The app must show only this dashboard on startup.【F:docs/PROJECT_RULES.md†L12-L33】

### Issue: Buttons do nothing (no response, no logs)

**What it means:** The required JS → bridge → service wiring is missing.

**Fix:** Validate each required JS function (`connectGateway`, `disconnectGateway`, `triggerCamsnap`, `requestStatus`) and the RPC mapping table. Every UI action must have a handler, log trace, and response envelope.【F:docs/PROJECT_RULES.md†L24-L36】【F:RPC_MAP.md†L1-L10】

### Issue: UI shows success but nothing happens

**What it means:** The UI is faking success (not allowed).

**Fix:** Remove mock responses. All UI must reflect actual service state, with real logs, and any missing wiring must hard-fail with a visible error banner and logs.【F:docs/PROJECT_RULES.md†L12-L36】

### Issue: Pairing required but the UI doesn’t block

**What it means:** Pairing flow isn’t surfaced properly.

**Fix:** Implement the explicit blocking UI state for pairing required, and show the device id/approval instructions as defined in the handshake phase. Pairing must be visible and must stop the operator surface until approved.【F:OPENCLAW_ANDROID_COMPANION_BLUEPRINT.md†L54-L82】

### Issue: Camsnap fails or returns empty data

**What it means:** Node capability isn’t implemented or the capture pipeline failed.

**Fix:** Ensure the Android node advertises its capabilities and the `camsnap` path checks permissions, captures an image, encodes it, and returns a real payload. The UI should log and surface any failure codes.【F:OPENCLAW_ANDROID_COMPANION_BLUEPRINT.md†L116-L139】

## Safety + operational notes

- **No insecure workarounds** unless explicitly enabled and documented. (Security hardening is a required phase.)【F:OPENCLAW_ANDROID_COMPANION_BLUEPRINT.md†L141-L150】
- **No swallowing exceptions**; missing logs mean the feature doesn’t exist.【F:docs/PROJECT_RULES.md†L48-L54】
- **All actions must be real** and produce verifiable log traces; otherwise they must fail visibly.【F:docs/PROJECT_RULES.md†L12-L36】
