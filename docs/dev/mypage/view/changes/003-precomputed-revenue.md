# 판매자 수익 현황(mypage/seller-revenue) — Redis 캐싱 → 집계 컬럼(정확값 유지) 전환

대상: mypage/view                <!-- 완료 시 이 기능의 changes/로 이동 -->
담당: 전용운

## 배경 / 요구

- 튜터 피드백: `mypage/seller-revenue`는 돈과 직결된 민감한 데이터인데 TTL(10분) 기반 캐싱은 "무효화 버그 시 최대 10분 오차"라는 여지를 남긴다는 지적. 추가로, 결제가 발생할 때마다 즉시 무효화되는 구조라 판매가 잦은(=결제가 자주 들어오는) 판매자일수록 캐시 적중률이 떨어져 캐싱 효과 자체가 옅어진다는 구조적 한계도 있음.
- 검토한 대안:
  1. TTL을 더 짧게(1분 등) — 오차 폭만 줄일 뿐 구조적 문제(잦은 무효화로 적중률 낮음)는 해결 안 됨. 기각.
  2. 캐싱 자체 제거 — 매번 SUM/COUNT 재계산. 정확하지만 원래 정책이 이 엔드포인트를 캐싱 대상으로 지정한 이유(집계 쿼리 비용) 자체를 포기하는 것.
  3. **(채택) 캐시가 아니라 미리 계산해둔 값(집계 컬럼)으로 전환.** 이 저장소에 이미 같은 패턴이 있음 — `group_buy_team.current_count`가 매번 COUNT하지 않고 컬럼에 캐싱해두고 참가 시점마다 트랜잭션 안에서 갱신하는 방식(`docs/ERD.md` "결정된 것"). 판매자 수익도 결제/환불 시점에 트랜잭션 안에서 누적값을 갱신하면 Redis·TTL·무효화가 통째로 필요 없어지고, **항상 정확한 값**이 나와 애초에 이번 튜터 피드백의 두 우려(오차 여지, 캐싱 효과 저하)가 원천적으로 사라짐.
- 이번 작업은 `docs/dev/mypage/view/changes/002-caching.md`(직전에 구현한 Redis 캐싱)를 **되돌리고 대체**하는 작업이다. `product/list`·`product/detail` 캐싱(`docs/dev/product/crud/changes/002-caching.md`)은 이번 스코프와 무관하며 그대로 유지한다.

## 설계

- **신규 테이블** `seller_revenue_summary`: 판매자(`seller_id`, UNIQUE FK) 1명당 1행. `total_revenue`(PAID 누적 합), `paid_count`(PAID 건수), `refunded_count`(REFUNDED 건수)를 들고 있다가, 결제/환불 시점에 **그 트랜잭션 안에서** 증감시킨다. 상세 컬럼 명세는 이번 Plan 단계에서 `docs/db/seller_revenue_summary.md`로 작성.
- **읽기**: `SellerMypageService.revenue()`는 더 이상 SUM/COUNT 집계 쿼리나 Redis 캐시를 거치지 않고, 이 테이블에서 sellerId로 단순 조회(PK/unique lookup)만 한다. `@Cacheable` 제거.
- **쓰기(갱신 시점)**: 정책이 원래 정의했던 무효화 시점과 동일하게, "결제 발생·환불 처리 시" 이 요약 행을 갱신한다.
  - `PaymentService.create()`: PAID 결제 저장 시 해당 판매자 요약 행의 `total_revenue += amount`, `paid_count += 1`.
  - `TeamDeadlineService.processDeadline()`: 환불 발생 시(`paidPayments`가 비어있지 않을 때) 해당 판매자 요약 행의 `total_revenue -= 환불금액합`, `paid_count -= 환불건수`, `refunded_count += 환불건수`.
- **동시성 참고**: `current_count`는 "정원 초과 금지"라는 불변식이 있어 비관적 락이 필요했지만, 이 요약값 증감엔 그런 불변식이 없는 단순 누적이라 락 없이도 원자적 UPDATE 한 문장(`col = col + x` 형태)만으로 충분할 가능성이 높다 — 이미 팀원이 `team/join-atomic`에서 이 방식(조건부 UPDATE)을 검증한 전례가 있다(`docs/dev/team/crud/design.md`). 다만 정확한 구현 방식(원자적 UPDATE vs 다른 방식, 동시 다발 결제 시 검증 방법)은 Generate가 판단.
- **기존 데이터 이전(마이그레이션) 문제**: 이 요약 테이블은 지금부터 신규로 채워지는데, 이미 존재하는 과거 결제 데이터는 반영이 안 돼 있다. 판매자별로 처음 조회하거나 처음 결제가 들어올 때 기존 집계 쿼리(`PaymentRepository.findRevenueSummaryBySellerId`, 삭제하지 않고 유지)로 한 번 실제 값을 계산해 요약 행을 초기화하는 "지연 부트스트랩" 방식과, 별도의 일회성 백필 스크립트로 미리 다 채워두는 방식 중 어느 쪽으로 할지는 Generate가 결정 — 단, **반드시 어떤 형태로든 처리해야 하는 리스크**로 명시한다(안 하면 기존 판매자의 과거 매출이 0으로 보이는 심각한 버그가 됨).
- **계층 제약**: 갱신·조회 로직은 Service 계층(`SellerMypageService`/`PaymentService`/`TeamDeadlineService`)에만 — 이 부분은 캐싱 정책의 계층 제약과 무관하게(이제 캐싱이 아니므로) 그대로 유지하는 게 기존 컨벤션과 일관됨.
- **제거 대상** (Redis 캐싱 되돌리기): `SellerMypageService.revenue()`의 `@Cacheable`, `PaymentService`/`TeamDeadlineService`의 캐시 무효화 호출(`CacheManager` 관련 코드), `CacheConfig`의 `SELLER_REVENUE_CACHE`/`sellerRevenueCacheCustomizer`/`SELLER_REVENUE_TTL`. `SellerRevenueCachingTest.java`는 더 이상 유효하지 않은 시나리오(캐시 히트/무효화)를 검증하므로 삭제하거나 새 메커니즘에 맞는 테스트로 대체(Generate 판단).

