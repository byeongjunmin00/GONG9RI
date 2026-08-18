# 상품 목록 고도화 — 카테고리·정렬·참여 진행바 (product/list-enhancements) — Design

## 개요

`GET /api/products`(메인 페이지 상품 목록)를 실제 공동구매 사이트처럼 보강한다 — 카테고리 필터, 정렬(최신순/인기순), 카드에 표시할 실시간 참여 진행률까지 전부 이 하나의 엔드포인트에서 제공한다.

## API / 인터페이스

`docs/api/product.md`가 원천. 핵심만 요약:

- `GET /api/products?category=&sort=&page=&size=`
  - `category`: `FOOD`/`LIVING`/`BEAUTY`/`FASHION`/`DIGITAL`/`ETC` — 생략하면 전체.
  - `sort`: `LATEST`(등록일 내림차순) / `POPULAR`(진행 중 팀 중 참여 인원 최다 기준 내림차순) — 생략하면 정렬 없음(회귀 방지, 기존 호출부 동작 유지).
  - 응답 각 항목에 `category`, `activeTeamCurrentCount`, `activeTeamTargetParticipants` 포함.
- `POST /api/products`/`PUT /api/products/{id}`: `category` 필수(`@NotNull`).

## 데이터 모델

- `product.category` VARCHAR(20) NOT NULL, default `'ETC'`(`docs/db/product.md`) — 기존 row가 있는 테이블에 추가한 컬럼이라 `@ColumnDefault` 패턴(`autoRefundOnCancel`과 동일) 사용.
- `activeTeamCurrentCount`/`activeTeamTargetParticipants`는 DB 컬럼이 아니라 응답 시점에 `group_buy_team`을 조회해 계산하는 파생 값(DTO 전용 필드).

## 규칙 / 검증

### 카테고리 필터
- `ProductRepositoryImpl.findAllWithSeller`가 QueryDSL `BooleanExpression`으로 조건부 필터링(null이면 조건 없음).
- `ProductService.list()`의 캐시 키(`PRODUCT_LIST_CACHE`, TTL 30분)에 `category`를 포함해야 한다 — 안 그러면 카테고리별 결과가 캐시를 서로 덮어쓴다.

### 정렬(LATEST/POPULAR)
- `LATEST`: `product.createdAt.desc()`.
- `POPULAR`: 이 상품의 RECRUITING 팀 중 참여 인원이 가장 많은 팀의 인원수(`MAX(currentCount)`, 상관 서브쿼리)로 내림차순. DB 레벨 정렬이라 페이지네이션 경계가 정확하다(자바에서 후처리 정렬하면 페이지가 어긋남). RECRUITING 팀이 없는 상품은 서브쿼리가 NULL → MySQL이 DESC 정렬에서 NULL을 마지막으로 보내 자연스럽게 뒤로 밀린다.
- `sort`도 캐시 키에 포함한다. **인기순 정렬 결과는 캐시 TTL(30분)만큼 낡을 수 있다는 걸 의도적으로 허용한다** — 실시간 랭킹이 아니라 주기 갱신 랭킹으로 판단(다수 실서비스도 이런 방식). 아래 진행바 숫자와는 다른 신선도 기준을 적용한다는 점에 유의.

### 참여 진행바(activeTeamCurrentCount/activeTeamTargetParticipants)
- **캐시에 넣지 않는다.** `ProductService.list()`(캐시됨)는 이 필드를 항상 `null`로 둔 채 반환하고, 별도 public 메서드 `attachActiveTeamProgress(ProductPageResponse)`가 캐시 없이 매번 최신 `group_buy_team` 상태를 조회해 덧붙인다. `ProductController.list()`가 이 두 메서드를 순서대로 호출한다.
  - 이유: 팀 참가/취소는 자주 바뀌는 값이라, 캐시된 페이지 안에 넣으면 최대 30분간 실제와 다른 참여 인원을 보여줄 수 있다(판매자 수익현황을 Redis+TTL 캐싱에서 뺀 것과 같은 판단 — `docs/dev/mypage/view/design.md` 참고).
  - **같은 클래스 안에서 `list()`를 호출하면(self-invocation) Spring의 `@Cacheable` 프록시를 우회**해버리므로, 반드시 호출자(컨트롤러)가 `list()`와 `attachActiveTeamProgress()`를 각자 별도로 호출해야 한다 — 한 메서드 안에서 다른 하나를 내부 호출하지 않는다.
- 상품 하나에 진행 중인 팀이 여러 개면, **진행률(currentCount/maxParticipants)이 가장 높은 팀**을 대표로 골라 그 팀의 스냅샷을 노출한다(사용자 결정 — "달성률이 곧 성사될 팀"을 보여주는 게 FOMO 신호로 더 적절). 이건 인기순 정렬 기준(참여 인원 최다)과 **다른 선택 기준**이라 두 값이 서로 다른 팀을 가리킬 수 있다 — 의도된 차이(진행바는 "곧 성사될 팀" 스포트라이트, 인기순은 "얼마나 많이 모였는지" 자체가 신호).
- RECRUITING 팀이 하나도 없는 상품은 두 필드 모두 `null`(프론트는 이때 진행바를 숨긴다).

## 관련 코드

`entity/ProductCategory.java`, `entity/Product.java`(category 필드), `dto/ProductSort.java`, `dto/ProductSummaryResponse.java`(activeTeamCurrentCount/TargetParticipants + `withActiveTeamProgress()`), `repository/ProductRepositoryCustom.java`/`Impl.java`, `repository/GroupBuyTeamRepository.findByProductIdInAndStatus`, `service/ProductService.java`(`list()`, `attachActiveTeamProgress()`), `controller/ProductController.java`.
