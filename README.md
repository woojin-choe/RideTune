# RideTune

> 휴대폰에서 한 번 설정한 나의 감각 프로필로, 차가 소리·빛·공기를 내게 맞춰 주는 감각 민감 탑승자용 차량 앱

현대자동차그룹 차량용 앱 개발 공모전 출품작. Pleos Connect(Android Automotive) 환경에서 동작하는 차량용 앱입니다.
공모전 요강 정리: [docs/contest-info.md](docs/contest-info.md)

## APK

- 위치: [`apk/`](apk/) 폴더의 `팀명_RideTune_release.apk`
- _(용량 초과로 GitHub Releases에 올린 경우 Release 링크를 여기에 적는다)_

## 테스트 환경

| 항목 | 값 |
|---|---|
| 대상 플랫폼 | Pleos Connect (Android Automotive OS, API 34) |
| 에뮬레이터 | Pleos Car Emulator (Android Studio Device Manager) |
| 최소 / 대상 SDK | minSdk 28 / targetSdk 34 (compileSdk 37) |
| 빌드 도구 | Gradle 9.8.2 (wrapper 포함), AGP 9.5.0, Kotlin 2.2.10, JDK 17 이상 |
| Pleos SDK | Vehicle 2.3.2, NaviHelper 3.0.5 |

## 실행 방법

### APK로 바로 실행

1. Pleos Car Emulator를 켠다 ([설정 가이드](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)).
2. `adb install apk/팀명_RideTune_release.apk`
3. 앱 목록에서 **RideTune** 실행

### 소스에서 빌드

```bash
# Android Studio에 들어 있는 JDK를 써도 된다
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

./tools/emulator.sh         # Pleos 에뮬레이터 켜기 + debug 빌드 설치·실행 (가상 콘솔 http://127.0.0.1:8088)
./gradlew installDebug      # 이미 켜진 에뮬레이터/기기에 debug 설치
./gradlew submissionApk     # release 빌드 후 apk/<팀명>_RideTune_release.apk로 복사
```

## 필요한 설정

- `local.properties`에 Android SDK 경로(`sdk.dir=...`). Android Studio로 열면 자동 생성된다.
- Pleos SDK는 `https://nexus-playground.pleos.ai/repository/maven-releases/`에서 받는다(별도 인증 없음).
- release 서명(선택): `~/.gradle/gradle.properties`에 아래 값을 두면 서명된 APK가 만들어진다. 없으면 debug 키로 서명된다(설치·실행은 되지만 마켓 배포용은 아님).
  ```properties
  ridetune.storeFile=/절대/경로/ridetune.jks
  ridetune.storePassword=...
  ridetune.keyAlias=...
  ridetune.keyPassword=...
  ```
- 제출 APK 파일 이름의 팀명은 `gradle.properties`의 `ridetune.teamName`.

## 프로젝트 구조

```
app/src/main/kotlin/com/ridetune/app/
  domain/        감각 프로필(SensoryProfile) → 차 안 목표 설정(CabinTarget) 계산(CabinPlanner)
  data/          프로필 저장(ProfileRepository)
  data/vehicle/  Pleos Vehicle SDK로 조명·스마트글라스·공조·창문 적용(PleosCabinController)
  ui/            프로필 편집 + "내 프로필 적용" / "진정 모드" 화면
app/src/test/    CabinPlanner 단위 테스트 (./gradlew testDebugUnitTest)
tools/           에뮬레이터 실행 스크립트
.github/         GitHub Actions: 푸시마다 테스트·lint·APK 빌드
apk/             제출용 release APK
docs/            공모전 요강 정리, 기획서 초안, 제출 체크리스트
```
