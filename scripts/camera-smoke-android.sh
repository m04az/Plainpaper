#!/usr/bin/env bash
# Runs the CameraX smoke test (core/capture androidDeviceTest) on a connected device or on an
# emulator with a virtual-scene back camera (specs/003-capture-import research R13).
#
#   scripts/camera-smoke-android.sh            # first connected device / running emulator
#   scripts/camera-smoke-android.sh --create   # creates and starts the AVD "plainpaper-camera" first
set -euo pipefail
cd "$(dirname "$0")/.."

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
AVD_NAME="plainpaper-camera"
IMAGE="system-images;android-35;google_apis;arm64-v8a"
PACKAGE="app.plainpaper.core.capture.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"

if [[ "${1:-}" == "--create" ]]; then
  if ! "$SDK/cmdline-tools/latest/bin/avdmanager" list avd | grep -q "Name: $AVD_NAME"; then
    "$SDK/cmdline-tools/latest/bin/sdkmanager" --install "$IMAGE" >/dev/null
    echo no | "$SDK/cmdline-tools/latest/bin/avdmanager" create avd -n "$AVD_NAME" -k "$IMAGE" -d pixel_6 >/dev/null
    echo "hw.camera.back=virtualscene" >> "$HOME/.android/avd/$AVD_NAME.avd/config.ini"
  fi
  "$SDK/emulator/emulator" -avd "$AVD_NAME" -no-boot-anim -no-snapshot-load >/dev/null 2>&1 &
  adb wait-for-device
  until [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do sleep 3; done
fi

SERIAL="${ANDROID_SERIAL:-$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')}"
[[ -n "$SERIAL" ]] || { echo "no device"; exit 1; }
export ANDROID_SERIAL="$SERIAL"

./gradlew :core:capture:assembleAndroidDeviceTest -q
APK=$(find core/capture/build/outputs -name "*.apk" | head -1 || true)
[[ -n "$APK" ]] || { echo "device-test APK not found under core/capture/build/outputs"; exit 1; }
adb install -r -t "$APK" >/dev/null
adb shell pm grant "$PACKAGE" android.permission.CAMERA 2>/dev/null || true
adb shell am instrument -w -r -e class app.plainpaper.core.capture.CameraSmokeTest "$PACKAGE/$RUNNER" | tee /tmp/camera-smoke.txt | grep -E "INSTRUMENTATION_STATUS: (test|stack)|OK \(|FAILURES|Error" || true
grep -q "INSTRUMENTATION_CODE: -1" /tmp/camera-smoke.txt && ! grep -q "FAILURES\|INSTRUMENTATION_STATUS: stack" /tmp/camera-smoke.txt
