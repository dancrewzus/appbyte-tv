#!/usr/bin/env bash
# AppByte TV · reversible clean-up of an Android TV over ADB.
#
#   tools/debloat.sh <tv-ip> measure                 RAM / swap / process count / launcher start time (run it before AND after)
#   tools/debloat.sh <tv-ip> plan    <list-file>     shows what each package of the list is doing right now (changes nothing)
#   tools/debloat.sh <tv-ip> apply   <list-file>     removes the packages of the list for the current user
#   tools/debloat.sh <tv-ip> restore <list-file>     brings them back
#   tools/debloat.sh <tv-ip> launcher-off [package]  disables the stock launcher so AppByte TV becomes Home (default com.google.android.tvlauncher)
#   tools/debloat.sh <tv-ip> launcher-on  [package]  enables it again (do this first if anything looks wrong)
#
# Nothing is deleted from the system image: `pm uninstall -k --user 0` only hides a package for user 0, and `cmd package install-existing <pkg>` brings it back.
# A list file has one package per line; everything after '#' is a comment. Lists live in tools/tiers/.
# Needs `adb` in the PATH (or set ADB=/path/to/adb) and "Network debugging" / "ADB debugging" enabled on the TV (see docs/INSTALL.md).
set -euo pipefail
ADB=${ADB:-adb}
IP=${1:?usage: $0 <tv-ip> measure|plan|apply|restore|launcher-off|launcher-on [file|package]}; ACT=${2:?missing action}; ARG=${3:-}
$ADB connect "$IP:5555" >/dev/null 2>&1 || true
A() { $ADB -s "$IP:5555" shell "$@"; }
pk() { grep -v '^\s*#' "$ARG" | awk '{print $1}' | grep -E '^[a-zA-Z0-9_.]+$'; }
need_file() { [ -f "$ARG" ] || { echo "list file required (see tools/tiers/)"; exit 1; }; }
case "$ACT" in
  measure)
    A 'grep -E "MemTotal|MemAvailable|SwapTotal|SwapFree" /proc/meminfo | tr "\n" " "; echo; echo "processes: $(ps -A | wc -l)"'
    for _ in 1 2 3; do A 'am force-stop com.google.android.tvlauncher; sleep 2; am start -W -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null | grep WaitTime'; sleep 3; done ;;
  plan)
    need_file
    for p in $(pk); do printf '%s: installed=%s running=%s ' "$p" "$(A "pm list packages | grep -c ^package:$p\$" | tr -d '\r')" "$(A "ps -A | grep -c ' $p\$'" | tr -d '\r')"; A "dumpsys package $p | grep -m1 versionName" | tr -d '\r'; echo; done ;;
  apply)   need_file; for p in $(pk); do printf '%s: ' "$p"; A "pm uninstall -k --user 0 $p" 2>&1 | tr -d '\r' | tail -1; done ;;
  restore) need_file; for p in $(pk); do printf '%s: ' "$p"; A "cmd package install-existing $p" 2>&1 | tr -d '\r' | tail -1; done ;;
  launcher-off) A "pm disable-user --user 0 ${ARG:-com.google.android.tvlauncher}"; sleep 2; A "input keyevent KEYCODE_HOME" ;;
  launcher-on)  A "pm enable ${ARG:-com.google.android.tvlauncher}" ;;
  *) echo "unknown action: $ACT"; exit 1 ;;
esac
