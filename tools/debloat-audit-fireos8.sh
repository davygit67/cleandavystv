#!/bin/sh
# Read-only Fire OS 8 debloat audit for the Hisense 55E6QF.
# Inspects only. Disables nothing. Usage: TV=<tv-ip> tools/debloat-audit-fireos8.sh
# Every disable still needs the shown package list plus explicit approval.
set -eu
TV="${TV:-${1:-}}"
if [ -z "$TV" ]; then echo "Usage: TV=<tv-ip> tools/debloat-audit-fireos8.sh" >&2; exit 2; fi
OUT="work/debloat-audit-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"
echo "TV=$TV (not committed; work/ is gitignored)"
adb -s "$TV" shell getprop ro.product.model ro.product.manufacturer ro.build.version.release ro.build.version.sdk ro.build.fingerprint > "$OUT/getprop.txt" || true
adb -s "$TV" shell pm list packages -f > "$OUT/packages.txt" || true
adb -s "$TV" shell pm list packages -d --user 0 > "$OUT/disabled.txt" || true
adb -s "$TV" shell settings get secure enabled_accessibility_services > "$OUT/accessibility.txt" || true
adb -s "$TV" shell settings get secure screensaver_components > "$OUT/screensaver.txt" || true
adb -s "$TV" shell dumpsys package com.gathin.tvhome > "$OUT/quiet-machine-dumpsys.txt" || true
adb -s "$TV" shell dumpsys package com.amazon.tv.launcher > "$OUT/stock-home-dumpsys.txt" || true
cat > "$OUT/RESTORE.md" <<'EOF'
# Restore (generated per audit run)
# Re-enable a package:  adb shell pm enable <package>
# Restore launcher APK: adb install -r -d previous.apk
# Nothing was disabled by the audit itself.
EOF
echo "Wrote $OUT (packages, disabled, getprop, settings, dumpsys). Review before proposing any disable list."
