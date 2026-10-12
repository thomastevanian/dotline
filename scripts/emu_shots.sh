#!/usr/bin/env bash
# Drives Dotline on a running emulator and saves screenshots to ./shots (JPEG, 720 px wide).
# Every step is best-effort: a missing button is logged and the run continues, so one UI change
# never loses the other screenshots. Used by .github/workflows/screens.yml.
set -u
PKG=com.dotline.launcher
APK=app/build/outputs/apk/debug/app-debug.apk
mkdir -p raw shots
LOG=shots/log.txt
: > "$LOG"
say() { echo "$*" | tee -a "$LOG"; }

shot() { # name [wait seconds]
  sleep "${2:-1.5}"
  adb exec-out screencap -p > "raw/$1.png"
  python3 - "$1" <<'PY'
import sys
from PIL import Image
n = sys.argv[1]
im = Image.open(f"raw/{n}.png").convert("RGB")
w = 720
im = im.resize((w, round(im.height * w / im.width)), Image.LANCZOS)
im.save(f"shots/{n}.jpg", quality=88)
PY
  say "shot $1"
}

tap_text() { # exact text or content-description; optional 2nd arg "last" = top-most match
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb pull /sdcard/ui.xml raw/ui.xml > /dev/null 2>&1
  local xy
  xy=$(python3 - "$1" "${2:-first}" <<'PY'
import re, sys
import xml.etree.ElementTree as ET
t, mode = sys.argv[1], sys.argv[2]
try:
    root = ET.parse("raw/ui.xml").getroot()
except Exception:
    sys.exit(0)
hits = []
for n in root.iter("node"):
    if n.get("text") == t or n.get("content-desc") == t:
        b = list(map(int, re.findall(r"\d+", n.get("bounds"))))
        hits.append(((b[0] + b[2]) // 2, (b[1] + b[3]) // 2))
if hits:
    x, y = hits[-1] if mode == "last" else hits[0]
    print(x, y)
PY
)
  if [ -n "$xy" ]; then
    adb shell input tap $xy
    say "tapped '$1' at $xy"
    return 0
  fi
  say "WARN: '$1' not found"
  return 1
}

swipe() { adb shell input swipe "$1" "$2" "$3" "$4" "${5:-250}"; }
home() { adb shell input keyevent KEYCODE_HOME; }
back() { adb shell input keyevent KEYCODE_BACK; }

say "device: $(adb shell getprop ro.product.model) $(adb shell wm size | tail -1) $(adb shell wm density | tail -1)"
adb install -r -g "$APK" > /dev/null && say "installed"
adb shell cmd role add-role-holder android.app.role.HOME $PKG > /dev/null 2>&1
adb shell logcat -c

# 1. First run: the intro.
home
shot 01_onboarding_1 4
tap_text "Next" && shot 02_onboarding_2 1.2
tap_text "Next" && shot 03_onboarding_3 1.2

# 2. Skip the intro (the same button a real user taps) and look at the home screen.
tap_text "Skip"
shot 04_home_page1 5
swipe 900 1300 150 1300 250 && shot 05_home_swipe_left 1.5
swipe 900 1300 150 1300 250 && shot 06_home_swipe_left_again 1.5
swipe 150 1300 900 1300 250 && swipe 150 1300 900 1300 250 && shot 07_home_swipe_back 1.5

# 3. Drawer and search.
home; sleep 1
swipe 540 2050 540 700 260 && shot 08_drawer 2
adb shell input tap 540 2250 && sleep 1 && adb shell input text "c" && shot 09_drawer_search 1.5
back; sleep 0.5; back; sleep 1; home; sleep 1

# 4. App menu (long press an icon), then remove that icon so its cell is empty, then long press
#    the empty cell: that is how edit mode opens. Then the widget picker.
home; sleep 1
adb shell input swipe 540 1100 540 1100 900
shot 10_app_menu 1.5
tap_text "Remove from home"; sleep 1
adb shell input swipe 540 1100 540 1100 900
shot 11_edit_mode 1.5
tap_text "Widgets" last && shot 12_widget_picker 2
back; sleep 1

# 5. Settings and its pages (opened from edit mode, like a real user would).
tap_text "Settings" last && shot 13_settings 2
for page in "Home" "Icons" "Clock and widgets" "Drawer" "Gestures" "Theme" "Backup and restore" "About"; do
  if tap_text "$page"; then
    name=$(echo "$page" | tr 'A-Z ' 'a-z_')
    shot "14_settings_$name" 1.2
    back; sleep 0.8
  fi
done
tap_text "Sounds" && shot 15_sound_studio 2 && back && sleep 1
tap_text "Finish the look" && shot 16_finish_the_look 2 && back && sleep 1
tap_text "Wallpapers" && shot 17_wallpaper_studio 2.5 && back && sleep 1

# 6. Diagnostics.
home; sleep 1
{
  echo "=== app crash log ==="
  adb shell run-as $PKG cat files/crash_log.txt 2>&1 | head -120
  echo "=== logcat errors from the app ==="
  adb logcat -d -b crash 2>&1 | head -80
  adb logcat -d *:E 2>&1 | grep -i "dotline" | head -40
} > shots/diagnostics.txt
say "done"
