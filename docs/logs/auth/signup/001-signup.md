# 001-signup — 회원가입 (로그)

## Attempt 1 — 2026-07-31  ❌ FAIL
- 시도: `Member` 엔티티, DTO, `MemberRepository`, `MemberService`, `AuthController`, 공통 응답 wrapper(`ApiResponse`), 전역 예외 처리(`GlobalExceptionHandler`), `SecurityConfig`(비밀번호 암호화용) 구현. 테스트는 `com.fasterxml.jackson.databind.ObjectMapper`, `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc`(표준 Spring Boot 패키지) 기준으로 작성.
- 결과: `./gradlew test` 컴파일 실패. `compileTestJava`에서 4개 에러 — 위 두 클래스를 찾을 수 없음.
- 원인: 이 프로젝트의 Spring Boot 4.1.0 + Jackson 3.x 조합에서 패키지가 이동됨 — 확인해보니(`./gradlew dependencies`, jar 내부 조회로 실제 클래스 위치 확인):
  - Jackson databind(`ObjectMapper` 등)가 `com.fasterxml.jackson.databind` → `tools.jackson.databind`로 이동 (Jackson 3.x). 단 `jackson-annotations`(`@JsonInclude` 등)는 여전히 `com.fasterxml.jackson.annotation`.
  - `@AutoConfigureMockMvc`가 `org.springframework.boot.test.autoconfigure.web.servlet` → `org.springframework.boot.webmvc.test.autoconfigure`로 이동.
- 다음: import 경로만 수정(같은 접근으로 고칠 수 있는 실패 → Generate 루프로 재시도, Plan 회귀 불필요)

## Attempt 2 — 2026-07-31  ✅ PASS
- 시도: 테스트 import를 `tools.jackson.databind.ObjectMapper`, `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`로 수정.
- 결과: `./gradlew build` 전체 통과 (compileJava/test/assemble 포함). `AuthControllerTest` 3케이스 + 기존 `Gong9riApplicationTests` 1케이스 전부 성공.
- 증거(API 샘플, MockMvc):
  - `POST /api/auth/signup` (정상) → `201 {"success":true,"data":{"memberId":..,"username":"gonguri1","name":"홍길동","role":"BUYER"}}` (password 필드 없음 확인)
  - `POST /api/auth/signup` (username 중복) → `409 {"success":false,"code":"DUPLICATE_USERNAME","message":"이미 존재하는 아이디입니다."}`
  - `POST /api/auth/signup` (username 빈 값) → `400 {"success":false,"code":"VALIDATION_FAILED","message":"..."}`
- DB 증거: 로컬 MySQL `member` 테이블 스키마가 `docs/db/member.md`와 일치(`DESCRIBE member`로 확인). 저장된 비밀번호가 평문("password123")과 다름을 테스트에서 assert(BCrypt 암호화 확인). 테스트가 `@Transactional`이라 롤백되어 실행 후 `member` 테이블 row 0건 확인(DB 오염 없음).
