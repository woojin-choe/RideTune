# RideTune

현대자동차그룹 차량용 앱 개발 공모전 출품작(Pleos Connect / Android Automotive). 요강: docs/contest-info.md, 기획: docs/proposal/기획서.md.

## 빌드
- 터미널 빌드에는 `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` 필요(시스템 Java 없음).
- `./gradlew testDebugUnitTest lintDebug assembleRelease`, 제출 APK는 `./gradlew submissionApk`.
- 에뮬레이터 실행·설치: `./tools/emulator.sh`.

## Pleos SDK 주의
- Vehicle SDK의 `check…Capabilities`는 호출한 스레드를 막는다. 메인 스레드에서 부르면 ANR — IO에서 호출(PleosCabinController).
- 값 범위는 차량마다 다르므로 항상 capability 조회 후 그 범위·영역에 적용한다.
- SDK 문서 전수 수집본: 옆 저장소 hmg-vehicle-app-contest의 브랜치 `data/pleos-playground-2026-11-04`.

## 에뮬레이터(이 Mac: Intel i7-9750H, Radeon Pro 5300M)
- AVD `Pleos_UI_Intel`. `-gpu host`면 에뮬레이터 창이 검게 나온다 → `-gpu swiftshader_indirect`(tools/emulator.sh). AVD 설정보다 실행 옵션이 확실하다.
- `hw.screen=multi-touch`여야 터치 장치가 생긴다(`touch`로 바꾸면 클릭이 안 먹음).
- `-gpu host`로 켰을 때는 차량 제어(set)가 시간 초과(PropertyAsyncError 3)로 실패했다. swiftshader로 켜면 SDK 응답은 성공으로 온다(풍량만 error 2). 단 가상 콘솔 상태 화면은 `dds_ready=false`라 값 변화를 보여 주지 않는다.
- AVD 해상도는 2560×1440·density 160 그대로 둔다. 1920×1080으로 낮췄더니 Pleos 시스템 앱(drivingview)이 1분마다 죽고 하단 바가 사라졌다.
- 다른 무거운 앱(게임 등)이 돌면 시스템 앱까지 ANR이 난다.
