# member (회원)

## 컬럼
| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, auto | 식별자 |
| member_code | VARCHAR(20) | NULL (백필 후 NOT NULL, UNIQUE 예정) | 회원번호(admin-identifier-codes, 2026-08-22 추가). `"M" + PK 7자리 zero-pad`(`M0000001`, `docs/policy/identifier-code.md`) — PK 파생, 별도 채번 테이블 없음. 가입/카카오 신규가입 직후 자동 채번. **지금은 nullable이다** — 이 컬럼이 생기기 이전 기존 회원은 `IdentifierCodeBackfillService` 백필 전까지 NULL이고, 4개 테이블 전부 백필이 끝나야 NOT NULL+UNIQUE 제약을 건다(`docs/deploy-guide.md`). Admin 회원 목록 화면·검색에 노출(공개 API에는 노출 안 함, 개인정보 성격) |
| username | VARCHAR(50) | NOT NULL, UNIQUE | 로그인 아이디 |
| password | VARCHAR(255) | NOT NULL | 비밀번호 (암호화 저장) |
| name | VARCHAR(50) | NOT NULL | 이름 |
| email | VARCHAR(100) | NOT NULL, UNIQUE | 이메일. UNIQUE는 로그인 고도화 2단계(비밀번호 재설정이 이메일로 계정을 유일하게 찾아야 함)에서 추가됨 — 기존에 있던 컬럼에 새로 건 제약이라 배포 시 별도 마이그레이션 확인 필요(아래 "마이그레이션 메모" 참고) |
| email_verified | BIT(1) | NOT NULL, DEFAULT `b'0'` | 이메일 인증 완료 여부(로그인 고도화 2단계, 2026-08-12 추가). false면 비밀번호가 맞아도 로그인 거절(`EMAIL_NOT_VERIFIED`) — `docs/dev/auth/email-verification/design.md`. 카카오 로그인 계정은 가입 시점부터 true(아래 `kakao_id` 참고) |
| kakao_id | VARCHAR(100) | NULL, UNIQUE | 연동된 카카오 계정 식별자(로그인 고도화 3단계, 2026-08-12 추가). 일반 회원가입 계정은 null. `docs/dev/auth/social-login/design.md` |
| role | VARCHAR(20) | NOT NULL | `BUYER` / `SELLER` / `ADMIN`(관리자, 2026-08-18 추가) — 가입 시 고정, 전환 기능 없음. 카카오 로그인 신규 가입은 항상 `BUYER`(스코프 밖). `ADMIN`은 공개 회원가입(`POST /api/auth/signup`)으로 절대 만들 수 없다(`MemberService.signup()` 가드) — 최초 계정은 배포 후 DB에 직접 심는다(`docs/dev/admin/design.md`) |
| suspended | BIT(1) | NOT NULL, DEFAULT `b'0'` | 관리자 계정 정지 여부(admin, 2026-08-18 추가). true면 비밀번호가 맞아도 로그인 거절(`ACCOUNT_SUSPENDED`) — `docs/dev/admin/design.md` |
| created_at | DATETIME | NOT NULL | 가입일 |
| updated_at | DATETIME | NOT NULL | 마지막 수정일 |

## 인덱스
- UNIQUE `username`
- UNIQUE `email` (2026-08-12 추가)
- UNIQUE `kakao_id` (2026-08-12 추가) — 브랜드 뉴 컬럼이라 `ddl-auto: update`가 UNIQUE까지 한 번에 자동 생성함을 실측 확인(아래 "마이그레이션 메모"의 `email` 케이스와 대비)

## 관계
- (참조하는 테이블 없음 — 다른 테이블이 `member.id`를 참조)

## 사용하는 기능
- auth/signup, auth/login, auth/logout, auth/email-verification, auth/password-reset, auth/social-login

## 마이그레이션 메모 (2026-08-12, `email` UNIQUE 인덱스)

`ddl-auto: update`는 새 컬럼(`email_verified`)은 안전하게 추가하지만(`DEFAULT b'0'`로 기존 row도 문제없음, 실측 확인함), **기존 컬럼에 새로 거는 UNIQUE 제약(`email`)은 자동으로 추가해주지 않는다** — 로컬 dev DB에서 `SHOW INDEX FROM member`로 실제로 확인함(2026-08-07 이전 스키마엔 `PRIMARY`/`username` UNIQUE만 있고 `email` 인덱스가 없었음). 프로덕션(Railway) DB에 이 인덱스를 실제로 걸 때는 `docs/deploy-guide.md`의 "이메일 UNIQUE 인덱스 수동 적용" 절차를 그대로 따를 것 — 적용 전에 반드시 중복 이메일이 있는지 먼저 확인해야 한다(있으면 `ALTER TABLE`이 실패함).

**대비되는 케이스(`kakao_id`, 같은 날 실측)**: `kakao_id`는 **브랜드 뉴 컬럼**이라(`email`처럼 기존 컬럼에 나중에 제약을 추가한 게 아니라 컬럼 자체가 이번에 처음 생김) `ddl-auto: update`가 컬럼 생성과 UNIQUE 인덱스 생성을 한 번에 처리해준다는 걸 확인함(`kakao_id varchar(100) YES UNI`). 즉 "기존 컬럼에 제약을 나중에 추가"할 때만 수동 개입이 필요하고, "처음부터 제약이 있는 새 컬럼"은 `ddl-auto: update`만으로 충분하다.

## 삭제 정책
- 회원 탈퇴(본인 요청) 기능은 여전히 없음.
- 관리자 삭제(admin, 2026-08-18 추가)는 있지만 조건부다 — Product/Payment/Review/GroupBuyTeam/
  TeamParticipation/Wishlist/Inquiry/RefundRequest/ChatSession 전부에 이 회원을 참조하는 행이
  하나도 없을 때만 하드 삭제를 허용한다(`AdminService.hasActivity()`). 하나라도 있으면 정지
  (`suspended`)로만 처리한다 — `docs/dev/admin/design.md`.
