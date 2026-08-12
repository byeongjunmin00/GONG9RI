# member (회원)

## 컬럼
| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, auto | 식별자 |
| username | VARCHAR(50) | NOT NULL, UNIQUE | 로그인 아이디 |
| password | VARCHAR(255) | NOT NULL | 비밀번호 (암호화 저장) |
| name | VARCHAR(50) | NOT NULL | 이름 |
| email | VARCHAR(100) | NOT NULL, UNIQUE | 이메일. UNIQUE는 로그인 고도화 2단계(비밀번호 재설정이 이메일로 계정을 유일하게 찾아야 함)에서 추가됨 — 기존에 있던 컬럼에 새로 건 제약이라 배포 시 별도 마이그레이션 확인 필요(아래 "마이그레이션 메모" 참고) |
| email_verified | BIT(1) | NOT NULL, DEFAULT `b'0'` | 이메일 인증 완료 여부(로그인 고도화 2단계, 2026-08-12 추가). false면 비밀번호가 맞아도 로그인 거절(`EMAIL_NOT_VERIFIED`) — `docs/dev/auth/email-verification/design.md` |
| role | VARCHAR(20) | NOT NULL | `BUYER` / `SELLER` — 가입 시 고정, 전환 기능 없음 |
| created_at | DATETIME | NOT NULL | 가입일 |
| updated_at | DATETIME | NOT NULL | 마지막 수정일 |

## 인덱스
- UNIQUE `username`
- UNIQUE `email` (2026-08-12 추가)

## 관계
- (참조하는 테이블 없음 — 다른 테이블이 `member.id`를 참조)

## 사용하는 기능
- auth/signup, auth/login, auth/logout, auth/email-verification, auth/password-reset

## 마이그레이션 메모 (2026-08-12, `email` UNIQUE 인덱스)

`ddl-auto: update`는 새 컬럼(`email_verified`)은 안전하게 추가하지만(`DEFAULT b'0'`로 기존 row도 문제없음, 실측 확인함), **기존 컬럼에 새로 거는 UNIQUE 제약(`email`)은 자동으로 추가해주지 않는다** — 로컬 dev DB에서 `SHOW INDEX FROM member`로 실제로 확인함(2026-08-07 이전 스키마엔 `PRIMARY`/`username` UNIQUE만 있고 `email` 인덱스가 없었음). 프로덕션(Railway) DB에 이 인덱스를 실제로 걸 때는 `docs/deploy-guide.md`의 "이메일 UNIQUE 인덱스 수동 적용" 절차를 그대로 따를 것 — 적용 전에 반드시 중복 이메일이 있는지 먼저 확인해야 한다(있으면 `ALTER TABLE`이 실패함).

## 삭제 정책
- 하드 삭제 없음 (탈퇴 기능 자체가 이번 스코프에 없음)
