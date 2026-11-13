# 토닥길 공모전 개발 산출물

토닥길은 남은 이동 시간에 따라 이야기 장면을 선택하고 결말까지 재생하는 가족 오디오 앱을 제안합니다. 이 저장소는 2026년 현대자동차그룹 차량용 앱 개발 공모전 제출용 **기술 시제품**입니다.

## 먼저 실행하기

1. Pleos Connect 에뮬레이터를 부팅합니다. 일반 휴대전화 대신 아래 검증 환경을 사용하세요.
2. `apk/방윤정_토닥길_release.apk`를 설치합니다. 기존 검증용 앱과 별도 패키지입니다.
3. 앱을 열면 출발 전 화면이 나옵니다. 진행자는 `기본 음성(합성 음성)`이 선택되어 있습니다. 옆의 `+ 가족 목소리` 칸은 가족 녹음이 들어갈 자리를 보여주는 안내이며 아직 누를 수 없습니다. 별도 음원 복사나 서버 설정이 필요하지 않습니다.
4. 화면 아래 작은 `시연 도구`를 누르고 `시험 45초 길`을 누르면 도입 → 구름 → 결말로 재생됩니다.
5. 다시 `시험 45초 길`을 시작하고, 구름 장면이 나오는 동안 주황색 `시험 입력 · 모의 남은 시간` 표시를 누르면 `시험: 길 막힘 +20초` 버튼이 나옵니다. 결말이 시작되기 전에 누르면 나무·시냇물 장면이 추가됩니다. 결말이 시작된 뒤에는 새 장면을 넣지 않습니다. 자동 시연은 재현성을 위해 adb로 +25초를 입력했습니다.
6. `시험 75초 길`은 전체 장면을 들을 수 있는 비교용 시험입니다. `정지`로 종료할 수 있습니다.

화면의 시험 입력은 모의 값이며 실제 내비 수신으로 표시하지 않습니다. 음원은 창작 대본을 읽는 Windows 한국어 합성 음성이고, 화면에도 `합성 음성`으로 표기합니다. 실제 가족 녹음·차량 내 녹음·자장가 모드는 이번 APK에 포함되지 않았습니다. 가족 녹음 파일을 `assets/scenes/<진행자>/`에 넣으면 같은 편성으로 재생되고 화면에 `가족 녹음`으로 표시되는 구조입니다.

```sh
adb install -r apk/방윤정_토닥길_release.apk
adb shell am start --user 10 -n local.todakgil.probe.submission/local.todakgil.probe.MainActivity
# 재현 가능한 45초 시험
adb shell am start --user 10 -n local.todakgil.probe.submission/local.todakgil.probe.MainActivity --ei story_test 45
# 시험 입력에만 남은 시간 25초 추가
adb shell am start --user 10 -n local.todakgil.probe.submission/local.todakgil.probe.MainActivity --ei story_adjust 25
```

차량 사용자 번호는 환경마다 다릅니다. `adb shell am get-current-user`로 확인하고 위 예시의 10을 바꾸세요. 기기가 여러 대라면 `adb -s <serial>`을 사용합니다.

## 실제 내비 입력

Pleos 기본 내비에서 목적지를 설정한 뒤 출발 전 화면의 `이야기 시작`을 누릅니다. NaviHelper가 전달한 남은 시간으로 편성합니다. 경로가 없거나 0·오래된 응답이면 도착으로 단정하지 않고 기본 순서를 사용합니다. 실제 경로 60초 및 240초 수신 기록을 확보했지만, 정지 에뮬레이터에서 실제 ETA가 감소하는 주행과 도착 시각 일치는 검증하지 않았습니다. 이전 240초 시험에서도 콘텐츠는 약 1분에 끝났으므로 이를 4분 길이의 이야기 구현으로 해석하면 안 됩니다.

## 환경과 빌드

