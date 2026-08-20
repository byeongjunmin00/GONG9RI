# 001-timezone-seoul — 애플리케이션 시간대 고정 (로그)

## Attempt 1 — 2026-08-20  ✅ PASS
- 시도: `TimeZoneConfig`의 `@PostConstruct`에서 기본 시간대를 `Asia/Seoul`로 고정.
- 원인: Railway 컨테이너 기본 시간대가 UTC라 `LocalDateTime.now()`가 UTC 값을 만들었고, 응답에
  시간대 표시가 없어 브라우저가 로컬 시각으로 해석 → 9시간 어긋남. **`openAt` 판정 로직도 함께 틀림.**
- 결과: `./gradlew test` 전체 **409케이스 통과**.
- 증거(실측):
  ```
  실제:  KST 2026-08-20 21:30 / UTC 12:30
  API:   GET /api/products?sort=LATEST → createdAt=2026-08-20T11:35:34   ← UTC
  DB:    시간 컬럼 전부 DATETIME, TIMESTAMP 0개 (드라이버 변환 없음 확인)
  ```
- 역검증: `TZ=UTC` 환경에서 `TimeZoneConfig`를 제거하면 `TimeZoneConfigTest` **FAIL**, 복구하면 **PASS**.
  CI 러너가 UTC라 "설정을 배포 환경에만 넣고 코드엔 없는 상태"를 이 테스트가 잡아낸다.
- 미보정 항목(정직하게 남김): 이 변경 이전에 UTC로 저장된 기존 행들은 앞으로 KST로 해석돼 9시간 이르게
  표시된다(기계 생성 값 한정 — 사람이 입력한 `openAt`은 오히려 의도대로 교정됨). 테이블 여럿을 건드리는
  일괄 보정은 위험 대비 이득이 표시상 정정뿐이라 하지 않았다.

## Attempt 2 — 2026-08-20  ✅ PASS (Attempt 1의 판단 정정)
- 시도: 로컬 실서버로 저장·조회 왕복을 실측해 Attempt 1의 전제("DATETIME이라 드라이버 변환 없음")를 검증.
- 결과: **전제가 틀렸다.** 실제로는 JDBC URL의 `serverTimezone=UTC` 때문에 드라이버가 양방향 변환한다.
- 증거(실측):
  ```
  지금 KST         : 2026-08-20T21:37:44
  API 응답 createdAt: 2026-08-20T21:37:45   ← KST, 현재 시각과 일치 ✅
  DB 실제 저장값     : 2026-08-20 12:37:45   ← UTC
  ```
  즉 `앱(KST) → DB(UTC) → API(KST)`로 왕복이 일치한다. 저장은 시간대 중립, 표현은 서비스 시간대로
  분리되는 형태라 오히려 바람직하다.
- 영향 정정: Attempt 1에서 "기존 행은 9시간 이르게 표시된다"고 적었으나 **틀렸다.** 기존 행도 UTC로
  저장돼 있어 읽을 때 KST로 변환되므로 **함께 교정된다.** 데이터 보정 불필요.
