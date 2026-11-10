# 제출 체크리스트

마감: **2026.11.15(일) 23:59** · 접수: appdevchallenge@hyundai-partners.com

## 필수

- [ ] 접수양식 DOCX 내려받기 — https://appdevchallenge.auto-hmg.io/downloads/contest-application-consent.docx
- [ ] 01 참가신청서: 참가 단위, 부문(대학(원)생 / 일반), 팀명, 앱명, 대표자·팀원 정보
- [ ] 02 앱 프로젝트 소개 1~6번 작성 (초안: [proposal/기획서.md](proposal/기획서.md)) — 이름·연락처·소속 미기재 확인
- [ ] 03·04 개인정보 동의서: 대표자 + 팀원 **전원** 각각 동의·서명·날짜
- [ ] 메일 제목 `[차량용 앱 개발 공모전] 팀명(개인은 성명)_대표자명`
- [ ] 팀원 전원 CC

## 가산점 (선택)

- [ ] GitHub 저장소 주소를 기획서 7번에 기재
- [ ] Private이면 공지된 심사용 계정을 Collaborator로 초대하고 접근 확인 (쓰기 권한 주의)
- [ ] `gradle.properties`의 `ridetune.teamName` 설정 후 `./gradlew submissionApk` → `apk/팀명_RideTune_release.apk` 커밋 (release 서명 키는 세팅됨: `~/.android/keys/ridetune-release.jks`)
- [ ] APK가 에뮬레이터에서 실행되는지 확인 — 이 Mac은 설치·화면까지만 됨, 차량 제어 확인은 사양 맞는 PC 필요
- [ ] README의 한 줄 소개·실행 방법·테스트 환경·APK 위치 최신화
- [ ] 시연 영상(목업·화면 녹화 가능) 링크를 기획서 7번에 기재
- [ ] 첨부·링크에 개인정보·계정정보·실명이 드러나지 않는지 확인

## 숏폼 챌린지 (선택, 별도 상금)

- [ ] 30초~1분 세로 영상 → Instagram 릴스 / YouTube Shorts
- [ ] 해시태그 2~3개, `#HMG차량용앱개발공모전` 필수
- [ ] 링크를 기획서 8번에 기재

## 수정 제출 시

- [ ] **처음 보낸 이메일 주소**로, 제목 앞에 `[수정제출]`
- [ ] 전체 제출 자료 + 서명한 동의서 다시 모두 첨부, 팀원 전원 CC

## 이후 일정

- 11.23(월) 1차 결과 → 합격 시 12.3~4 PT 전까지 **실제 동작하는 앱** 완성(에뮬레이터 시연 가능)
