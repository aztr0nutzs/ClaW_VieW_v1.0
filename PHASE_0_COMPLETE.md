# Phase 0 Completion Record

Status: NOT COMPLETE  <!-- Change to COMPLETE when all fields below are filled in -->

## Device Details
- Model: REPLACE_WITH_DEVICE_MODEL (e.g., Pixel 7)
- Android Version: REPLACE_WITH_ANDROID_VERSION (e.g., 14)
- Device Identifier (optional): REPLACE_WITH_SERIAL_OR_ID (e.g., output of `adb devices`)

## Capture Session
- Capture script: `scripts/phase0_capture.sh`
- Capture timestamp (from script output): REPLACE_WITH_TIMESTAMP (e.g., 2025-02-03T12-34-56Z)
- Artifact directory (from script output): `REPLACE_WITH_ARTIFACT_DIRECTORY_PATH`
  <!-- Example: `artifacts/phase0/2025-02-03_12-34-56` -->

## Receipts
- `adb logcat`:
  - Captured: YES/NO
  - File path (inside artifact directory): `REPLACE_WITH_LOGCAT_FILE_PATH`
    <!-- Example: `artifacts/phase0/2025-02-03_12-34-56/logcat.txt` -->
- Foreground notification screenshot:
  - Captured: YES/NO
  - File path (inside artifact directory): `REPLACE_WITH_SCREENSHOT_PATH`
    <!-- Example: `artifacts/phase0/2025-02-03_12-34-56/notification.png` -->

## How to complete this record
1. Connect a **physical Android device** with USB debugging enabled.
2. Run the Phase 0 capture script from the project root:
   ```bash
   scripts/phase0_capture.sh
