# 코드 컨벤션 (code-convention)

코드를 작성·수정할 때 이 규칙을 따른다. (Spring Boot / Java 기준)
세부는 이 프로젝트에 맞게 조정한다.

## 패키지 · 계층 구조

- 루트 패키지: `com.gong9ri.gong9ri`
- 계층 분리: `controller` / `service` / `repository` / `entity` / `dto`
- 각 계층의 책임을 지킨다:
  - **controller**: 요청/응답만. 비즈니스 로직 금지 → service로 위임.
  - **service**: 비즈니스 로직, 트랜잭션 경계.
  - **repository**: 데이터 접근 (`JpaRepository`).
  - **entity**: JPA 엔티티. 컨트롤러 응답으로 **직접 노출 금지**.
  - **dto**: 요청/응답 전용 객체.

## 의존성 주입

- **생성자 주입**을 사용한다. 필드 `@Autowired` **금지**.
- 가능하면 필드는 `final`로 둔다.

## 트랜잭션

- 서비스 클래스에 `@Transactional(readOnly = true)`를 기본으로 두고,
  쓰기 메서드에만 `@Transactional`을 명시한다.

## 웹 · 검증

- REST 컨트롤러는 `@RestController`, 매핑은 `/api/...`.
- 요청 DTO에 Bean Validation(`@Valid` + 제약 애노테이션)을 적용한다.
- 예외는 `@RestControllerAdvice`로 일관 처리한다.
- 적절한 HTTP 상태코드 사용 (생성 `201`, 조회 `200`, 검증 실패 `400`, 정원 초과 등 충돌은 `409`).

## 동시성

- `group_buy_team.current_count`처럼 **여러 요청이 동시에 갱신할 수 있는 컬럼**을 건드리는 로직은
  일반 서비스 메서드와 구분해서 표시하고(주석 또는 별도 메서드명), 락 전략(비관적/낙관적/분산락 등)을 명확히 남긴다.
- 관련 정책은 `docs/policy/`, 계획 단계 결정 경위는 해당 기능 `docs/dev/{개념}/{기능}/design.md` 참고.

## 로깅

- `System.out.println`, `e.printStackTrace()` 금지. SLF4J(`@Slf4j`, Spring Boot 기본 내장 — Logback)만 사용한다.
- 로그 레벨 기준:
  | 레벨 | 용도 | 예 |
  |------|------|-----|
  | ERROR | 서버가 처리 못 하는 예외 | DB 연결 실패, 예상 못 한 런타임 예외 |
  | WARN | 복구 가능한 이상 상황 | 정원 초과 참가 시도(`TEAM_FULL`), 중복 참가 시도 |
  | INFO | 주요 도메인 이벤트 | 회원가입, 결제 생성, 팀 성사(`SUCCESS`) 전환 |
  | DEBUG | 개발 중 상세 추적 | 운영 환경에서는 끔 |
- 예외는 `@RestControllerAdvice`에서 잡을 때 반드시 로그로 남기고 그냥 삼키지 않는다.
- 로그 메시지에 도메인 식별자(`memberId`, `teamId`, `productId` 등)를 포함한다 — 검색·필터링 가능하게.

## 스타일

- 들여쓰기: **탭** 또는 **스페이스 4칸** — 팀 합의된 쪽으로 통일 (IDE 자동 포맷 기준 따름).
- 네이밍: 클래스 `PascalCase`, 메서드/필드 `camelCase`, 상수 `UPPER_SNAKE_CASE`.
- 매직 넘버/문자열 지양 → 상수화.

## 현재 상태 메모

- **Lombok 도입됨** (`build.gradle`에 `lombok` 의존성 포함) — getter/setter/생성자 보일러플레이트는 Lombok 애노테이션으로 줄인다.
- **Bean Validation 도입됨** (`spring-boot-starter-validation`).
- **Actuator 도입됨** (`spring-boot-starter-actuator`) — `/actuator/health` 등 모니터링 엔드포인트 기본 제공.
- DB는 **MySQL** (`mysql-connector-j`).
- 정적 분석(Checkstyle 등) 린터는 아직 미설정 — 도입되면 위 규칙 일부가 자동 강제된다.
