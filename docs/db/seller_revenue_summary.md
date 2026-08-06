# seller_revenue_summary (판매자 수익 요약)

## 컬럼
| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, auto | ID |
| seller_id | BIGINT | NOT NULL, UNIQUE, FK | 판매자 1명당 1행 |
| total_revenue | INT | NOT NULL, default 0 | PAID 결제 누적 합 |
| paid_count | BIGINT | NOT NULL, default 0 | PAID 결제 누적 건수 |
| refunded_count | BIGINT | NOT NULL, default 0 | REFUNDED 결제 누적 건수 |
| created_at | DATETIME | NOT NULL | 생성일(최초 부트스트랩 시점) |
| updated_at | DATETIME | NOT NULL | 마지막 갱신일 |

## 인덱스
- `UNIQUE seller_id` — 판매자당 요약 행이 정확히 하나만 존재하도록 강제(부트스트랩 경쟁 상태 방어의 근거).

## 관계
- seller_id → member.id

## 컬럼 집계 방식 (2026-08-06 upsert 전환, docs/dev/mypage/view/changes/004-upsert-fix.md)
- `current_count`와 동일한 패턴 — 매번 SUM/COUNT하지 않고, 결제/환불이 발생하는 트랜잭션 안에서 이 컬럼을 즉시 증감시킨다.
- **요약 행 생성 시점 = 결제 시점**: `SellerRevenueSummaryRepository.incrementPaid`는 `payment/create`(PAID 결제 저장 시) 안에서 MySQL `INSERT ... ON DUPLICATE KEY UPDATE`로 동작하는 upsert다 — 그 판매자의 요약 행이 없으면 이 결제 값(`total_revenue=amount`, `paid_count=1`)으로 새로 만들고, 있으면 원자적으로 `total_revenue += amount`, `paid_count += 1`. `UNIQUE(seller_id)` 충돌 시 MySQL이 그 행에 락을 걸고 UPDATE로 전환하는 단일 SQL 문이라, 같은 판매자에게 동시에 여러 "첫 결제"가 들어와도(요약 행이 아직 없는 상태) 유실·중복 없이 정확히 반영된다.
  - (이전 방식은 "행이 있으면만 증가"하는 조건부 UPDATE였고, 요약 행은 판매자가 자기 수익 페이지를 처음 조회할 때 지연 부트스트랩으로 만들어졌다. 이 둘의 시점이 어긋나 있어서, 아직 조회된 적 없는 판매자에게 결제가 들어오면 조용히 무시되고, 그 직후의 부트스트랩이 그 결제를 못 본 채 행을 만드는 경쟁 상태가 있었다. 요약 행 생성 시점을 "조회"가 아니라 "결제"로 옮겨서 이 경쟁 상태 자체를 없앴다.)
- **환불 감소**(`team/deadline-check`, `applyRefund`): 여전히 조건부 UPDATE(`total_revenue -= 환불금액합`, `paid_count -= 환불건수`, `refunded_count += 환불건수`)다. **전제**: 결제 시점에 `incrementPaid`(upsert)가 이미 요약 행을 만들어뒀을 것이므로, 환불 시점엔 행이 존재해야 한다(환불은 항상 이미 PAID였던 결제를 대상으로 하므로). 이 전제가 깨지는 유일한 경우는 "upsert 전환 이전부터 있던 결제 이력"이 아직 아래 백필로 채워지지 않은 채 환불이 먼저 들어오는 것 — 이 경우 대상 행이 없어 0 rows affected로 조용히 무시되는데, `TeamDeadlineService.processDeadline()`이 이 리턴값이 0이면 WARN 로그를 남겨 드러나게 한다.
- **동시성**: `current_count`와 달리 "정원 초과 금지" 같은 지켜야 할 불변식이 없는 단순 누적이라, 비관적 락 없이 원자적 UPDATE/upsert 한 문장만으로 충분하다.
- **조회는 순수 읽기**: `mypage/seller-revenue`(`SellerMypageService.revenue()`)는 요약 행을 단순 조회만 한다 — 위 upsert 덕분에 결제가 한 번이라도 있었다면 요약 행이 항상 존재하므로, 조회 시점에 행을 만드는 쓰기(예전의 지연 부트스트랩)는 하지 않는다. 요약 행이 없으면 결제가 아예 없었다는 뜻이라 그냥 0을 반환한다.
- **기존 데이터 백필(1회성)**: 이번 upsert 전환 *이전부터* 존재하던 결제 이력이 있는데 아직 요약 행이 없는 판매자를 위해, `SellerRevenueSummaryBackfillService`(대상 판매자 탐색: `PaymentRepository.findDistinctSellerIdsWithPayments`로 결제 이력 있는 seller_id 전부 조회 후, 이미 요약 행이 있는 판매자는 건너뛰고 없는 판매자만 `PaymentRepository.findRevenueSummaryBySellerId`로 재계산해 채움)를 별도로 마련했다. 배포 시 한 번만 켜서 실행하는 `SellerRevenueSummaryBackfillRunner`(`ApplicationRunner`, 기본 비활성 — `app.backfill.seller-revenue-summary=true`일 때만 등록·실행)를 통해서만 트리거한다. **조회마다 실행되면 안 된다** — 그게 이번에 없앤 지연 부트스트랩과 같은 문제(조회-쓰기 경쟁 상태)를 재발시킨다. 현재 로컬 DB는 결제 0건이라 당장 백필 대상은 없다.
- 캐싱(Redis+TTL) 대신 이 컬럼 집계 방식을 택한 이유는 돈 관련 데이터에 staleness 여지를 두지 않기 위함(튜터 피드백 반영) — 상세: `docs/policy/caching.md`, `docs/ERD.md`.

## 사용하는 기능
- payment/create, team/deadline-check, mypage/seller-revenue

## 삭제 정책
- 하드 삭제 없음(판매자가 존재하는 한 유지).
