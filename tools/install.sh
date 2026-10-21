#!/usr/bin/env bash
# AppByte TV · install the APK on a TV and grant what the launcher needs.
#   tools/install.sh <tv-ip> [path/to/app-debug.apk] [--location] [--screensaver] [--make-home]
#
#   (default)       install/update the APK, allow the media listener (mini music player) and start the launcher
#   --location      turn the TV's location setting on so the Wi-Fi name (SSID) can be shown (Android hides it otherwise; optional, privacy trade-off)
#   --screensaver   use the AppByte TV screensaver (clock + slow gradient) instead of the TV's default
#   --make-home     ALSO disable the stock launcher so AppByte TV becomes the Home screen (undo: tools/debloat.sh <ip> launcher-on)
set -euo pipefail
ADB=${ADB:-adb}
IP=${1:?usage: $0 <tv-ip> [apk] [--location] [--screensaver] [--make-home]}; shift
APK=launcher/app/build/outputs/apk/full/debug/app-full-debug.apk; LOC=0; SAVER=0; HOME_=0
for a in "$@"; do case "$a" in --location) LOC=1;; --screensaver) SAVER=1;; --make-home) HOME_=1;; *.apk) APK=$a;; *) echo "unknown option $a"; exit 1;; esac; done
[ -f "$APK" ] || { echo "APK not found: $APK (build it first: cd launcher && ./gradlew assembleFullDebug  — or assembleLiteDebug / assembleLocDebug, see docs/INSTALL.md)"; exit 1; }
$ADB connect "$IP:5555" >/dev/null
A() { $ADB -s "$IP:5555" shell "$@"; }
$ADB -s "$IP:5555" install -r "$APK"
A pm grant cl.appbyte.tv android.permission.ACCESS_FINE_LOCATION || true
A cmd notification allow_listener cl.appbyte.tv/cl.appbyte.tv.MediaListener || true
[ $LOC = 1 ] && A settings put secure location_mode 3
[ $SAVER = 1 ] && { A settings put secure screensaver_components cl.appbyte.tv/.AppByteDream; A settings put secure screensaver_enabled 1; }
A input keyevent KEYCODE_WAKEUP
A am start -n cl.appbyte.tv/.HomeActivity
if [ $HOME_ = 1 ]; then echo "Disabling the stock launcher (undo with: tools/debloat.sh $IP launcher-on)"; "$(dirname "$0")/debloat.sh" "$IP" launcher-off; fi
echo "done"
