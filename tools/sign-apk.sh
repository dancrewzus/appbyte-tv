#!/usr/bin/env bash
# Signs an APK with YOUR keystore (release-style signature instead of the debug key). Some TVs (Sony Bravia, 2017) install a "lite"/"loc" build signed this way but block the debug-signed one.
#   tools/sign-apk.sh <unsigned-or-debug.apk> <out.apk> <keystore.jks> <key-alias>
# The passwords are asked here (hidden) and never written anywhere. Create a keystore once:  keytool -genkeypair -v -keystore my.jks -alias appbyte -keyalg RSA -keysize 2048 -validity 10000
# Build a release APK first:  cd launcher && ./gradlew assembleLiteRelease   ->  app/build/outputs/apk/lite/release/app-lite-release-unsigned.apk
set -euo pipefail
IN=${1:?usage: $0 <in.apk> <out.apk> <keystore> <alias>}; OUT=${2:?missing out.apk}; KS=${3:?missing keystore}; ALIAS=${4:?missing alias}
APKSIGNER=$(ls -d "${ANDROID_HOME:-$HOME/Library/Android/sdk}"/build-tools/*/apksigner 2>/dev/null | tail -1)
[ -x "$APKSIGNER" ] || { echo "apksigner not found: set ANDROID_HOME to your Android SDK"; exit 1; }
read -r -s -p "Keystore password: " KS_PASS; echo
read -r -s -p "Key password (Enter = same): " KEY_PASS; echo; KEY_PASS=${KEY_PASS:-$KS_PASS}
export KS_PASS KEY_PASS
"$APKSIGNER" sign --ks "$KS" --ks-key-alias "$ALIAS" --ks-pass env:KS_PASS --key-pass env:KEY_PASS --v1-signing-enabled true --v2-signing-enabled true --out "$OUT" "$IN"
"$APKSIGNER" verify --print-certs "$OUT" | grep -E "certificate SHA-256|Verified"
