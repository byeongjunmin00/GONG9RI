# 로그인 (auth/login)

대상: auth/login
담당: 민병준

## 배경 / 요구

`auth/signup`으로 만들어진 회원이 세션 기반으로 로그인한다. `docs/api/auth.md`의 `POST /api/auth/login` 계약대로 구현하고, signup 때 임시로 전체 `permitAll()` 해둔 `SecurityConfig`의 인가 규칙을 이번에 제대로 잡는다.

## 설계

- Spring Security 표준 인증 흐름: `AuthenticationManager`로 인증 → 성공 시 `SecurityContext`를 세션에 저장(`HttpSessionSecurityContextRepository`)
- 사용자 조회는 `UserDetailsService` 구현체로 처리(`common/security` 패키지, signup 때 만든 `common/response`, `common/exception`과 같은 결)
- `SecurityConfig`: `/api/auth/**`만 permitAll, 나머지는 인증 요구로 좁힘
- 아이디 없음/비밀번호 틀림을 구분하지 않고 `LOGIN_FAILED(401)`로 통일 (계정 존재 여부 비노출)
- 참고 계약: `docs/api/auth.md`, `docs/dev/auth/signup/design.md`(리스크 인계 부분)

## 태스크

- [ ] `MemberLoginRequest` DTO
- [ ] `MemberRepository.findByUsername` 추가
- [ ] `MemberUserDetails`(UserDetails 구현) + `MemberDetailsService`(UserDetailsService 구현)
- [ ] `SecurityConfig`: `AuthenticationManager` 빈, `SecurityContextRepository` 빈, 인가 규칙 좁히기
- [ ] `AuthController`에 `POST /api/auth/login` 추가
- [ ] `ErrorCode`에 `LOGIN_FAILED` 추가
- [ ] 테스트(정상로그인/존재안하는아이디/틀린비번/검증실패)

## 평가(통과) 기준

- 정상 로그인 시 `200` + `{success:true, data:{memberId, username, name, role}}` + 세션 쿠키(JSESSIONID) 발급 확인
- 존재하지 않는 아이디/틀린 비밀번호 시 둘 다 동일하게 `401` + `LOGIN_FAILED`
- 필수값 누락 시 `400` + `VALIDATION_FAILED`
- `./gradlew test` 통과
