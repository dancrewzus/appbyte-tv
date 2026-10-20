#!/usr/bin/env bash
# Saves a 1920x1080 screenshot of the TV: tools/screenshot.sh <tv-ip> out.png
# Demo mode (made-up house, no personal data):  adb -s <ip>:5555 shell am start -S -n cl.appbyte.tv/.HomeActivity --ez demo true
set -euo pipefail
ADB=${ADB:-adb}; IP=${1:?usage: $0 <tv-ip> out.png}; OUT=${2:?missing output file}
$ADB connect "$IP:5555" >/dev/null; $ADB -s "$IP:5555" shell screencap -p /sdcard/appbyte-shot.png; $ADB -s "$IP:5555" pull /sdcard/appbyte-shot.png "$OUT" >/dev/null; $ADB -s "$IP:5555" shell rm /sdcard/appbyte-shot.png
