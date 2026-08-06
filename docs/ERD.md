# ERD (초안)

와이어프레임(구매자/판매자 화면, 유저플로우)을 기준으로 필요한 테이블을 정리한다.
아직 확정본이 아니라 **초안** — 팀원과 같이 검토 후 확정한다.

## 테이블 목록

| 테이블 | 역할 |
|---|---|
| `member` | 회원 (구매자/판매자 공용) |
| `product` | 판매자가 등록한 상품 |
| `price_tier` | 상품별 "N인 이상 참여 시 가격" 구간표 |
| `group_buy_team` | 상품 하나에 여러 개 생길 수 있는 "공동구매팀" |
| `team_participation` | 회원이 어떤 팀에 참여했는지 |
| `payment` | 결제 내역 |
| `seller_revenue_summary` | 판매자별 결제 집계(총매출·PAID건수·REFUNDED건수)를 미리 계산해둔 요약 행 |

## member (회원)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | bigint (PK) | 회원 ID |
| username | varchar | 로그인 아이디 |
| password | varchar | 비밀번호 (암호화 저장) |
| name | varchar | 이름 |
| email | varchar | 이메일 |
| role | varchar/enum | `BUYER`(구매자) 또는 `SELLER`(판매자) — 가입 시 하나로 고정 |
| created_at | datetime | 가입일 |

**설계 결정**: 구매자/판매자 테이블을 따로 안 만들고 `member` 하나에 `role` 컬럼으로 구분함. 가입할 때 역할 하나를 선택하고, 나중에 "역할 전환" 기능은 고도화 단계로 미룸(와이어프레임 논의에서 결정).

## product (상품)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | bigint (PK) | 상품 ID |
| seller_id | bigint (FK → member.id) | 등록한 판매자 |
| name | varchar | 상품명 |
| description | text | 상품 설명 |
| base_price | int | 정가(1인 구매 시 가격, "혼자구매하기") |
| max_participants | int | 팀 하나당 최대 인원(N) — 베스트공구가격 구간의 상한 |
| image_url | varchar | 상품 이미지 URL (단순 문자열, 별도 이미지 테이블 없음) |
| created_at | datetime | 등록일 |

## price_tier (가격 구간표)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | bigint (PK) | ID |
| product_id | bigint (FK → product.id) | 어떤 상품의 구간인지 |
| min_count | int | 이 가격이 적용되는 최소 참여 인원 (예: 2) |
| price | int | 그 인원대에 해당하는 1인당 가격 |

**설계 결정**: "베스트공구가격(2인~N인)"이 인원 구간별로 달라지는 계단식 구조라서, 상품 테이블에 가격을 고정값으로 두지 않고 별도 테이블로 분리함. 예: 2인 이상 9000원, 5인 이상 7500원, 10인(=N) 이상 5000원(베스트가격) 이런 식으로 여러 행이 쌓임.

## group_buy_team (공동구매팀)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | bigint (PK) | 팀 ID |
| product_id | bigint (FK → product.id) | 어떤 상품에 대한 팀인지 |
| leader_id | bigint (FK → member.id) | 팀을 처음 만든 사람 ("구매팀 신설하기") |
| current_count | int | 현재 참여 인원 (**동시성 제어가 필요한 핵심 컬럼**) |
| status | varchar/enum | `RECRUITING`(모집중) / `SUCCESS`(성사) / `FAILED`(미성사) |
| deadline | datetime | 팀 유지 마감 시각 (마이페이지의 "남은 팀유지 기간"). **팀 신설 시점 + 7일로 확정(2026-08-03)** |
| created_at | datetime | 팀 생성일 |

**설계 결정**: 상품 하나에 여러 개의 팀이 동시에 존재할 수 있는 구조(팀원 제안 반영). `current_count`를 동시에 여러 명이 "참가하기" 눌러도 정확히 세어야 하므로, 여기가 이번 프로젝트의 동시성 제어 핵심 지점이 됨(발제 필수항목 "동시성 제어"랑 직결).

