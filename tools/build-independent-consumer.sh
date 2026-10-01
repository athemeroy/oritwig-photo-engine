#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"
[[ -f engine/build/outputs/aar/engine-debug.aar ]] || { echo 'Build :engine:assembleDebug first'; exit 2; }
cp engine/build/outputs/aar/engine-debug.aar standalone-consumer/app/libs/telegram-photo-engine-proof.aar
(cd standalone-consumer; sha256sum app/libs/telegram-photo-engine-proof.aar > ENGINE-AAR.sha256)
# Copy to a clean temporary directory. There is no engine source or sibling module.
temp=$(mktemp -d /tmp/telegram-photo-aar-consumer.XXXXXX)
tar --exclude=build --exclude=.gradle -C standalone-consumer -cf - . | tar -C "$temp" -xf -
(cd "$temp"; sha256sum -c ENGINE-AAR.sha256; ./gradlew --offline --no-daemon --console=plain :app:assembleDebug :app:assembleDebugAndroidTest)
mkdir -p artifacts/independent-consumer
cp "$temp/app/build/outputs/apk/debug/app-debug.apk" artifacts/independent-consumer/
cp "$temp/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" artifacts/independent-consumer/
printf '%s\n' "$temp" > artifacts/independent-consumer/build-directory.txt
