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
- AVD `Pleos_UI_Intel`. `hw.screen=multi-touch`여야 터치 장치가 생긴다(`touch`로 바꾸면 클릭이 안 먹음).
- 가상 차량 장치가 비활성(콘솔 `emulator_inactive`, `dds_ready=false`)이라 차량 제어(set)는 시간 초과로 실패한다. 공식 최소 사양(RTX 3060급 GPU) 미달이 원인으로 추정.
- 다른 무거운 앱(게임 등)이 돌면 시스템 앱까지 ANR이 난다.
