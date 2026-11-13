# 제출 빌드 검증 2026-11-14

## 이번 빌드

- 화면 개편: 출발 전 화면(진행자·이야기·시작)과 재생 화면(남은 시간·장면 흐름·자막). 개발용 버튼은 `시연 도구` 뒤로 접고, 재생 중 시험 버튼은 시험 입력 표시를 눌렀을 때만 보이게 했다. 편성 엔진(`engine/StoryScheduler.kt`)은 바꾸지 않았다.
- 음원 표기: 진행자별 내장 음원 폴더를 읽고, 합성 음성과 가족 녹음을 구분해 표시한다. 가족 녹음 칸은 아직 비어 있다는 안내만 한다.
- Kotlin 편성 엔진 단위시험: tests=10, failures=0, errors=0, skipped=0.
- Gradle assembleRelease 성공. release 앱 ID `local.todakgil.probe.submission`, debuggable 아님.
- 기존 제출 키로 APK Signature Scheme v3 서명, apksigner verify 통과. 이전 제출 APK와 같은 키다.
- Pleos 2.1.0 / API 34 / x86_64 / 1920×1080 에뮬레이터 설치 성공. 별도 음원 push 없이 내장 합성 음원으로 재생.

APK SHA-256: `e9db3e289fb3c8d4bdbe8ceaa2deeb56a8e087e4ddbeabc6fbab361b47c3d60f`

## 시험 결과

| 시험 | 재생 순서 | 근거 |
|---|---|---|
| 남은 시간 45초 | intro → cloud → end. 종료 시 모의 남은 시간 9초. 이후 새 이야기 시작 없음 | release-short.log |
| 45초 시작, 구름 장면 중 adb로 +25초 | intro → cloud → tree → stream → end. 종료 시 모의 남은 시간 11초 | release-extended.log |
| 화면의 `시험: 길 막힘 +20초` 버튼 | 버튼 입력이 기록됨(`TEST_INPUT adjust=20`). 결말이 이미 시작된 뒤 눌러 장면은 추가되지 않음(설계대로) | release-button-plus20.log |

로그 첫 숫자는 기기 부팅 후 경과 밀리초이며 실제 날짜가 아니다.

## 시연 영상

- `demo/Todakgil_demo.mp4`: 111초, 1920×1080, H.264/AAC.
- 에뮬레이터 화면 녹화(`adb emu screenrecord`)로 화면과 소리를 함께 녹화했다. 편집은 앱 영역 확대, 자막·안내 카드, 음량 조절뿐이다. 이전 영상처럼 음성을 후반 합성하지 않았다.
- 영상의 두 시험은 위 표의 1·2행과 같은 실행이다.
- 영상은 이 APK 직전 빌드(SHA-256 `b8ead113…`)로 녹화했다. 최종 빌드와의 차이는 재생 화면의 시험 버튼(시험 입력 표시를 눌러야 나타남)과 그 버튼 이름의 내부 함수명뿐이며, 영상에 나오는 화면과 동작은 같다.

## 이전 기록

- previous-real-route.log는 2026-11-11 이전 debug 시제품의 실제 240초 내비 수신 기록이다. 이번 release 빌드에서 실제 경로를 새로 실행 검증했다는 뜻이 아니다.

## 미검증·후속

가족 음원 수집, 차량 내 녹음, 자장가 모드, 실제 이동 중 ETA 변화·도착 이벤트, 통화·주행 제한, 실차 청취 품질은 후속 개발·검증 범위다.
