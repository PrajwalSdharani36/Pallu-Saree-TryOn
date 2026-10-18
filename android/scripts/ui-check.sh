#!/usr/bin/env bash
# Runs inside the CI emulator: installs the app + walkthrough test, runs it, collects screenshots.
set -x
OUT=../ui-shots
mkdir -p "$OUT"
adb wait-for-device
adb shell settings put system screen_off_timeout 1800000
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
adb install -r -g app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb logcat -c
run() {  # one test method at a time, so the screen size can be changed between them
  adb shell am instrument -w -e server http://10.0.2.2:8000 -e key ci-key \
    -e class "com.pallu.tryon.MirrorWalkthroughTest#$1" \
    com.pallu.tryon.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | tee -a "$OUT/instrument.txt"
}
adb shell wm size reset; adb shell wm density reset    # the tablet itself
adb shell wm size >> "$OUT/instrument.txt"
run tabletPortrait
run tabletLandscape
adb shell wm size 1080x2400; adb shell wm density 420  # a typical staff phone
run phoneStaff
adb pull /sdcard/Android/data/com.pallu.tryon/files/shots "$OUT/" || true
adb logcat -d > "$OUT/logcat.txt" || true
grep -nE "FATAL|AndroidRuntime|PalluCamera|E Compose|CameraX" "$OUT/logcat.txt" > "$OUT/logcat-app.txt" || true
adb shell wm size reset
adb shell wm density reset
# fail the step if any test failed, after everything is collected
! grep -qE "FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed" "$OUT/instrument.txt"