**동시성 제어 방식 (2026-07-27 확정)**: `current_count`는 매번 `team_participation`을 `COUNT()`하지 않고 이 컬럼에 캐싱한다. 참가(`join`) 처리 시 해당 팀 row에 **비관적 락**(`SELECT ... FOR UPDATE`, JPA `@Lock(PESSIMISTIC_WRITE)`)을 걸고 `current_count` 확인 → 증가 → 정원 도달 시 `SUCCESS` 전환까지 한 트랜잭션에서 처리한다. "마지막 자리 경쟁" 상황에서 정확성을 최우선으로 하기 위함(자세한 이유는 옵시디언 참고).

## team_participation (팀 참여 내역)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | bigint (PK) | ID |
| team_id | bigint (FK → group_buy_team.id) | 어떤 팀에 참여했는지 |
| member_id | bigint (FK → member.id) | 참여한 회원 |
| joined_at | datetime | 참여 시각 |

**설계 결정**: "마이페이지 - 공구참여목록(미성사/성사)"을 조회하려면 회원이 어떤 팀에 참여했었는지 기록이 남아야 해서 별도 테이블로 분리함. `group_buy_team.status`를 join해서 성사/미성사 구분.

## payment (결제 내역)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | bigint (PK) | ID |
| member_id | bigint (FK → member.id) | 결제한 회원 |
| product_id | bigint (FK → product.id) | 결제한 상품 |
| team_id | bigint (FK → group_buy_team.id, nullable) | 공동구매 참여로 인한 결제면 팀 ID, "혼자구매하기"면 NULL |
| amount | int | 결제 금액 |
| status | varchar/enum | `PAID`(결제완료) / `REFUNDED`(환불, 미성사 시) |
| paid_at | datetime | 결제 시각 |

**설계 결정**: "결제창"에서 참가/신설/혼자구매 셋 다 바로 결제로 이어지므로(장바구니 없음), 결제 시점에 바로 payment row가 생김. `team_id`가 NULL이면 "혼자구매하기" 경로, 값이 있으면 공동구매 참여 경로로 구분. 팀이 미달성(FAILED)되면 해당 팀에 연결된 `payment`들을 `REFUNDED`로 일괄 처리하는 로직이 필요함.

## seller_revenue_summary (판매자 수익 요약)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | bigint (PK) | ID |
| seller_id | bigint (FK → member.id, UNIQUE) | 판매자 1명당 1행 |
| total_revenue | int | PAID 결제 누적 합 |
| paid_count | bigint | PAID 결제 누적 건수 |
| refunded_count | bigint | REFUNDED 결제 누적 건수 |
| created_at / updated_at | datetime | |

**설계 결정**: `current_count`와 동일한 이유·방식 — 매번 SUM/COUNT하지 않고 결제/환불 트랜잭션 안에서 즉시 갱신한다. 캐싱(Redis+TTL) 대신 이 방식을 택한 이유는 돈 관련 데이터에 staleness 여지를 두지 않기 위함(2026-08-05, 튜터 피드백 반영).

## 관계 정리 (요약)

```
member (1) ─── (N) product        [판매자가 여러 상품 등록]
product (1) ─── (N) price_tier    [상품 하나에 가격 구간 여러 개]
product (1) ─── (N) group_buy_team [상품 하나에 팀 여러 개 동시 존재]
group_buy_team (1) ─── (N) team_participation [팀 하나에 참여자 여러 명]
member (1) ─── (N) team_participation [회원이 여러 팀에 참여 가능]
member (1) ─── (N) payment
product (1) ─── (N) payment
group_buy_team (1) ─── (N) payment [nullable]
member (1) ─── (1) seller_revenue_summary
```

## 결정된 것 (2026-07-27)

- `current_count`: 컬럼 캐싱 + 비관적 락. 상세: `docs/policy/team-success-criteria.md`
- 미성사/환불 트리거: 1분 주기 스케줄러. 상세: `docs/policy/refund-trigger.md`
- 상품 이미지: `product.image_url` 단순 URL 컬럼 (갤러리 없음, MVP는 1장)
- 판매자 수익 요약(seller_revenue_summary): 컬럼 집계 + 결제/환불 시점 트랜잭션 내 갱신. 상세: docs/db/seller_revenue_summary.md (2026-08-05)

## 아직 결정 안 된 것 (팀원과 확인 필요)

- (현재 없음 — 남는 대로 여기 추가)
