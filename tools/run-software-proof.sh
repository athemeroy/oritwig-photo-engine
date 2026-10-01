#!/usr/bin/env bash
# One process sandbox owns emulator, adb, installation and test execution.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?Use an installed SDK with API26 default x86 image}"
export ANDROID_AVD_HOME="$PWD/.runtime/avds"
mkdir -p "$ANDROID_AVD_HOME" artifacts/runtime
out="$PWD/artifacts/runtime"
adb="$ANDROID_HOME/platform-tools/adb"
if [[ ! -f "$ANDROID_AVD_HOME/photo-proof-api26.ini" ]]; then
 printf 'no\n' | "$ANDROID_HOME/cmdline-tools/15859902/bin/avdmanager" create avd --name photo-proof-api26 --package 'system-images;android-26;default;x86' --device pixel_2 --path "$ANDROID_AVD_HOME/photo-proof-api26.avd" > "$out/avd-create.log" 2>&1
fi
"$adb" start-server
"$ANDROID_HOME/emulator/emulator" -avd photo-proof-api26 -port 5560 -accel off -no-window -no-audio -no-snapshot -no-boot-anim -wipe-data -gpu swiftshader -memory 1536 -cores 2 -skin 480x800 -camera-back none -camera-front none > "$out/emulator.log" 2>&1 &
emupid=$!
cleanup() {
 status=$?
 timeout 30 "$adb" -s emulator-5560 logcat -d > "$out/logcat.txt" 2>&1 || true
 timeout 20 "$adb" -s emulator-5560 shell getprop > "$out/properties.txt" 2>&1 || true
 timeout 15 "$adb" -s emulator-5560 emu kill >/dev/null 2>&1 || kill "$emupid" 2>/dev/null || true
 echo "$status" > "$out/exit-code.txt"
 exit "$status"
}
trap cleanup EXIT
booted=false
for i in $(seq 1 180); do
 kill -0 "$emupid" 2>/dev/null || { tail -60 "$out/emulator.log"; exit 1; }
 if [[ $(timeout 10 "$adb" -s emulator-5560 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r') == 1 ]]; then booted=true; break; fi
 sleep 5
done
[[ $booted == true ]] || { echo 'Emulator boot not completed within 15 minutes'; exit 1; }
echo 'API26 x86 software emulator booted'
"$adb" -s emulator-5560 shell input keyevent 82
"$adb" -s emulator-5560 shell pm list packages > "$out/packages-before-install.txt"
timeout 120 "$adb" -s emulator-5560 install smoke-host/build/outputs/apk/debug/smoke-host-debug.apk > "$out/install-host.txt"
timeout 120 "$adb" -s emulator-5560 install -t smoke-host/build/outputs/apk/androidTest/debug/smoke-host-debug-androidTest.apk > "$out/install-tests.txt"
timeout 600 "$adb" -s emulator-5560 shell am instrument -w -r dev.oritwig.photoproof.test/dev.oritwig.photoproof.ProofInstrumentation | tee "$out/instrumentation.txt"
"$adb" -s emulator-5560 exec-out run-as dev.oritwig.photoproof tar -c -C files proof-results > "$out/results.tar"
mkdir -p "$out/exported"; tar xf "$out/results.tar" -C "$out/exported"
"$adb" -s emulator-5560 shell am start -n dev.oritwig.photoproof/.ProofActivity > "$out/launch.txt"
sleep 8
"$adb" -s emulator-5560 exec-out screencap -p > "$out/host-screen.png"
python3 - "$out/instrumentation.txt" <<'PY'
from pathlib import Path
import re,sys
s=Path(sys.argv[1]).read_text()
assert re.findall(r'^INSTRUMENTATION_CODE: (-?\d+)\s*$',s,re.M)==['-1'],s
assert re.search(r'^INSTRUMENTATION_RESULT: passed=\d+',s,re.M),s
print('Runtime proof passed')
PY
