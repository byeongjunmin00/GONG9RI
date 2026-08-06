# 로깅 체계 도입 — 도메인 로그 + 액세스 로그 + 요청 추적(MDC)

대상: logging/request-tracing
담당: 전용운

## 배경 / 요구

- 현재 `spring-boot-starter-actuator`만 도입된 상태, 실제 로그 코드는 아직 없음.
- `docs/code-convention.md`에 로그 레벨 기준(ERROR/WARN/INFO/DEBUG)과 SLF4J 사용 규칙은 이미 정의돼 있으나 적용된 곳이 없음.
- 목표(범위 1+2+4로 합의):
  1. 도메인 이벤트 로그 — 주요 서비스 흐름에 컨벤션대로 로그 채우기
  2. 요청 단위 액세스 로그 — 모든 API 요청의 메서드/URI/상태코드/소요시간 자동 기록
  3. 요청 추적(correlation id) — MDC 기반 traceId로 동시 요청 상황에서도 로그 라인을 요청 단위로 구분 가능하게
- (로그 포맷/파일 출력/롤링 등 logback 인프라 설정은 이번 범위 아님 — 배포 파이프라인이 생기면 별도 Plan으로 다룸)

## 설계 (접근 방향)

- **요청 추적/액세스 로그**: 서블릿 필터(또는 인터셉터) 하나를 신설해 모든 요청에 공통 적용. 시작 시 UUID 기반 traceId를 발급해 MDC에 저장하고, 응답 완료 후 액세스 로그(메서드/URI/상태코드/소요시간)를 남긴 뒤 MDC를 정리한다. (Filter vs Interceptor 선택, 클래스 배치는 Generate 몫)
- **traceId 노출**: 새 `logback-spring.xml`을 만들지 않고, `application.yml`의 로그 패턴 속성(`logging.pattern.level` 등)만 조정해 `%X{traceId}`가 콘솔 로그에 보이게 한다 (경량 접근 — 파일 출력/롤링/프로필 분리는 이번 범위 아님).
- **도메인 이벤트 로그**: 기존 auth/product/team/payment 서비스 계층에 code-convention.md 레벨 기준대로 로그를 삽입한다. WARN 대상 예: `TEAM_FULL`, `ALREADY_JOINED`, 로그인 실패. INFO 대상 예: 회원가입/로그인 성공, 결제 생성, 팀 성사(`SUCCESS`) 전환. ERROR는 `GlobalExceptionHandler`류에서 예상 못한 예외를 잡을 때.
- 모든 로그에 도메인 식별자(`memberId`/`teamId`/`productId` 등) 포함 (컨벤션 규칙).

## 리스크 / 전제

- MDC는 스레드로컬 기반 — 현재 스택에 별도 비동기(`@Async`) 처리가 없는지 확인 필요(있다면 traceId가 끊길 위험).
- 액세스 로그가 매 요청마다 쌓여 로그량이 늘어남 — 파일 관리/롤링은 이번 범위 밖(후속 과제).
- 여러 도메인 서비스 파일을 두루 건드리므로 변경 파일 수는 많지만, 로그 추가는 기존 로직에 side-effect 없음.
- 동시 참가 등 동시성 테스트 시 traceId가 요청별로 겹치지 않고 분리되는지가 이번 작업의 핵심 검증 포인트.

## 태스크

- [ ] 요청 추적용 Filter/Interceptor 신설 (traceId 발급 → MDC 저장 → 응답 후 정리)
- [ ] 같은 컴포넌트에서 액세스 로그(메서드/URI/상태코드/소요시간) INFO 레벨로 기록
- [ ] `application.yml` 로그 패턴에 `%X{traceId}` 노출 추가
- [ ] auth(signup/login/logout) 서비스 도메인 로그 추가
- [ ] product(등록/수정/삭제) 서비스 도메인 로그 추가
- [ ] team(신설/참가/deadline-check 스케줄러) 서비스 도메인 로그 추가
- [ ] payment(생성/환불) 서비스 도메인 로그 추가
- [ ] 전역 예외 처리기(`@RestControllerAdvice`)에 ERROR 로그 보강
- [ ] 로그 메시지에 도메인 식별자 포함 여부 점검

## 평가(통과) 기준

- `./gradlew compileJava` / `./gradlew test` 회귀 없음
- `bootRun` 후 실제 API 호출 시 콘솔 로그에 `[traceId]`가 포함되어 출력됨 (사용자 직접 확인)
- 동시 요청(팀 참가 등) 시 각 요청의 traceId가 서로 분리되어 있음
- code-convention.md 로그 레벨 기준 준수
