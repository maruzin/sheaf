#!/usr/bin/env bash
# Guards against Play Console warnings that Sheaf has already hit once. Runs in CI (ci.yml).
# 1. Edge-to-edge: our code must not call the Window APIs deprecated in Android 15, and MainActivity
#    must call enableEdgeToEdge(). (androidx.activity's own back-compat calls are expected; see
#    docs/PLAY_CONSOLE_GUIDE.md "Known Play Console warnings".)
# 2. R8: no blanket keep rules for libraries that ship their own consumer rules.
# 3. Device catalog: touchscreen and camera stay optional.
set -euo pipefail
cd "$(dirname "$0")/.."
fail=0

if grep -rnE 'statusBarColor|navigationBarColor|setStatusBarColor|setNavigationBarColor|setDecorFitsSystemWindows|systemUiVisibility|LAYOUT_IN_DISPLAY_CUTOUT_MODE|windowOptOutEdgeToEdgeEnforcement' \
    --include='*.kt' --include='*.java' --include='*.xml' app/src feature/*/src core/*/src; then
  echo "::error::Deprecated edge-to-edge API used in app code (Play: 'uses deprecated APIs for edge-to-edge')."; fail=1
fi
if ! grep -q 'enableEdgeToEdge()' app/src/main/kotlin/com/sheaf/app/MainActivity.kt; then
  echo "::error::MainActivity must call enableEdgeToEdge() (Play: 'Edge-to-edge may not display for all users')."; fail=1
fi
if grep -nE '^-keep[a-z]* class (com\.google\.android\.gms|com\.android\.billingclient|androidx|kotlin|kotlinx|com\.google\.firebase)\.\*\*' app/proguard-rules.pro; then
  echo "::error::Blanket keep rule for a library that ships its own R8 rules (Play: 'Improve memory and performance with R8')."; fail=1
fi
for f in android.hardware.touchscreen android.hardware.camera android.hardware.screen.portrait; do
  if ! grep -qE "uses-feature android:name=\"$f\" android:required=\"false\"" app/src/main/AndroidManifest.xml; then
    echo "::error::$f must be declared required=\"false\" (Play: mark non-essential hardware optional)."; fail=1
  fi
done
# Any library activity locked to an orientation makes Play infer a required screen feature.
M=app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml
if [ -f "$M" ] && grep -q 'screenOrientation="\(sensor\)\{0,1\}landscape' "$M" && \
   ! grep -q 'android.hardware.screen.landscape" android:required="false"' app/src/main/AndroidManifest.xml; then
  echo "::error::A merged activity is locked to landscape: declare android.hardware.screen.landscape required=\"false\"."; fail=1
fi
[ "$fail" -eq 0 ] && echo "Play warning guards: OK"
exit "$fail"
