# 회원가입 (auth/signup)

대상: auth/signup
담당: 민병준

## 배경 / 요구

GONG9RI의 첫 구현 기능. `member` 테이블이 product/team/payment 등 다른 모든 테이블의 FK 기준점이라, 이게 없으면 다른 기능을 테스트할 수 없다. `docs/api/auth.md`의 `POST /api/auth/signup` 계약대로 구현한다.

이번 작업은 프로젝트 최초 기능이라, signup 자체 외에 이후 모든 기능이 재사용할 공통 인프라(응답 wrapper, 전역 예외 처리)도 같이 만든다.

## 설계

- 계층: `entity`(`Member`) / `dto`(요청·응답) / `repository`(`MemberRepository`) / `service`(`MemberService`) / `controller`(`AuthController`)
- 회원가입 흐름: username 중복 체크 → 비밀번호 암호화 → 저장 → 응답(password 제외)
- 공통 응답 wrapper(`{success, data}` / `{success, code, message}`)와 전역 예외 처리(`@RestControllerAdvice`)를 이번에 처음 만들고, 이후 기능이 재사용
- 참고 계약: `docs/api/auth.md`, `docs/db/member.md`, `docs/api/README.md`(공통 응답 형식), `docs/code-convention.md`

## 태스크

- [ ] `Member` 엔티티
- [ ] 회원가입 요청/응답 DTO
- [ ] `MemberRepository` (username 중복 체크)
- [ ] `MemberService` (가입 로직)
- [ ] `AuthController` (`POST /api/auth/signup`)
- [ ] 공통 응답 wrapper
- [ ] 전역 예외 처리(`@RestControllerAdvice`) + `DUPLICATE_USERNAME`/`VALIDATION_FAILED` 매핑
- [ ] 테스트(정상가입/중복아이디/검증실패)

## 평가(통과) 기준

- 정상 가입 시 `201` + `{success:true, data:{memberId, username, name, role}}` (password 미노출)
- username 중복 시 `409` + `DUPLICATE_USERNAME`
- 필수값 누락/형식 오류 시 `400` + `VALIDATION_FAILED`
- DB에 비밀번호 평문 저장 안 됨
- `./gradlew test` 통과
