#!/usr/bin/env bash
set -euo pipefail

required_env_var() {
  local var_name="$1"
  if [[ -z "${!var_name:-}" ]]; then
    echo "Missing required environment variable: ${var_name}" >&2
    exit 1
  fi
}

required_env_var "PACKAGE_NAME"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is not installed or not in PATH." >&2
  exit 1
fi

online_devices=($(adb devices | awk 'NR>1 && $2=="device" {print $1}'))

if [[ ${#online_devices[@]} -ne 1 ]]; then
  echo "Error: This script requires exactly one connected and authorized device, but found ${#online_devices[@]}." >&2
  echo "Please connect one device and enable USB debugging." >&2
  exit 1
fi

device_id="${online_devices[0]}"

timestamp="$(date +%Y%m%d_%H%M%S)"
output_dir="phase0_artifacts_${timestamp}"
mkdir -p "${output_dir}"

if [[ ! -d "${output_dir}" ]]; then
  echo "Failed to create output directory: ${output_dir}" >&2
  exit 1
fi

if [[ ! -w "${output_dir}" ]]; then
  echo "Output directory is not writable: ${output_dir}" >&2
  exit 1
fi
device_model="$(adb shell getprop ro.product.model | tr -d '\r')"
android_version="$(adb shell getprop ro.build.version.release | tr -d '\r')"
{
  echo "device_id=${device_id}"
  echo "device_model=${device_model}"
  echo "android_version=${android_version}"
} > "${output_dir}/device_info.txt"

adb logcat -c

logcat_file="${output_dir}/logcat.txt"
adb logcat -v time > "${logcat_file}" &
logcat_pid=$!

cleanup() {
  if kill -0 "${logcat_pid}" >/dev/null 2>&1; then
    kill "${logcat_pid}" >/dev/null 2>&1 || :
    wait "${logcat_pid}" 2>/dev/null || :
  fi
}
trap cleanup EXIT

if [[ -n "${MAIN_ACTIVITY:-}" ]]; then
  adb shell am start -n "${PACKAGE_NAME}/${MAIN_ACTIVITY}" >/dev/null
else
  resolved_activity="$(adb shell cmd package resolve-activity --brief "${PACKAGE_NAME}" | tail -n 1 | tr -d '\r')"
  if [[ -z "${resolved_activity}" ]]; then
    echo "Unable to resolve main activity for package: ${PACKAGE_NAME}" >&2
    exit 1
  fi
  adb shell am start -n "${resolved_activity}" >/dev/null
fi

echo "App launch requested."
echo "IMPORTANT: For Phase 0, the foreground notification must appear within 5 seconds of app launch."
echo "Watch the device now. If the notification appears within 5 seconds, press Enter to capture the notification screenshot."
echo "If it does NOT appear within 5 seconds, press Ctrl+C to abort and treat this as a Phase 0 failure."
read -r

screencap_file="${output_dir}/notification.png"
if ! adb exec-out screencap -p > "${screencap_file}"; then
  echo "Failed to capture screenshot via adb. Ensure the device is connected, unlocked, and USB debugging is enabled." >&2
  exit 1
fi

if [[ ! -s "${screencap_file}" ]]; then
  echo "Screenshot file is missing or empty: ${screencap_file}. Phase 0 artifacts are incomplete." >&2
  exit 1
fi
# Ensure logcat capture is stopped and the log file is flushed before reporting success
if kill -0 "${logcat_pid}" >/dev/null 2>&1; then
  kill "${logcat_pid}" >/dev/null 2>&1 || true
  wait "${logcat_pid}" 2>/dev/null || true
fi

echo
echo "Phase 0 logcat captured at: ${logcat_file}"
echo "To verify Phase 0 completion, review the logcat for at least the following:"
echo "  1) Service creation for the foreground service."
echo "  2) A call to startForeground() occurring within 5 seconds of app launch."
echo "  3) No ANR (Application Not Responding) entries during the capture window."
echo
echo "Example commands you can run locally to help review the logcat:"
echo "  # Service creation (adjust patterns to match your app's logs):"
echo "  grep -i 'Service' \"${logcat_file}\" | head"
echo
echo "  # startForeground() calls (if your app logs them):"
echo "  grep -i 'startForeground' \"${logcat_file}\""
echo
echo "  # Check for ANR entries:"
echo "  grep -i 'ANR' \"${logcat_file}\" || echo 'No ANR lines found.'"
echo
echo "Phase 0 artifacts captured in ${output_dir}."
