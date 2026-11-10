# RideTune 차량용 MVP

이용자별 감각 환경 선호를 저장하고, 해당 차량이 실제로 지원하는 공조·앰비언트 조명 설정을 미리 확인한 후 적용하는 Android Automotive 앱입니다. 서버와 휴대폰 없이 차량용 흐름부터 구현했습니다. 실제 Pleos SDK를 사용하며 연결 실패 시 모의 성공으로 대체하지 않습니다.

## 시작

Android Studio에서 이 `vehicle-app` 폴더를 엽니다. Gradle JDK는 **JDK 17**을 선택하세요. 현재 Mac의 JDK는 `/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`입니다. Studio 내장 JDK 25와 Gradle 8.9 조합 대신 JDK 17을 사용합니다.

상위 프로젝트 폴더에서:

```sh
source scripts/android-env.sh
./scripts/start-pleos.sh
# 에뮬레이터가 부팅된 후, 별도 터미널에서
./vehicle-app/gradlew -p vehicle-app :app:installDebug
adb install --user "$(adb shell am get-current-user | tr -d '\r')" -r vehicle-app/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.humanfirst.ridetune.vehicle/.MainActivity
```

이미 AVD가 실행 중이면 다시 실행하지 않습니다. 상위 폴더의 `./scripts/run-ridetune.sh`로 빌드·설치·실행을 한 번에 할 수도 있습니다. 필요할 때 `scripts/connect-console.sh`로 Virtual Console을 연결합니다. APK: `app/build/outputs/apk/debug/app-debug.apk`.

빌드 기준: Kotlin 1.9.25 / AGP 8.7.3 / Gradle 8.9 / JDK 17 / compile·target SDK 34 / min SDK 28 / Pleos Vehicle 2.3.2. Pleos 공식 Maven 저장소에서 SDK를 받습니다. 프로젝트 시크릿을 소스나 APK에 넣지 않았습니다.

## 시연 흐름

1. Pleos AVD의 기어가 P인지 확인합니다. 앱 상단에 연결 상태와 P단 여부가 표시됩니다.
2. 바다·숲·온유 중 프로필을 선택합니다. 예시는 특정 장애의 권장 설정이 아닌 임의의 시연값입니다.
3. 설정 편집에서 별명, 적용 좌석, 온도·풍량·조명을 지정하고 저장합니다. `유지`/미지정은 OFF와 다릅니다.
4. `적용 미리보기`에서 현재값 → 요청값, 공유 구역, 제외 사유를 확인합니다. 지원하지 않는 뒷좌석을 앞좌석으로 몰래 대체하지 않습니다.
5. 확인 후 적용하면 SDK 조회값을 다시 읽어 항목별 결과를 표시합니다. 실패 항목은 재시도할 수 있습니다.
6. `이용 종료·복원`에서 원래값을 확인하고 복원합니다. 이용 중 외부에서 바뀐 값은 덮어쓰지 않으며 `현재값 유지`를 선택해 마칠 수 있습니다.
7. 앱이 종료돼도 진행 중 세션의 원래값/시도 기록은 남습니다. 재실행 후 자동 재적용하지 않고 이용 이어보기로 확인합니다.

프로필 편집 화면의 JSON 버튼은 개발용 가져오기·내보내기 텍스트입니다. 홈에서 JSON을 붙여 넣은 후 편집·저장할 수 있습니다. 전송 규격과 예시는 `docs/profile-format.txt`, `docs/sample-profile.json`을 참고하세요. 현재 QR 카메라, BLE 페어링, iPhone 앱은 포함하지 않습니다.

## 실제 제어 및 제약

확인한 Pleos API 34 AVD 기준입니다. 다른 차종은 실행 시 capability와 현재값을 다시 확인합니다.

