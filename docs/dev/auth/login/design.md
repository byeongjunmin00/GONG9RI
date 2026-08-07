# 로그인 (auth/login) — Design

## 개요

`member`의 username/password로 인증하고, 세션 기반으로 로그인을 유지한다. 아이디 없음/비밀번호 틀림을 구분하지 않고 `LOGIN_FAILED`로 통일 응답해 계정 존재 여부를 노출하지 않는다. 이번 기능으로 `SecurityConfig`가 `/api/auth/**`만 인증 없이 열어두고 나머지는 인증을 요구하도록 좁혀졌다.

## API / 인터페이스

- `POST /api/auth/login` — 상세: `docs/api/auth.md`. 응답은 `signup`과 동일한 `MemberResponse` 형태 재사용.

## 데이터 모델

- 추가 테이블 없음. `member` 재사용(`docs/db/member.md`).

## 규칙 / 검증

- 인증 흐름: `AuthenticationManager.authenticate()` → 성공 시 `SecurityContext`를 `SecurityContextRepository`(`HttpSessionSecurityContextRepository`)로 세션에 저장
- 사용자 조회: `MemberDetailsService`(`UserDetailsService` 구현)가 `MemberRepository.findByUsername`으로 조회, 없으면 `UsernameNotFoundException` → Spring Security가 인증 실패로 처리
- 비밀번호 검증: Spring Security가 자동 구성하는 `DaoAuthenticationProvider`가 `MemberDetailsService` + signup 때 만든 `PasswordEncoder`(BCrypt)로 처리 — 별도 구현 불필요
- 인증 실패(아이디 없음/비번 틀림 모두) → `AuthController`가 `AuthenticationException`을 잡아 `BusinessException(LOGIN_FAILED)`로 변환, `401` 응답
- `SecurityConfig`: `POST /api/auth/signup`, `POST /api/auth/login`만 permitAll, 나머지 `anyRequest().authenticated()` — 인증이 필요한 `/api/auth/logout`은 `docs/dev/auth/logout/design.md` 참고. **product/team 등 새 컨트롤러가 생기면 이 규칙을 다시 검토해야 함** (다음 기능들의 리스크로 인계)

## 관련 코드 위치

- `dto/MemberLoginRequest.java`
- `repository/MemberRepository.java` — `findByUsername` 추가됨
- `common/security/{MemberUserDetails,MemberDetailsService}.java`
- `config/SecurityConfig.java` — `AuthenticationManager`/`SecurityContextRepository` 빈, 인가 규칙
- `controller/AuthController.java` — `login()` 메서드
- `common/exception/ErrorCode.java` — `LOGIN_FAILED` 추가
- 테스트: `src/test/.../controller/AuthControllerTest.java` (signup 테스트와 같은 클래스에 이어서 작성)
