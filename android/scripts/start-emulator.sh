#!/usr/bin/env bash
# Boots a headless emulator for the UI walkthrough, keeping its log for diagnosis.
set -x
OUT=${OUT:-$PWD/../ui-shots}
mkdir -p "$OUT"
{ uname -a; cat /etc/os-release; ls -l /dev/kvm; nproc; free -g; } > "$OUT/env.txt" 2>&1
SDK=${ANDROID_HOME:-$ANDROID_SDK_ROOT}
BIN=$SDK/cmdline-tools/latest/bin
IMG="system-images;android-31;default;x86_64"  # plain AOSP: lighter, the app needs no Play services
export ANDROID_AVD_HOME="$HOME/.android/avd"
mkdir -p "$ANDROID_AVD_HOME"
yes | "$BIN/sdkmanager" --install "$IMG" emulator platform-tools > "$OUT/sdkmanager.txt" 2>&1
echo no | "$BIN/avdmanager" create avd -n tab -k "$IMG" --force -p "$ANDROID_AVD_HOME/tab.avd" >> "$OUT/sdkmanager.txt" 2>&1
printf "hw.ramSize=3072\nhw.cpu.ncore=2\ndisk.dataPartition.size=4G\nhw.camera.front=emulated\nhw.camera.back=emulated\nhw.keyboard=yes\n" >> "$ANDROID_AVD_HOME/tab.avd/config.ini"
"$SDK/emulator/emulator" -list-avds >> "$OUT/sdkmanager.txt" 2>&1
export PATH="$SDK/platform-tools:$SDK/emulator:$PATH"
nohup emulator -avd tab -no-window -no-snapshot -no-audio -no-boot-anim -gpu swiftshader_indirect \
  -camera-front emulated -camera-back emulated -accel on > "$OUT/emulator.log" 2>&1 &
EMU=$!
timeout 300 adb wait-for-device || { echo "no device"; exit 1; }
for i in $(seq 180); do
  [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && { echo "booted after $((i*5))s" | tee "$OUT/boot.txt"; break; }
  kill -0 $EMU 2>/dev/null || { echo "emulator exited"; exit 1; }
  sleep 5
done
[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] || { echo "emulator did not boot"; exit 1; }
adb shell input keyevent 82
