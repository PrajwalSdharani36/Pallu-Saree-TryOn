#!/usr/bin/env bash
# Boots a headless emulator shaped like a Galaxy Tab S6 Lite (1200 x 2000 px, 240 dpi) for the UI walkthrough, keeping its log for diagnosis.
set -x
OUT=${OUT:-$PWD/../ui-shots}
mkdir -p "$OUT"
# free disk on the small runner (unused toolchains)
sudo rm -rf /usr/share/dotnet /opt/ghc /usr/local/share/boost /usr/local/lib/node_modules /opt/hostedtoolcache/CodeQL 2>/dev/null
{ uname -a; df -h /; cat /etc/os-release; ls -l /dev/kvm; nproc; free -g; } > "$OUT/env.txt" 2>&1
SDK=${ANDROID_HOME:-$ANDROID_SDK_ROOT}
BIN=$SDK/cmdline-tools/latest/bin
IMG="system-images;android-31;default;x86_64"  # plain AOSP: lighter, the app needs no Play services
export ANDROID_AVD_HOME="$HOME/.android/avd"
mkdir -p "$ANDROID_AVD_HOME"
yes | "$BIN/sdkmanager" --install "$IMG" emulator platform-tools > "$OUT/sdkmanager.txt" 2>&1
echo no | "$BIN/avdmanager" create avd -n tab -k "$IMG" --force -p "$ANDROID_AVD_HOME/tab.avd" >> "$OUT/sdkmanager.txt" 2>&1
CFG="$ANDROID_AVD_HOME/tab.avd/config.ini"
for kv in hw.lcd.width=1200 hw.lcd.height=2000 hw.lcd.density=240 hw.ramSize=3072 hw.cpu.ncore=2 disk.dataPartition.size=3G hw.camera.front=emulated hw.camera.back=emulated hw.keyboard=no; do
  k=${kv%%=*}; sed -i "/^${k//./\\.}[ ]*=/d" "$CFG"; echo "$kv" >> "$CFG"
done
cat "$CFG" >> "$OUT/sdkmanager.txt"
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
