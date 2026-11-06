#!/usr/bin/env bash
# Run only the read-only navigation test on a connected Android device.
set -euo pipefail
cd "$(dirname "$0")/.."
kanak_adb=$(command -v adb || true)
kanak_sdk=$(printenv ANDROID_HOME || true)
if [[ -z "$kanak_adb" ]]; then
  for kanak_candidate in "$kanak_sdk/platform-tools/adb" "$HOME/Library/Android/sdk/platform-tools/adb"; do
    if [[ -x "$kanak_candidate" ]]; then kanak_adb=$kanak_candidate; break; fi
  done
fi
if [[ -z "$kanak_adb" ]]; then
  echo "Android platform-tools adb not found. Use Android Studio with the Android SDK installed." >&2
  exit 1
fi
command -v python3 >/dev/null || { echo "Python 3 is required to summarize the report." >&2; exit 1; }
kanak_devices=$("$kanak_adb" devices | awk 'NR>1 && $2=="device" {print $1}')
kanak_serial=$(printenv ANDROID_SERIAL || true)
if [[ -n "$kanak_serial" ]]; then
  "$kanak_adb" -s "$kanak_serial" get-state >/dev/null
else
  kanak_count=$(printf '%s\n' "$kanak_devices" | awk 'NF {n++} END {print n+0}')
  [[ "$kanak_count" == 1 ]] || { echo "Connect and authorize exactly one device, or set ANDROID_SERIAL." >&2; exit 1; }
  export ANDROID_SERIAL=$kanak_devices
fi
kanak_run_id=$(python3 -c 'import uuid; print(uuid.uuid4())')
bash gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kanak.app.performance.InputLatencyTest \
  -Pandroid.testInstrumentationRunnerArguments.kanakLatencyRunId="$kanak_run_id" \
  --no-daemon
mkdir -p app/build/reports
"$kanak_adb" -s "$ANDROID_SERIAL" logcat -d -s KANAK_INPUT_TIMING:I '*:S' > app/build/reports/input-latency.log
python3 scripts/summarize-input-latency.py app/build/reports/input-latency.log "$kanak_run_id"
echo "Raw observations: app/build/reports/input-latency.log"
