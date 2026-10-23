#!/usr/bin/env bash
# Device benchmark (spec 002, SC-005/SC-006; research R16).
#   scripts/bench-android.sh [--low-end] [--iterations N] [--photos DIR]
# Pushes up to 10 photos to the test app's external files dir, runs BenchmarkTest on the connected
# device (or the low-end emulator profile) and stores the JSON under reference-set/benchmarks/.
set -euo pipefail
cd "$(dirname "$0")/.."

ITERATIONS=2
PHOTOS=reference-set/photos
LOW_END=0
while [ $# -gt 0 ]; do
  case "$1" in
    --low-end) LOW_END=1 ;;
    --iterations) ITERATIONS="$2"; shift ;;
    --photos) PHOTOS="$2"; shift ;;
    *) echo "unknown option $1"; exit 1 ;;
  esac
  shift
done

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
AVD_NAME=plainpaper-lowend
if [ "$LOW_END" = 1 ]; then
  # 2 GB RAM, 2 cores, API 30 x86_64: the low-end reference profile until a physical phone exists.
  if ! "$SDK/cmdline-tools/latest/bin/avdmanager" list avd | grep -q "Name: $AVD_NAME"; then
    "$SDK/cmdline-tools/latest/bin/sdkmanager" --install "system-images;android-30;google_apis;x86_64" >/dev/null
    echo no | "$SDK/cmdline-tools/latest/bin/avdmanager" create avd -n "$AVD_NAME" -k "system-images;android-30;google_apis;x86_64" -d pixel_3a >/dev/null
    CONFIG="$HOME/.android/avd/$AVD_NAME.avd/config.ini"
    perl -pi -e 's/^hw.ramSize=.*/hw.ramSize=2048/; s/^hw.cpu.ncore=.*/hw.cpu.ncore=2/' "$CONFIG"
    grep -q '^hw.ramSize' "$CONFIG" || echo 'hw.ramSize=2048' >> "$CONFIG"
    grep -q '^hw.cpu.ncore' "$CONFIG" || echo 'hw.cpu.ncore=2' >> "$CONFIG"
  fi
  if ! adb devices | grep -q "emulator-"; then
    "$SDK/emulator/emulator" -avd "$AVD_NAME" -no-snapshot -no-boot-anim >/dev/null 2>&1 &
    adb wait-for-device
    until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
  fi
fi

SERIAL="${ANDROID_SERIAL:-$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')}"
[ -n "$SERIAL" ] || { echo "no device connected"; exit 1; }
export ANDROID_SERIAL="$SERIAL"

# Photos: up to 10 from the reference set, else the synthetic set (bash 3 compatible: no mapfile).
FILES=()
for f in $(ls "$PHOTOS"/*.jpg 2>/dev/null | head -10); do FILES+=("$f"); done
if [ ${#FILES[@]} -eq 0 ]; then
  ./gradlew :tools:scansuite:run --args="--only english-plain-r0" -q >/dev/null
  for f in $(ls build/scansuite/synthetic/photos/*.jpg | head -10); do FILES+=("$f"); done
fi
PKG=app.plainpaper.core.scanengine.test
# Install once through Gradle, push the photos to shared Downloads (files adb pushes into the
# app's own folder are invisible to it under scoped storage), grant media reads, then start the
# instrumentation directly: the Gradle connected-test task would reinstall and wipe state.
./gradlew :core:scanengine:installAndroidDeviceTest -q
SHARED="/sdcard/Download/plainpaper-bench"
adb shell "rm -rf $SHARED /storage/emulated/0/Android/data/$PKG/files/bench && mkdir -p $SHARED"
for f in "${FILES[@]}"; do adb push "$f" "$SHARED/" >/dev/null; done
SDK_INT=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
if [ "$SDK_INT" -ge 33 ]; then adb shell pm grant "$PKG" android.permission.READ_MEDIA_IMAGES; else adb shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE; fi
MODEL=$(adb shell getprop ro.product.model | tr -d '\r' | tr ' ' '-')
echo "Benchmarking ${#FILES[@]} photos x $ITERATIONS on $MODEL ($SERIAL)"
adb shell am instrument -w -e class app.plainpaper.core.scanengine.BenchmarkTest -e iterations "$ITERATIONS" \
  "$PKG/androidx.test.runner.AndroidJUnitRunner" | tail -5
TARGET="/storage/emulated/0/Android/data/$PKG/files"
OUT="reference-set/benchmarks/$MODEL-$(date +%Y%m%d).json"
adb pull "$TARGET/bench.json" "$OUT" >/dev/null && echo "Saved $OUT"
python3 - "$OUT" <<'PY'
import json, statistics, sys
r = json.load(open(sys.argv[1]))
runs = [x for x in r["runs"] if "totalMs" in x]
t = sorted(x["totalMs"] for x in runs)
if t:
    p95 = t[int(len(t) * 0.95) - 1] if len(t) > 1 else t[0]
    print(f"{r['device']} (API {r['androidSdk']}, {r['capability']}): runs={len(runs)} median={statistics.median(t):.0f} ms p95={p95:.0f} ms")
    deltas = [x["nativeHeapDeltaBytes"] for x in runs]
    print(f"native heap delta after 1 s: max {max(deltas)/1e6:.1f} MB")
failed = [x for x in r["runs"] if "totalMs" not in x]
print("failed runs:", len(failed))
PY