| 항목 | 현재 AVD | 처리 |
|---|---|---|
| 설정 온도 | 앞좌석 좌·우 각각 15~30°C, 0.5°C 간격 | 범위와 간격 검증 |
| 풍량 | 앞좌석 공유 1~8단계 | LOW=1, MEDIUM=5, HIGH=8; OFF=0은 미지원으로 제외 |
| 공조 전원·A/C | 1·2열 공유 | 적용 전 공조 전원 확인 |
| 자동 공조 | 앞좌석 공유 | 수동 풍량 전에 OFF 확인 |
| 앰비언트 스위치·색상·밝기 | 1·2열 공유, 밝기 0~100 | 공유 구역 표시, ARGB 변환, 읽기 재확인 |
| 송풍 방향 | 지원 모드 목록이 비어 있음 | setter가 있어도 요청을 보내지 않음 |
| 주행 모드 연동 색상 | ERROR_NOT_SUPPORTED_PROPERTY | 제외; 권한/통신 오류는 미지원으로 간주하지 않음 |

`VehicleGateway.kt`에 실제 API 호출을 격리했습니다. `Vehicle.initialize()`, `getHVAC()`, `getLight()`, 각 `check…Capabilities / get… / set…`을 사용합니다. 자세한 시그니처는 `docs/SDK-signatures.txt`, 측정 결과는 `docs/sdk-verification.txt`에 있습니다.

P단 확인은 현재 SDK의 deprecated `getGear().getCurrentGearSelectionState()`를 사용합니다. 이번 AVD에서 검증했으며 다음 SDK/실차 대응 시 표준 AAOS GEAR_SELECTION으로 이전할 지점입니다. 확인 실패 시 쓰기를 막습니다. P단을 확인한 직후 기어가 바뀌는 경쟁 조건을 앱만으로 완전히 제거할 수 없으므로 실차 권한·운전자 주의분산 정책 검증은 별도로 필요합니다.

경고음, ADAS, 안전벨트 경고, 차량 전체 음소거, 외부 소음 제거, 좌석 이동, 내비 제어는 구현하지 않았습니다. 센서로 감정/불안을 판단하지 않습니다. AI 모델이나 API도 아직 연결하지 않았습니다. 실차/다른 차종 권한 승인·배포 심사와 실제 이용자 검증 전 단계인 개발용 APK입니다.

## 구조와 저장

- Models: 프로필 규격/검증, 차량 지원 범위에 맞춘 적용 계획
- VehicleGateway: Pleos SDK 비동기 콜백을 suspend 함수로 변환
- SessionEngine: 직렬 적용, 쓰기 전 기록, 실제값 재확인, 재시도와 충돌을 보존하는 복원
- Storage: 앱 내부 저장소 AtomicFile, 프로필 및 마지막 세션만 저장
- RideViewModel: 화면 상태/작업 잠금/기어 재조회
- MainActivity: Compose 기반 한국어 화면

앱은 인터넷 권한을 요청하지 않습니다. 진단명·의료정보는 저장하지 않으며 별명과 설정값만 저장합니다. OS 클라우드 백업과 기기 전송을 제외했습니다. 현재 로컬 프로필은 최대 20개, JSON 16KB 이하입니다. 공용 차량 배포 전에는 프로필 삭제/보관기간 UX를 추가해야 합니다.

## 검증

```sh
source ../scripts/android-env.sh
./gradlew :app:testDebugUnitTest :app:lintDebug
# Pleos 에뮬레이터와 P단 필요. 차량값을 일시 변경한 뒤 복원합니다.
./gradlew :app:connectedDebugAndroidTest
```

테스트는 프로필 검증, 범위·좌석·공유 구역, 주행 잠금, 중복 실행, 변경된 미리보기, 실패 재시도, 외부 변경 보존, 세션 기록과 복원을 다룹니다. 실제 SDK 테스트는 온도·풍량·조명 값의 변경/복원과 통합 프로필 적용·저장 재로드·복원을 검증합니다. 테스트 실행기는 테스트 후 APK를 제거할 수 있으므로 시연 전 `installDebug`를 다시 실행하세요.
