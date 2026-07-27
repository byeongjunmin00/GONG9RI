# member (회원)

## 컬럼
| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, auto | 식별자 |
| username | VARCHAR(50) | NOT NULL, UNIQUE | 로그인 아이디 |
| password | VARCHAR(255) | NOT NULL | 비밀번호 (암호화 저장) |
| name | VARCHAR(50) | NOT NULL | 이름 |
| email | VARCHAR(100) | NOT NULL | 이메일 |
| role | VARCHAR(20) | NOT NULL | `BUYER` / `SELLER` — 가입 시 고정, 전환 기능 없음 |
| created_at | DATETIME | NOT NULL | 가입일 |
| updated_at | DATETIME | NOT NULL | 마지막 수정일 |

## 인덱스
- UNIQUE `username`

## 관계
- (참조하는 테이블 없음 — 다른 테이블이 `member.id`를 참조)

## 사용하는 기능
- auth/signup, auth/login, auth/logout

## 삭제 정책
- 하드 삭제 없음 (탈퇴 기능 자체가 이번 스코프에 없음)