| 항목 | 검증 환경 |
|---|---|
| Pleos 시스템 이미지 | RELEASE.release_connect_v2.1.0 test-keys |
| Android 이미지 | API 34 Automotive x86_64, 이미지 revision 1 |
| 에뮬레이터 | Android Emulator 37.2.13.0, build 16740729, WHPX |
| 전체 화면 | 1920 × 1080, density 160; 앱은 오른쪽 패널에 표시 |
| 내비 SDK | ai.pleos.playground:NaviHelper:3.0.5 |
| 개발 도구 | JDK 17, Gradle wrapper 8.10.2, compile/target SDK 35 |
| release 앱 ID | local.todakgil.probe.submission |

Android SDK 경로를 `ANDROID_HOME` 또는 로컬 `local.properties`에 설정합니다. 저장소에는 개발 PC 경로, SDK 바이너리, 서명 개인키를 넣지 않습니다. SDK 의존성은 Gradle이 공식 Maven 저장소에서 가져옵니다.

```sh
./gradlew testDebugUnitTest assembleRelease
```

Windows는 `gradlew.bat`를 사용합니다. 생성되는 `app/build/outputs/apk/release/app-release-unsigned.apk`에 본인의 키로 서명하면 설치할 수 있습니다. 제출된 `apk/` 파일은 별도 제출용 키로 서명했고 debuggable=false인 release 빌드입니다. 개인키는 공개하지 않습니다. 자체 빌드에 다른 키를 사용하면 제출 APK와 업데이트 호환이 되지 않습니다.

## 구현과 검증 범위

- 순수 Kotlin 편성 엔진: 필수·추가 장면, 짧은/기본 결말 선택. 단위시험 10개 통과.
- MediaPlayer foreground service: 내장 음원 재생, 재생 중 화면 상태·자막·선택 이유 표시.
- 실제 NaviHelper 입력과 감소하는 시험 입력을 구분. 장면이 끝날 때 다음 장면 선택.
- 이전 ProbeService 시험에서는 내비 안내 시 일시정지·복귀와 120초 백그라운드 재생 확인.
- 이번 제출 영상은 정지 에뮬레이터의 모의 시간 변화 시험이며 실차 주행 영상이 아닙니다.
- 통화·주행 제한·실차 청취 품질·실제 도착 시각 연동·긴 여행 콘텐츠는 추가 검증 대상입니다.
- 현재 콘텐츠는 약 1분입니다. 이동 전체를 무제한 채우거나 결말을 반복하지 않습니다.

## 시연 영상

`demo/Todakgil_demo.mp4`(1분 51초)를 내려받아 재생하세요. release 앱을 Pleos Connect 에뮬레이터에서 실행하고, 에뮬레이터의 화면 녹화 기능으로 화면과 소리를 함께 녹화했습니다. 편집에서는 앱 영역 확대, 제목·설명 자막, 음량 조절만 했고 재생 순서와 속도는 바꾸지 않았습니다. 장면 1은 남은 시간 45초, 장면 2는 같은 45초로 시작해 재생 중 adb로 25초를 더한 시험입니다. 남은 시간은 모의 입력이고 음성은 합성 음성이며, 영상 안에도 이 조건을 표시합니다. 에뮬레이터 출력 녹음이므로 실차 스피커 청취 품질의 증거는 아닙니다.

## 코드 위치

- `app/src/main/java/local/todakgil/probe/engine/StoryScheduler.kt`: 편성 엔진
- `app/src/main/java/local/todakgil/probe/StoryService.kt`: 내비 입력과 음원 재생
- `app/src/main/java/local/todakgil/probe/MainActivity.kt`: 출발 전 화면(진행자·이야기 선택)과 재생 화면(남은 시간·장면 흐름·자막). 조작은 출발 전·정차 상태 기준
- `app/src/test/`: 단위시험
- `app/src/main/assets/scenes/synth/`: 설치 후 바로 재생하는 합성 음원
- `evidence/`: 개발 검증 결과와 시연 로그

AI는 대본 초안·코드 작성과 오류 분석에 사용했습니다. 실행 중 편성은 규칙 기반이며 생성형 AI 호출·음성 복제·자유 대화는 사용하지 않습니다.

