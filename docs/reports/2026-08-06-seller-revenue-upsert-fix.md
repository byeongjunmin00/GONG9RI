# 개발 보고서 — 판매자 수익 요약(seller_revenue_summary) 부트스트랩 경쟁 수정

- **작성일**: 2026-08-06
- **작업자**: 전용운
- **대상 기능**: `mypage/view` (기존 기능 수정 — `docs/dev/mypage/view/changes/003-precomputed-revenue.md`에 대한 정정)
- **관련 문서**: [계획(완료 이관)](../dev/mypage/view/changes/004-upsert-fix.md) · [design.md](../dev/mypage/view/design.md) · [실행 로그](../logs/mypage/view/004-upsert-fix.md) · [선행 작업 보고서(집계 컬럼 전환)](2026-08-05-mypage-seller-revenue-caching.md)

---

## 1. 배경

직전 작업(003, Redis 캐싱 → `seller_revenue_summary` 집계 컬럼 전환)의 Evaluate 단계에서 "계획엔 없던 시나리오"로 지적된 리스크를 실제로 다시 검토한 결과, **근본 설계 오류**로 확인돼 이번에 정정했다.

### 문제의 정확한 구조

- 기존 설계: 요약 행은 **판매자가 자기 수익 페이지를 조회할 때**(`SellerMypageService.revenue()`) 없으면 그제서야 과거 결제를 다시 집계해서 만들어졌다("지연 부트스트랩").
- 결제(`PaymentService.create()` → `incrementPaid`)는 "행이 있으면 증가"하는 조건부 UPDATE라서, 아직 한 번도 조회된 적 없는 판매자에게 결제가 들어오면 **행이 없어 조용히 무시됐다**(0건 영향, 에러 없음).
- 그 결제와 그 판매자의 "첫 조회(부트스트랩)"가 타이밍이 겹치면, 부트스트랩이 그 결제를 못 본 채 행을 만들고, 그 결제 금액은 어디에도 반영되지 않고 영구히 사라질 수 있는 좁은 경쟁 구간이 있었다.
- 사용자 리뷰 과정에서 "요약 행이 조회 시점이 아니라 결제 시점에 이미 있어야 하는 게 맞지 않냐"는 지적이 나왔고, 검토 결과 이게 정확한 진단이었다 — **"결제 시점의 정상적인 행 생성"과 "이번 기능 도입 이전 과거 데이터 보정"이라는 서로 다른 두 문제를 하나의 로직(조회 시 부트스트랩)에 뭉쳐놔서 생긴 설계 오류**였다.

## 2. 수정 내용

두 문제를 명확히 분리했다.

| 문제 | 이전 | 이후 |
|---|---|---|
| 결제 시점 행 생성 | 조회 시 지연 부트스트랩 (경쟁 위험) | **결제 시점 upsert**(즉시, 원자적) |
| 과거 데이터 보정 | 위와 동일한 로직이 겸함 | **별도 일회성 배치**(기본 비활성화) |

### 핵심 변경

- **`SellerRevenueSummaryRepository.incrementPaid`**: 조건부 JPQL UPDATE → **네이티브 `INSERT ... ON DUPLICATE KEY UPDATE`**로 교체. 요약 행이 없으면 그 결제 값으로 즉시 생성, 있으면 원자적으로 증가. `UNIQUE(seller_id)` 충돌 시 MySQL이 해당 행을 락으로 직렬화하므로, 동시에 들어오는 "첫 결제" 여러 건도 안전하게 처리된다.
- **`SellerMypageService.revenue()`**: 조회 시 행을 만드는(쓰기) 로직 전부 제거. 순수 조회로 단순화, 행 없으면 결제 이력이 없다는 뜻이므로 그냥 빈 값(0) 반환. 클래스 기본 `@Transactional(readOnly=true)`로 복귀(더 이상 쓰기가 없음).
- **`TeamDeadlineService.processDeadline()`**: 환불 감소(`applyRefund`)가 0건 영향이면(요약 행이 없는데 환불이 들어온 예외 상황 — 백필 안 된 과거 이력 판매자 케이스) WARN 로그로 가시화.
- **신규 백필 메커니즘**: `PaymentRepository.findDistinctSellerIdsWithPayments()` + `SellerRevenueSummaryBackfillService`(판매자별로 요약 행 없으면 원본 재계산 후 생성) + `SellerRevenueSummaryBackfillRunner`(`ApplicationRunner`, `app.backfill.seller-revenue-summary=true`로만 켜지는 배치, **기본 비활성화** — 조회/기동마다 실행되지 않음).
- **죽은 코드 정리**: 이번 변경으로 호출부가 없어진 `RevenueResponse.from(RevenueSummaryProjection)` 오버로드를 Evaluate 단계에서 발견해 제거.

## 3. 검증

**핵심 검증**: 요약 행이 아예 없는(한 번도 결제 없던) 판매자에게 20개 스레드가 동시에 "첫 결제"를 넣는 경쟁 시나리오를 재현하는 테스트(`SellerRevenueSummaryConcurrencyTest`, 기존엔 "행이 이미 있는 상태"였던 걸 "행이 없는 상태"로 재작성)로, 정확히 이전에 지적됐던 그 경쟁을 정면으로 검증했다. **5회 연속 재실행 전부 통과**, 플래키니스 없음.

기타:
- 결제 이력이 아예 없는 판매자는 부트스트랩 없이도 정확히 0 반환 확인
- 백필 서비스 자체 테스트(멱등성 포함)
- 요약값 vs 원본 재계산값 드리프트 검증(기존 유지)
- 기존 `SellerMypageControllerTest` 회귀 확인(테스트 하나가 `Payment`를 직접 심던 방식에서 `SellerRevenueSummary`를 직접 심는 방식으로 조정 — 더 이상 부트스트랩에 의존하지 않으므로)

| 항목 | 결과 |
|---|---|
| `SellerRevenueSummaryConcurrencyTest` (재작성, 5회 연속 재실행) | ✅ 5/5 |
| `./gradlew clean build` (저장소 전체) | ✅ BUILD SUCCESSFUL, 85개 테스트 전부 통과 |

## 4. 남은 리스크 (문서화, 차단 사유 아님)

백필이 새 로직 배포와 함께(또는 그 이전에) 실행돼야 한다는 전제가 생긴다 — 안 하면 "과거 이력만 있고 아직 요약 행이 없는 판매자"에게 환불이 먼저 들어오는 경우 여전히 문제가 될 수 있다(이 경우는 최소한 WARN 로그로 남기게 해뒀다). 현재 로컬 DB는 결제 0건이라 당장 해당 없음.

## 5. 이 작업이 보여주는 것

계획(Plan) 단계에서 정한 설계가 Evaluate에서 "리스크로 남겨진 것"이라도, 사용자와 다시 짚어보는 과정에서 **설계 자체의 오류**로 재분류될 수 있다는 사례다. 최초 지적("돈 관련 데이터에 TTL staleness가 있으면 안 된다")에서 시작해 캐싱→집계 컬럼 전환(003)까지 갔다가, 그 안에서도 "조회 시점 생성"이라는 잘못된 타이밍 설계가 남아있던 걸 대화로 짚어내 이번(004)에 정정했다.

## 6. 커밋/푸시

아직 수행하지 않음 — 사용자 확인 후 진행 예정.
