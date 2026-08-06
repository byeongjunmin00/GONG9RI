# 001-request-tracing — 도메인 로그 + 액세스 로그 + 요청 추적(MDC) (로그)

## Attempt 1 — 2026-08-06

- 시도:
  - `common/filter/RequestLoggingFilter.java` 신설 — `OncePerRequestFilter` + `@Order(HIGHEST_PRECEDENCE)`로 Spring Security 필터체인보다 먼저 실행. 요청마다 UUID 8자 `traceId`를 MDC에 심고, 처리 완료 후 `method/URI/상태코드/소요시간(ms)`을 INFO로 남기고 MDC 정리.
  - `application.yaml`에 `logging.pattern.console`을 추가해 `[%X{traceId}]`가 콘솔 로그에 노출되게 함(logback-spring.xml 신설 없이 경량 접근).
  - `ErrorCode.INTERNAL_SERVER_ERROR` 추가 + `GlobalExceptionHandler`에 `Exception.class` catch-all 핸들러 추가(`log.error` + 500 응답) — 기존엔 `BusinessException`/`MethodArgumentNotValidException`만 잡고 있어 예상 못한 예외에 대한 ERROR 로그가 없었음.
  - `AuthController.login`에 로그인 성공(INFO, memberId/username) / 실패(WARN, username) 로그 추가 — 기존엔 로그인 이벤트 자체에 로그가 하나도 없었음.
  - 그 외 회원가입/상품 등록·수정·삭제/팀 신설·참가/마감 처리는 기존에 이미 code-convention.md 레벨 기준대로 로그가 들어가 있어 손대지 않음.

- 결과:
  - `./gradlew compileJava` → **성공**.
  - `./gradlew test` → 83개 중 80개 실패, 전부 `IllegalStateException` → `HibernateException at DialectFactoryImpl`(JDBC 메타데이터로 Dialect 확인 불가) 동일 패턴.
- 원인: **로직 문제 아님.** 로컬 MySQL(3306)·Redis(6379)에 TCP 연결 자체가 안 됨(`Test-NetConnection` 확인 결과 둘 다 실패) — DB/Redis 미가동 상태에서 스프링 컨텍스트 로딩이 실패한 것. 이번 변경(필터·로그 추가)과 무관.
- 증거: `compileJava` 성공 로그, `Test-NetConnection -Port 3306/6379` 결과 둘 다 `False`.
- 다음: 로컬 MySQL·Redis를 기동한 뒤 `./gradlew test` 재실행 필요. 또한 `bootRun` 후 실제 API 호출(로그인 등)로 콘솔에 `[traceId]` 포함 로그가 찍히는지, 동시 요청 시 traceId가 분리되는지 사용자 직접 확인 필요(evaluate-guide.md 기준).

## Attempt 1 후속 — 2026-08-06 ✅ PASS

- Docker(`gong9ri-mysql`, `gong9ri-redis` 컨테이너)로 MySQL·Redis 기동 후 재검증.
- 결과:
  - `./gradlew test` → **BUILD SUCCESSFUL** (전체 통과, 회귀 없음).
  - `bootRun` 후 실제 API 호출로 콘솔 로그 확인:
    - `GET /api/products` → `200`, traceId `fb6b7622` — 액세스 로그 정상.
    - `POST /api/auth/login`(존재하지 않는 계정) → `401 LOGIN_FAILED`, traceId `626d0349` — `AuthController`의 WARN 로그, `GlobalExceptionHandler`의 WARN 로그, `RequestLoggingFilter`의 액세스 로그 **세 줄 모두 같은 traceId**로 연결됨.
    - `POST /api/auth/signup`(요청 body 인코딩 문제로 우연히 발생한 역직렬화 예외) → `500 INTERNAL_SERVER_ERROR`, traceId `3ac3725a` — 신설한 catch-all `Exception` 핸들러가 `ERROR` 레벨로 스택트레이스를 남기고 500 응답을 반환함을 확인(의도된 시나리오는 아니었지만 신규 핸들러 동작 자체를 검증하는 계기가 됨).
    - `POST /api/auth/signup` → `POST /api/auth/login`(정상 계정 `logtest2`, memberId=711) → `200`, traceId `7489053b` — 로그인 성공 INFO 로그(`memberId=711, username=logtest2`)와 액세스 로그가 같은 traceId로 연결됨.
    - 서로 다른 요청은 매번 다른 traceId를 받았고(겹침 없음), 각 요청 내부의 여러 로그 줄은 모두 같은 traceId로 묶임 — 요청 추적(MDC) 목표 달성.
  - 사용 후 컨테이너는 유지, `bootRun` 프로세스는 검증 후 종료함.
- 평가(evaluate-guide.md 기준): 계산적 평가(테스트 전체 통과) + 추론적 평가(계획·code-convention.md 로그 레벨 기준 준수) 모두 통과.

## 리뷰 중 발견·수정 — 2026-08-06 (민병준)

- pull 받아 리뷰하던 중, 위 27번 줄에서 팀원이 "우연히 발생한 역직렬화 예외 → 500"이라고 남긴 부분이 의도된 동작인지 문제인지 판단 안 하고 넘어간 걸 발견 — 직접 확인 필요하다고 판단.
- `curl`로 `/api/auth/signup`에 문법이 깨진 JSON을 보내서 실제로 재현: `500 INTERNAL_SERVER_ERROR` 확인(정상은 `400`).
- 원인: 신규 `@ExceptionHandler(Exception.class)` catch-all이 `HttpMessageNotReadableException`(요청 본문 파싱 실패, 클라이언트 입력 오류)까지 붙잡아버림 — 이 핸들러가 생기기 전에는 스프링 기본 예외 처리(`DefaultHandlerExceptionResolver`)가 이 예외를 자동으로 `400`으로 매핑해줬는데, `ExceptionHandlerExceptionResolver`가 우선순위가 더 높아서 `Exception.class` 핸들러가 먼저 잡아버리는 것.
- 수정: `HttpMessageNotReadableException` 전용 핸들러를 `Exception.class`보다 먼저 추가해 `400 VALIDATION_FAILED`로 응답. 이번엔 발견된 이 케이스만 좁게 고침(비슷한 다른 표준 예외(잘못된 경로변수 타입, 지원 안 하는 HTTP 메서드 등)도 같은 문제가 있을 수 있으나 이번 스코프 밖 — 다음에 발견되면 같은 패턴으로 추가).
- 회귀 테스트 추가: `AuthControllerTest.signup_malformedJson_returnsBadRequestNotServerError` — 문법 깨진 JSON → `400 VALIDATION_FAILED` 확인.
- 검증: `./gradlew build` 전체 84케이스(기존 83 + 신규 1) 전부 통과.