## 태스크

- [ ] `docs/db/seller_revenue_summary.md` 테이블 명세 작성
- [ ] `docs/policy/caching.md`에서 `mypage/seller-revenue`를 캐싱 대상에서 제거하고(더 이상 캐싱이 아님) 새 방식으로 바뀌었다는 점을 근거/배경에 짧게 반영, `적용 대상`에서 `payment/create`·`team/deadline-check`도 이 항목과 관련해선 제거(단, `product/list` 관련 트리거는 그대로 유지되는지 확인)
- [ ] `docs/ERD.md` "결정된 것"에 이번 결정(요약 컬럼 방식) 추가 (current_count 결정과 같은 패턴으로 기록)
- [ ] `SellerRevenueSummary` 엔티티 + 리포지토리(원자적 증감 쿼리) 추가
- [ ] `PaymentService.create()`: 캐시 무효화 제거 → 요약 행 증가 갱신으로 교체
- [ ] `TeamDeadlineService.processDeadline()`: 캐시 무효화 제거 → 요약 행 감소 갱신으로 교체
- [ ] `SellerMypageService.revenue()`: `@Cacheable` 제거 → 요약 테이블 조회로 교체 + 부트스트랩/백필 처리
- [ ] `CacheConfig`에서 `sellerRevenue` 관련 빈·상수 제거(product 목록/상세 캐시는 그대로 유지)
- [ ] `SellerRevenueCachingTest.java` 정리(삭제 또는 새 메커니즘에 맞는 테스트로 교체)
- [ ] 신규 동작 테스트 작성: 결제 시 증가, 환불 시 감소, 동시 다발 결제 시 정합성(동시성), 기존 데이터 부트스트랩/백필 시나리오, "요약값 vs 원본 재계산값 일치" 정합성 검증
- [ ] `docs/dev/mypage/view/design.md` 갱신(캐싱 설명 → 집계 컬럼 방식 설명으로 교체)

## 평가(통과) 기준

- 기존 `SellerMypageControllerTest`(11케이스) 회귀 없이 통과
- 신규 테스트 통과: 결제 생성 시 정확히 반영, 환불 시 정확히 반영, 동시에 여러 결제가 들어와도 최종 합계가 정확함(동시성 검증), 요약 테이블에 아직 행이 없는(과거 데이터만 있는) 판매자도 정확한 값을 돌려줌(부트스트랩/백필 검증)
- 판매자 여러 명 + 대량 더미 결제로 "요약 테이블 조회 결과"와 "원본 payment 테이블에서 직접 재계산한 값"이 정확히 일치하는지 별도로 대조 검증(드리프트 방지)
- `./gradlew build` 전체 통과
- `docs/policy/caching.md`·`docs/ERD.md`·`docs/db/seller_revenue_summary.md`·`docs/dev/mypage/view/design.md` 갱신 완료 여부

## 리스크/전제

- **기존 데이터 이전을 빠뜨리면 심각한 정합성 버그**(과거 매출이 0으로 표시)로 이어진다 — 반드시 처리해야 함(방식은 Generate 결정).
- 동시 다발 결제에 대한 원자적 증감의 정확성은 이번 작업의 핵심 검증 대상이다(동시성 테스트로 실제 확인 필요, `team/join` 동시성 테스트 사례 참고).
- 이 변경은 직전에 구현한 Redis 캐싱(`changes/002-caching.md`)을 되돌리는 작업이라, 관련 코드·문서를 깨끗이 제거해야 한다(죽은 코드 방지) — 단, `product/list`·`product/detail` 캐싱(같은 `CacheConfig` 파일을 공유)은 건드리지 않는다.
- 로컬 검증엔 MySQL(Docker `gong9ri-mysql`)이 필요. Redis(`gong9ri-redis`)는 이 기능엔 더 이상 필요 없지만 `product/list`·`product/detail` 캐싱이 계속 쓰므로 컨테이너 자체는 유지.
