# 캐싱 정책

> **고도화 단계 작업** — 1차 MVP에서 구현하지 않는다.
> 단, MVP 개발 시에도 이 정책을 숙지하고 캐싱 친화적 구조로 짠다.

## 규칙

### 캐싱 대상 / 제외 대상

| 대상 | 캐싱 | 무효화 시점 |
|------|------|------------|
| 상품 목록·상세 | O | 해당 상품 수정·삭제 시 + **리뷰 작성·수정·삭제 시** |
| 판매자 수익 현황 | X (컬럼 캐싱으로 전환) | — |
| 공구팀 목록 | X | — |
| 팀 참가·신설 | X | — |

### 계층 제약

캐싱 로직은 **Service 계층에서만** 적용한다 (Controller·Repository 금지).

### TTL (안전장치)

무효화 트리거 누락에 대비해 모든 캐시 항목에 TTL을 건다. 무효화가 빠짐없이 동작하는 것을 전제하지 않는다 — TTL이 최후 방어선이다.

## 근거 / 배경

- 상품 목록·상세는 조회 빈도가 높고 등록·수정 전까지 변하지 않아 캐싱 효과가 크다.
- 판매자 수익 현황은 캐싱 대상에서 제외한다(2026-08-05). 돈과 직결된 데이터라 TTL 기반 staleness 여지를 두지 않기로 하고, `group_buy_team.current_count`와 같은 방식(집계 컬럼을 결제/환불 트랜잭션 안에서 즉시 갱신)으로 전환했다. 상세: `docs/db/seller_revenue_summary.md`.
- 공구팀 목록은 참가 시마다 `current_count`가 바뀌어 오래된 값 노출 위험이 있다.
- 팀 참가·신설은 동시성 제어(`docs/db/group_buy_team.md`) 핵심 로직이라 캐싱 개입 시 정합성 위험이 있다.
- `group_buy_team.current_count`는 이미 DB 컬럼 레벨 캐싱으로 확정됐다 (`docs/ERD.md`).
- 무효화 로직이 어딘가 누락되면(버그) 캐시가 옛 값을 영구히 반환할 위험이 있어, TTL을 안전장치로 병행한다.
- **캐시된 응답이 "그 엔티티" 말고 다른 데이터에도 의존하면, 그 데이터를 바꾸는 쪽에서도 무효화해야 한다**(2026-08-20 추가). 상품 응답의 `sellerTrustedBadge`(product/seller-trust)는 상품이 아니라 **리뷰**로 결정되는데, 처음엔 무효화 트리거를 상품 수정·삭제에만 걸어둬서 리뷰로 배지 조건을 채워도 최대 30분간 배지가 안 떴다. 캐시 키(상품)와 의존 데이터(리뷰)가 다를 수 있다는 걸 표에서 놓치기 쉽다.
- **`@CacheEvict`는 트랜잭션 커밋 이후에만 실행되도록 순서가 고정돼 있다**(2026-08-20 추가, `CacheConfig`의 `@EnableCaching(order = Ordered.HIGHEST_PRECEDENCE)`). `@Transactional`과 `@CacheEvict`를 같은 메서드에 함께 쓰면(`ReviewService`, `ProductService` 등) AOP 어드바이저 순서를 명시하지 않는 한 캐시 무효화가 커밋보다 먼저 실행될 수 있어, 그 틈에 동시 조회가 아직 커밋 안 된 옛 값으로 캐시를 다시 채우는 레이스가 생긴다. 이 설정으로 캐싱 어드바이저가 항상 트랜잭션 어드바이저보다 바깥쪽에 있어 "커밋 → 무효화" 순서가 모든 `@CacheEvict` 호출부에 구조적으로 보장된다(개별 메서드가 신경 쓸 필요 없음) — `CacheEvictionOrderingTest`가 이 순서를 고정 검증한다.

## 적용 대상

- product/list, product/detail
- product/update, product/delete
- review/create, review/update, review/delete (`sellerTrustedBadge` 때문에 상품 캐시를 무효화)
