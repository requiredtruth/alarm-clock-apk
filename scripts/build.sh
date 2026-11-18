#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK directory}}"
tools="${ANDROID_BUILD_TOOLS:-$sdk/build-tools/34.0.0}"
platform="${ANDROID_PLATFORM_JAR:-$sdk/platforms/android-34/android.jar}"
key_store="${ALARM_KEYSTORE:?Set ALARM_KEYSTORE to the release PKCS12 keystore}"
: "${ALARM_KEY_PASSWORD:?Set ALARM_KEY_PASSWORD to the keystore password}"

mkdir -p build/res/raw build/generated build/classes build/dex
python3 scripts/tone.py build/res/raw/alarm.wav
"$tools/aapt2" compile --dir res -o build/drawables.zip
"$tools/aapt2" compile --dir build/res -o build/tone.zip
"$tools/aapt2" link --manifest AndroidManifest.xml -I "$platform" \
  --java build/generated -R build/drawables.zip -R build/tone.zip \
  -0 wav -o build/resources.apk
mapfile -d '' sources < <(find src build/generated -name '*.java' -print0)
javac --release 8 -classpath "$platform" -encoding UTF-8 -d build/classes "${sources[@]}"
jar cf build/classes.jar -C build/classes .
"$tools/d8" --min-api 26 --lib "$platform" --output build/dex build/classes.jar
cp build/resources.apk build/unaligned.apk
zip -qj build/unaligned.apk build/dex/classes.dex
"$tools/zipalign" -f -p 4 build/unaligned.apk build/aligned.apk
"$tools/apksigner" sign --ks "$key_store" --ks-pass env:ALARM_KEY_PASSWORD \
  --ks-key-alias alarm-clock --out build/alarm-clock.apk build/aligned.apk
"$tools/apksigner" verify --verbose --print-certs build/alarm-clock.apk
"$tools/aapt2" dump badging build/alarm-clock.apk | sed -n '1,16p'
