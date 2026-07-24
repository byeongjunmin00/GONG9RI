# db — 테이블(스키마) 명세

데이터베이스 **테이블 스키마의 진실의 원천(SSOT)**이다.
여러 기능이 같은 테이블을 공유하므로(예: `group_buy_team` 테이블은 신설·참가·조회가 다 씀),
각 `design.md`에 흩어 적지 않고 **여기 한 곳**에 두고 참조한다. (정책과 같은 패턴)

> 전체 테이블 개요/관계는 `docs/ERD.md`(초안)를 먼저 본다. 이 폴더는 테이블별 **상세 컬럼 명세**를 담는다.

## 위치 & 이름

```
docs/db/
├── README.md
└── {테이블}.md          예: group_buy_team.md
```

## 테이블 문서 템플릿

```markdown
# group_buy_team (공동구매팀)

## 컬럼
| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, auto | 식별자 |
| product_id | BIGINT | NOT NULL, FK | 상품 |
| leader_id | BIGINT | NOT NULL, FK | 팀장 |
| current_count | INT | NOT NULL, default 1 | 현재 참여 인원 (동시성 제어 핵심) |
| status | VARCHAR(20) | NOT NULL | RECRUITING/SUCCESS/FAILED |
| deadline | DATETIME | NOT NULL | 팀 유지 마감 |

## 인덱스
- idx_product_status (product_id, status)

## 관계
- product_id → product.id
- leader_id → member.id

## 사용하는 기능
- team/create, team/join ...
```

## 작성 컨벤션

- **네이밍**: 테이블·컬럼 **snake_case**, 테이블명은 **단수**(`group_buy_team`) — 일관 유지.
- **PK**: `id BIGINT AUTO_INCREMENT`.
- **FK**: `{참조테이블}_id` (예: `product_id`).
- **공통 컬럼**: `created_at`, `updated_at` (`DATETIME`, audit용).
- **타입**: 문자열 `VARCHAR(n)` 길이 명시 · 시간 `DATETIME` · 참/거짓 `BOOLEAN`.
- **NULL**: `NOT NULL` 기본. nullable은 사유와 함께 명시 (예: `payment.team_id`는 혼자구매 시 NULL).
- **인덱스**: 조회 조건이 되는 컬럼에 부여.
- **삭제 정책**: soft delete(`deleted_at`) 여부를 팀 규칙으로 정해 일관 적용.

## 관계 & 규칙

- 이 명세는 **Plan 단계에서 작성**한다. Generate는 이 스키마대로 JPA 엔티티를 구현한다.
- `design.md`의 "데이터 모델"은 여기(`docs/db/{테이블}.md`)를 **참조**한다.
- 스키마가 바뀌면 이 문서를 고치고, 그 테이블을 쓰는 기능들의 영향을 검토한다.
- 실제 테이블은 JPA 엔티티로 생성되므로, 이 문서와 **엔티티가 일치**해야 한다 (완료 시 동기화).
