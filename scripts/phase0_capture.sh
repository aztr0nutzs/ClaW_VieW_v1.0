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
    kill "${logcat_pid}"
  fi
}
trap cleanup EXIT

if [[ -n "${MAIN_ACTIVITY:-}" ]]; then
  adb shell am start -n "${PACKAGE_NAME}/${MAIN_ACTIVITY}" >/dev/null
else
  adb shell monkey -p "${PACKAGE_NAME}" -c android.intent.category.LAUNCHER 1 >/dev/null
fi

echo "App launch requested. Verify the foreground notification appears within 5 seconds."
echo "Press Enter to capture the notification screenshot."
read -r

adb exec-out screencap -p > "${output_dir}/notification.png"

echo "Phase 0 artifacts captured in ${output_dir}."
