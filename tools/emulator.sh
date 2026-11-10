#!/bin/zsh
# Pleos 에뮬레이터를 켜고(cold boot) RideTune을 설치·실행한다. 가상 콘솔은 http://127.0.0.1:8088
#   ./tools/emulator.sh          에뮬레이터 켜기 + debug 빌드 설치·실행
#   ./tools/emulator.sh release  release APK로 설치·실행
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
AVD="${RIDETUNE_AVD:-Pleos_UI_Intel}"
ROOT="${0:A:h}/.."
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"

if ! "$ADB" get-state >/dev/null 2>&1; then
    echo "에뮬레이터 켜는 중: $AVD (cold boot — quick boot이면 VHAL을 못 쓴다)"
    nohup "$SDK/emulator/emulator" -avd "$AVD" -no-snapshot -no-boot-anim >"${TMPDIR:-/tmp}/ridetune-emulator.log" 2>&1 &
fi
"$ADB" wait-for-device
until [[ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do sleep 3; done
echo "부팅 완료"

# 터치 장치가 없으면 Mac에서 클릭이 안 먹는다(AVD의 hw.screen은 multi-touch여야 한다).
"$ADB" shell getevent -p | grep -q virtio_input_multi_touch || echo "경고: 터치 장치 없음 — AVD config.ini의 hw.screen=multi-touch 확인" >&2

"$ADB" forward tcp:8088 tcp:8088 >/dev/null
echo "가상 콘솔: http://127.0.0.1:8088"

cd "$ROOT"
if [[ "${1:-debug}" == "release" ]]; then
    ./gradlew -q assembleRelease
    "$ADB" install -r app/build/outputs/apk/release/app-release.apk
else
    ./gradlew -q assembleDebug
    "$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
fi
"$ADB" shell am start -n com.ridetune.app/.ui.MainActivity
