# RideTune (라이드튠)

> RideTune은 감각에 예민한 탑승자의 선호에 맞춰 Pleos 차량의 공조·조명을 조절하고, 이동 순서와 낯선 소리를 차량 화면과 한국어 음성으로 안내하는 앱입니다.

---

## 1. 주요 기능

1. **감각 프로필과 적용 전 확인**. 선호 온도, 풍량, 조명 설정을 확인하고 P단에서 차량이 지원하는 항목을 적용합니다. iPhone 연결 번호를 이용한 실시간 프로필 전송은 개발용 Debug 빌드에서 시연합니다.
2. **안심 대화 단짝**. 반복 질문이나 빗길·터널 등 상황을 화면에서 선택하면, 규칙 기반 한국어 응답과 TTS를 제공하고 지금과 다음 순서를 표시합니다.
3. **감각 조정과 복원**. 공조·조명 조정은 P단과 SDK 지원 범위에서 수행합니다. 주행 중에는 음성·화면 안내를 제공합니다. P단에서 변경 이력을 바탕으로 복원하고 항목별 결과를 확인할 수 있습니다.

---

## 2. APK 파일 위치

- **저장소 내 APK 경로**: [`apk/HumanFirst_RideTune_release.apk`](./apk/HumanFirst_RideTune_release.apk)
  - 에뮬레이터 시연용 서명된 Release APK입니다. 내장 프로필로 차량 앱을 시연할 수 있습니다.

---

## 3. 개발에 사용한 Pleos Connect 에뮬레이터 버전과 화면 규격

| 항목 | 규격 / 버전 |
| :--- | :--- |
| **시스템 이미지** | **Pleos Connect v2.1.0 APIs arm64** (`Android 14 / API Level 34`) |
| **Pleos Vehicle SDK** | `ai.pleos.playground:Vehicle:2.3.2` (`Hvac`, `Light`, `Gear`, `Vehicle`) |
| **AVD 이름** | `RideTune_Pleos_API34` |
| **화면 해상도 및 밀도** | **2560 × 1440 px**, **160 dpi (mdpi)** 가로형 차량 디스플레이 |
| **할당 메모리 (RAM)** | 4096 MB (Cold Boot 실행) |

---

## 4. 테스트 환경 및 필요한 설정

### 개발 및 테스트 환경
- **OS**: macOS (Apple Silicon arm64)
- **JDK**: OpenJDK 17 (`17.0.21`)
- **Android SDK**: Compile SDK 34 / Target SDK 34 / Min SDK 28, Build Tools `34.0.0`, Android Gradle Plugin `8.5.2`, Kotlin `1.9.25`
- **iOS Companion App (선택 연동)**: Xcode, iOS Simulator 또는 실물 iPhone (동일 Wi-Fi 로컬 네트워크 권한 허용)

### 필요한 설정
- Pleos Connect 에뮬레이터 실행 후 차량 상태 시뮬레이션 웹 콘솔(`http://127.0.0.1:8088`)을 연결하려면 ADB 포트 포워딩이 필요합니다 (`scripts/connect-console.sh`에서 자동 설정).
- Debug 차량 앱에서 iOS 프로필 실시간 전송을 시연하려면 TCP 포트 포워딩(`48732` / 로컬 LAN `48733`)이 활성화되어 있어야 합니다 (`scripts/run-ridetune.sh`에서 자동 설정).
- iPhone 없이 **Pleos 차량 에뮬레이터 단독**으로도 앱 내장 샘플 프로필(`바다`, `숲`, `온유`)로 설정 미리보기·적용·복원과 대화 기능을 테스트할 수 있습니다.

---

## 5. 실행 방법

### 방법 A. 제출된 Release APK 바로 설치 및 실행
Pleos Connect 에뮬레이터가 실행된 상태에서 프로젝트 루트 경로에서 아래 명령어를 실행합니다.

```bash
# Android SDK의 platform-tools가 PATH에 등록되어 있어야 합니다.
car_user=$(adb -e shell am get-current-user | tr -d '\r')
adb -e install -r --user "$car_user" apk/HumanFirst_RideTune_release.apk
adb -e shell am start --user "$car_user" -n com.humanfirst.ridetune.vehicle/.MainActivity
```

P단에서 내장 프로필을 선택하고 **변경 내용 확인 → 이 설정 적용**을 누릅니다. **AI 대화 단짝**에서 반복 질문이나 창밖 상황 버튼을 누를 수 있습니다. 적용을 마치면 이용 화면에서 **이전 설정으로 복원**을 선택합니다.

### 방법 B. 소스 빌드와 iPhone 연동 시연

스크립트의 SDK·JDK 경로와 AVD 이름은 개발 환경 기준입니다. 다른 환경에서는 `scripts/android-env.sh`와 `scripts/start-pleos.sh`의 경로 및 AVD 이름을 맞춰 주세요.
```bash
# 1. Pleos Connect 에뮬레이터 기동 (Cold Boot)
./scripts/start-pleos.sh

# 2. (새 터미널에서) 차량 웹 콘솔(http://127.0.0.1:8088) 포트 연결
./scripts/connect-console.sh

# 3. 차량용 앱 빌드·설치·실행 및 iOS 연동 포트 포워딩 활성화
./scripts/run-ridetune.sh
```

Debug 차량 앱에서 **iPhone에서 프로필 가져오기**를 누르고, iOS 앱에서 차량에 표시된 연결 번호를 확인한 뒤 **프로필 보내기**를 누릅니다. 같은 Mac의 iOS 시뮬레이터를 사용하면 로컬 포트 포워딩으로 연결합니다. 실제 iPhone으로 시연할 때는 Mac과 같은 Wi-Fi에서 Mac의 주소를 입력하고 로컬 네트워크 접근을 허용합니다.

### 방법 C. 유닛 테스트 및 Release APK 직접 빌드
```bash
source scripts/android-env.sh
./vehicle-app/gradlew -p vehicle-app :app:testDebugUnitTest :app:assembleRelease
```
