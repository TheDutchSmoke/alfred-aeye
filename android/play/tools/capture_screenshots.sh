#!/bin/bash
# Capture the Play phone screenshots from a debug build on an emulator.
#
#     android/play/tools/capture_screenshots.sh emulator-5580
#
# Writes 01-overview, 02-overview-dark and 03-settings into android/play/screenshots/ at
# 1080x2160: Play refuses a screenshot whose long side is more than twice the short one, which a
# Pixel's native 1080x2400 is. Uses the debug build's screenshot extras (sample data, no chip).
# 04-widget (the large widget on the home screen) is placed by hand from the widget picker
# after turning on Settings > Sample data; see PLAY_STORE.md. Watch shots: also in PLAY_STORE.md.
set -eu
SERIAL=${1:?usage: capture_screenshots.sh <adb serial>}
ADB=${ADB:-/usr/local/share/android-commandlinetools/platform-tools/adb}
OUT="$(cd "$(dirname "$0")/.." && pwd)/screenshots"
A() { "$ADB" -s "$SERIAL" "$@"; }
C=studio.cimento.aeye/com.giovanni.aeye.MainActivity
mkdir -p "$OUT"

A shell wm size 1080x2160
A shell cmd uimode night no >/dev/null
A shell settings put global sysui_demo_allowed 1
demo() { A shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
demo exit
demo enter
# The sample's reset times follow the real clock, so the status bar must too.
demo clock -e hhmm "$(A shell date +%H%M | tr -d '\r')"
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4 -e fully true
demo network -e mobile hide
demo notifications -e visible false

shot() { A shell am force-stop studio.cimento.aeye; A shell am start -W -n $C "$@" >/dev/null; sleep 3; }
png() { A exec-out screencap -p | python3 -c "import sys;from PIL import Image;Image.open(sys.stdin.buffer).convert('RGB').save(sys.argv[1])" "$OUT/$1"; }
shot --ez screenshot true;          png 01-overview.png
shot --ez screenshot_settings true; sleep 1; png 03-settings.png
A shell cmd uimode night yes >/dev/null; sleep 2
shot --ez screenshot true;          png 02-overview-dark.png
A shell cmd uimode night no >/dev/null
echo "wrote $OUT/01-overview.png 02-overview-dark.png 03-settings.png (wm size left at 1080x2160; 'adb shell wm size reset' to undo)"
