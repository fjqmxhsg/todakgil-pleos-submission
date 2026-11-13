# 제출 빌드 검증 2026-11-14

- Kotlin 편성 엔진 단위시험: tests=10, failures=0, errors=0, skipped=0.
- Gradle assembleRelease 성공. release 앱 ID `local.todakgil.probe.submission`.
- aapt badging에 application-debuggable 없음. 별도 제출 키로 APK Signature Scheme v3 서명, apksigner verify 통과.
- Pleos 2.1.0 / API 34 / x86_64 / 1920×1080 환경 신규 패키지 설치 성공. 기존 검증 앱 데이터 보존.
- 첫 실행에서 별도 음원 push 없이 내장 합성 음원 6개 인식, 오디오 포커스 요청 성공.
- 45초 시험: intro → cloud → end. FINISHED 시 모의 남은 시간 9초. 종료 후 다음 이야기 시작 없음.
- 45초 시작 후 +25초: intro → cloud → tree → stream → end. FINISHED 시 모의 남은 시간 10초.
- 근거: release-short.log, release-extended.log. 로그 첫 숫자는 기기 elapsedRealtime 밀리초이며 실제 날짜가 아님.
- previous-real-route.log는 2026-11-11 이전 debug 시제품의 실제 240초 수신 기록이다. 이번 release 빌드에서 실제 경로를 새로 실행 검증했다는 뜻이 아니다.
- 시연 영상: 126.05초, 1920×1080, H.264/AAC. 화면 캡처 연결, 음성은 동일 파일을 재생 로그에 맞춰 후반 합성. 청취 품질·실차 주행 검증이 아님.

APK SHA-256: `8cf9e1b4e8d0410ad4ba69fca480b8e9f476c87698ef8cf7b2795ce439181d60`

가족 음원 수집, 차량 내 녹음, 자장가 모드, 실제 이동 ETA 변화·도착, 통화·주행 제한은 후속 개발·검증 범위다.
