# 로깅/요청 추적 — Design

## 개요

모든 API 요청에 대해 요청 단위 traceId를 발급해 MDC에 심고, 요청 처리 완료 후 액세스 로그(메서드/URI/상태코드/소요시간)를 남긴다. 같은 요청 안에서 발생하는 도메인 이벤트 로그·예외 로그도 같은 traceId로 태깅되어, 콘솔 로그를 traceId로 grep하면 해당 요청의 전체 흐름을 추적할 수 있다.

도메인 이벤트(회원가입, 로그인 성공/실패, 상품 등록·수정·삭제, 공구팀 신설·참가, 마감 처리, 결제 생성)와 예상 못한 예외는 `docs/code-convention.md`의 로그 레벨 기준(ERROR/WARN/INFO/DEBUG)에 따라 각 서비스·전역 예외 처리기에서 SLF4J로 남긴다.

## API / 인터페이스

- 별도 엔드포인트 없음 — 기존 모든 `/api/**` 요청에 공통으로 적용되는 횡단 관심사.

## 데이터 모델

- 사용하는 테이블 없음 (로그는 콘솔 출력, DB에 별도 저장하지 않음).

## 구성 요소

- `common/filter/RequestLoggingFilter` — `OncePerRequestFilter`, `@Order(Ordered.HIGHEST_PRECEDENCE)`로 Spring Security의 `FilterChainProxy`(order -100)보다 먼저 실행되어 인증 실패(401/403) 응답도 추적 범위에 포함된다. 요청 시작 시 UUID 앞 8자를 `traceId`로 MDC에 저장하고, `finally`에서 액세스 로그를 INFO로 남긴 뒤 MDC를 정리한다.
- `application.yaml`의 `logging.pattern.console`에 `[%X{traceId}]`를 추가해 콘솔 로그 전체에 traceId가 노출되도록 함(별도 `logback-spring.xml` 없음 — 파일 출력/롤링/프로필 분리는 아직 범위 밖, 필요 시 별도 Plan).
- `common/exception/GlobalExceptionHandler`에 `Exception.class` catch-all 핸들러를 추가해, `BusinessException`·검증 실패 이외의 예상 못한 예외를 ERROR 레벨로 스택트레이스와 함께 기록하고 `500 INTERNAL_SERVER_ERROR`로 응답한다(`common/exception/ErrorCode.INTERNAL_SERVER_ERROR` 추가).
  - **리뷰 중 발견·수정(2026-08-06)**: 이 catch-all이 `HttpMessageNotReadableException`(잘못된 JSON 등 요청 본문 파싱 실패 — 클라이언트 입력 오류)까지 가로채 500으로 응답하는 회귀가 있었음(원래는 스프링 기본 처리로 400이 나가야 함). `HttpMessageNotReadableException` 전용 핸들러를 `Exception.class`보다 먼저 추가해 `400 VALIDATION_FAILED`로 응답하도록 수정, 회귀 테스트(`AuthControllerTest.signup_malformedJson_returnsBadRequestNotServerError`) 추가.
- `controller/AuthController.login`에 로그인 성공(INFO, memberId/username)·실패(WARN, username) 로그를 추가함. 그 외 도메인 서비스(회원가입, 상품, 팀, 결제, 마감 스케줄러)는 기존에 이미 컨벤션에 맞는 로그가 있어 이번 작업에서 추가 변경 없음.

## 규칙 / 검증

- 로그 레벨 기준·SLF4J 사용·도메인 식별자 포함 규칙은 `docs/code-convention.md`를 따른다.
- MDC는 스레드로컬 기반이라, 향후 `@Async` 등 별도 스레드로 요청을 넘기는 로직이 추가되면 traceId가 끊길 수 있음 — 현재는 해당 로직 없음.
- 로그 파일 출력·롤링·dev/prod 프로필 분리는 이번 범위 밖(배포 파이프라인이 생기면 별도 Plan에서 다룸).
