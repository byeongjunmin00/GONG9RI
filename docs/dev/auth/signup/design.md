# 회원가입 (auth/signup) — Design

## 개요

구매자/판매자 공용 회원가입. 아이디 중복 체크 후 비밀번호를 암호화해서 저장하고, 가입된 회원 정보(비밀번호 제외)를 반환한다. `member` 테이블은 product/team/payment 등 다른 모든 도메인의 FK 기준점이라, 이후 모든 기능이 이 위에서 동작한다.

이번 기능에서 이후 모든 API가 공용으로 쓰는 공통 인프라(응답 wrapper, 전역 예외 처리, 비밀번호 암호화용 `PasswordEncoder`)도 같이 만들어졌다.

## API / 인터페이스

- `POST /api/auth/signup` — 상세: `docs/api/auth.md`
- 공통 응답 형식(`{success, data}` / `{success, code, message}`): `docs/api/README.md`, 구현체 `common/response/ApiResponse.java`

## 데이터 모델

- `member` 테이블 — 상세: `docs/db/member.md`, 엔티티 `entity/Member.java`
- `created_at`/`updated_at`은 Spring Data JPA Auditing(`@EnableJpaAuditing`, `Gong9riApplication.java`)으로 자동 관리

## 규칙 / 검증

- `username` 중복 시 `409 DUPLICATE_USERNAME` (`MemberRepository.existsByUsername`)
- 필수값 누락/형식 오류 시 `400 VALIDATION_FAILED` (Bean Validation, `GlobalExceptionHandler`가 `MethodArgumentNotValidException` 처리)
- 비밀번호는 `PasswordEncoder`(BCrypt)로 암호화해서 저장 (`docs/db/member.md` "암호화 저장" 요구사항)
- 인증 방식은 세션 기반(`docs/api/auth.md`)이지만, 이 기능(가입)만으로는 세션을 발급하지 않는다 — 세션 발급은 `auth/login`에서 처리 예정

## 관련 코드 위치

- `entity/Member.java`, `entity/Role.java`
- `dto/MemberSignupRequest.java`, `dto/MemberResponse.java`
- `repository/MemberRepository.java`
- `service/MemberService.java`
- `controller/AuthController.java`
- `common/response/ApiResponse.java`
- `common/exception/{ErrorCode,BusinessException,GlobalExceptionHandler}.java`
- `config/SecurityConfig.java` — **임시 상태**: 로그인/세션 기능이 아직 없어 전체 `permitAll()`. `auth/login` 구현 시 인가 규칙을 다시 잡아야 함(다음 기능의 리스크로 인계)
- 테스트: `src/test/.../controller/AuthControllerTest.java`
