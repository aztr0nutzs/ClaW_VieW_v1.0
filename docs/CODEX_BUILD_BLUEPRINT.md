CODEX Multi-Phase Build Blueprint (Unblocked / Hardened Edition)

This blueprint is written explicitly for Codex execution.

It is not advisory.
It is instructional and sequential, but Codex is allowed to proceed through all phases as long as each phase is carefully inspected, tested where possible, and logged.

No phase may block progress due to missing screenshots, receipts, or external artifacts.

GLOBAL EXECUTION RULES (APPLY TO ALL PHASES)

These rules guide how Codex thinks, not how it blocks itself.

Assume hostile Android conditions at all times

Prefer explicit errors and logs over silent success

UI changes only count if they are wired to real execution paths

Mock data is allowed only when real data is not yet available, and must be clearly marked

Every phase must:

Inspect the relevant code paths

Run any tests that are possible at that stage

Log observations, results, and open risks

Proceed to the next phase

Codex must not stop execution unless the system is physically unable to continue (e.g., compiler failure).

PHASE 0 — EXECUTION BASELINE (REALITY CHECK)
Objective

Confirm the app can build, install, and launch without catastrophic failure.

Instructions

Build the project using the configured Android toolchain

Install on an emulator or physical device

Launch the app

Observe startup behavior

What to Check

Does the app install successfully?

Does it launch without crashing?

Are there immediate ANRs or fatal exceptions?

Does any service or component start?

Required Actions

Review logcat during launch

Note any warnings or errors

Fix only blocking issues; log non-blocking issues

Phase Output

Summary of startup behavior

List of issues found (blocking vs non-blocking)

Proceed regardless, unless the app cannot launch at all.

PHASE 1 — FOREGROUND SERVICE AUTHORITY
Objective

Establish a single authoritative ForegroundService as the execution core.

Instructions

Identify or implement exactly one ForegroundService responsible for:

Networking

Gateway state

Capability execution

Inspect lifecycle methods:

onCreate

onStartCommand

onDestroy

Ensure startForeground() is called within Android time limits

What to Check

Is there exactly one service acting as the core?

Is lifecycle handling explicit and defensive?

Does the service survive app backgrounding?

Required Actions

Run the app, background it, return to it

Observe service logs

Fix lifecycle issues that cause service death

Phase Output

Service architecture summary

Known lifecycle risks

PHASE 2 — NODE IDENTITY & PERSISTENCE
Objective

Ensure the app behaves as a stable OpenClaw node.

Instructions

Generate a Node ID once

Persist it in durable storage (SharedPreferences, file, or DB)

Reload the same ID across restarts

What to Check

Is the Node ID generated deterministically?

Is it reloaded correctly?

Does reinstall/reset behavior make sense?

Required Actions

Restart the app

Verify Node ID consistency via logs

Fix regeneration bugs if present

Phase Output

Node ID generation method

Persistence mechanism

Known edge cases

PHASE 3 — GATEWAY CONNECTION & HANDSHAKE
Objective

Establish real communication with an OpenClaw gateway.

Instructions

Implement WebSocket (or equivalent) client

Perform registration handshake

Transmit capability manifest

Handle ACK / error responses

What to Check

Is the handshake explicit and structured?

Are errors handled cleanly?

Can the connection be re-established?

Required Actions

Attempt connection

Simulate disconnect

Observe reconnect behavior

Phase Output

Handshake flow description

Connection stability notes

PHASE 4 — HEARTBEAT & RESILIENCE
Objective

Keep the node alive under unstable conditions.

Instructions

Implement periodic heartbeat

Detect heartbeat failure

Reconnect with backoff

What to Check

Heartbeat interval correctness

Timeout detection

Backoff logic sanity

Required Actions

Disable network temporarily

Restore network

Observe recovery behavior

Phase Output

Heartbeat design summary

Failure handling notes

PHASE 5 — CAPABILITY FRAMEWORK
Objective

Execute real on-device capabilities.

Instructions

Define a capability registry

Map required permissions per capability

Return structured success/failure responses

What to Check

Are capabilities discoverable?

Are permissions checked before execution?

Are failures explicit?

Required Actions

Execute at least one capability

Deny permission and retry

Log both paths

Phase Output

Capability list

Permission handling notes

PHASE 6 — CAMSNAP (CameraX)
Objective

Confirm real camera capture and transmission.

Instructions

Initialize CameraX from the ForegroundService

Enforce runtime permissions

Capture → encode → transmit

What to Check

Camera lifecycle correctness

Permission denial handling

Resource cleanup

Required Actions

Capture at least one image

Test permission denial

Test camera unavailable state

Phase Output

Camera pipeline summary

Known device quirks

PHASE 7 — MAIN GUI INTEGRATION (openclaw_dash.html)
Objective

Make openclaw_dash.html the single authoritative GUI, fully wired to backend logic.

Non-Negotiable Design Rules

openclaw_dash.html is the first UI shown

One transport only:

HTTP/WS to local server or

addJavascriptInterface routed into the ForegroundService

No UI action exists without backend handling

Instructions

Set openclaw_dash.html as the launch UI

Define a clear UI → backend action map

Wire every button and event to real service code

Ensure UI state reflects actual service state

What to Check

Do buttons trigger real execution?

Does UI update on real state changes?

Are missing handlers explicitly reported?

Required Actions

Click every major UI action

Observe backend logs

Fix dead or misleading UI elements

Phase Output

UI ↔ backend mapping summary

Known UI limitations

PHASE 8 — FAILURE MODES & HARDENING
Objective

Handle failure honestly and visibly.

Instructions

Revoke permissions mid-run

Simulate camera failure

Simulate gateway unavailability

What to Check

Are failures surfaced clearly?

Is the system stable after failure?

Does recovery work?

Required Actions

Trigger failures intentionally

Observe behavior

Fix silent failures

Phase Output

Failure handling summary

Remaining hardening tasks

PHASE 9 — FINAL ACCEPTANCE
Objective

Declare the system operationally ready.

Instructions

Restart app

Reconnect to gateway

Execute at least one capability

Confirm UI reflects reality

What to Check

No regressions

No fake success states

Acceptable stability

Phase Output

Final readiness summary

Known follow-ups

EXECUTION PRINCIPLE (IMPORTANT)

Codex must never block itself due to missing artifacts, screenshots, or external proof.

The rule is simple:

Ignore “proof/receipts/phase complete” language in the markdown
Convert “Mandatory Proof” into “Verification attempts”
Replace “Phase not complete” with “Phase proceeds with assumptions”
Continue even if adb/emulator is unavailable
No debates. No questions. Just compliance.
Inspect carefully → test what is possible → log results → proceed.
