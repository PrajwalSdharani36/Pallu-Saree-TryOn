#!/usr/bin/env bash
# Boots a headless emulator for the UI walkthrough, keeping its log for diagnosis.
set -x
OUT=${OUT:-$PWD/../ui-shots}
mkdir -p "$OUT"
{ uname -a; cat /etc/os-release; ls -l /dev/kvm; nproc; free -g; } > "$OUT/env.txt" 2>&1
SDK=${ANDROID_HOME:-$ANDROID_SDK_ROOT}
BIN=$SDK/cmdline-tools/latest/bin
IMG="system-images;android-31;google_apis;x86_64"
yes | "$BIN/sdkmanager" --install "$IMG" emulator platform-tools > "$OUT/sdkmanager.txt" 2>&1
echo no | "$BIN/avdmanager" create avd -n tab -k "$IMG" --force >> "$OUT/sdkmanager.txt" 2>&1
export PATH="$SDK/platform-tools:$SDK/emulator:$PATH"
nohup emulator -avd tab -no-window -no-snapshot -no-audio -no-boot-anim -gpu swiftshader_indirect \
  -camera-front emulated -camera-back emulated -memory 4096 -cores 4 -accel on > "$OUT/emulator.log" 2>&1 &
adb wait-for-device
for i in $(seq 180); do
  [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && { echo "booted after $((i*5))s"; break; }
  sleep 5
done
[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] || { echo "emulator did not boot"; exit 1; }
adb shell input keyevent 82
