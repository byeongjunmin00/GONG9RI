# 상품 목록·상세(product/list, product/detail) Redis 캐싱

대상: product/crud                <!-- 완료 시 이 기능의 changes/로 이동 -->
담당: 전용운

## 배경 / 요구

- `docs/policy/caching.md`가 캐싱 대상으로 지정한 두 항목 중 나머지 하나. `mypage/seller-revenue`는 이미 구현 완료(`docs/dev/mypage/view/changes/002-caching.md`).
- 상품 목록·상세는 조회 빈도가 높고 등록·수정·삭제 전까지 안 변해 캐싱 효과가 크다고 정책에 명시돼 있다.
- **정책 문서엔 없지만 이번에 사용자와 협의해 스코프에 포함하기로 결정한 사항**: `docs/policy/caching.md`의 무효화 트리거 표엔 `product/list`에 대해 `product/update`·`product/delete`만 적혀있고 `product/register`(신규 등록)가 빠져있다. 목록 조회(`ProductRepository.findAllWithSeller`)에 `ORDER BY`가 없어 새 상품이 어느 페이지에 나타날지 불확실하고, 이 목록은 비로그인 포함 모두가 보는 공개 카탈로그라 캐시가 안 지워지면 "방금 등록한 상품이 목록에 안 보이는" 문제가 TTL 만료 전까지 사용자에게 그대로 노출된다. 판단 근거를 사용자에게 설명하고 **register도 목록 캐시 무효화 대상에 포함하기로 합의함**.
- **선행 작업(`mypage/seller-revenue` 캐싱)에서 얻은 교훈 반영**: 캐싱 대상 응답 DTO(`ProductPageResponse`, `ProductResponse`, `ProductSummaryResponse`, `PriceTierResponse`)는 전부 `Serializable`을 구현하지 않는 `record`다. 지난 작업에서 기본 Redis 직렬화기(JDK 직렬화)가 non-serializable payload를 요구해 런타임에 깨지는 결함과, 범용 JSON 직렬화기를 쓰면 타입 정보 소실로 캐시 히트 시 `LinkedHashMap`/`ClassCastException`이 나는 결함을 순서대로 겪었다(`docs/logs/mypage/view/002-caching.md` Attempt 1·2 참고). 이번엔 같은 실수를 반복하지 않도록 처음부터 타입 고정 JSON 직렬화기를 쓰는 방향으로 계획한다.

## 설계

- **캐시 대상**:
  - `ProductService.list(page, size)` — 캐시 키: `page`+`size` 조합.
  - `ProductService.detail(productId)` — 캐시 키: `productId`.
- **무효화 트리거** (정책 + 이번에 합의한 register 포함):
  - `ProductService.register(...)` 완료 시 → 목록 캐시 **전체** 무효화(신규 상품이 어느 페이지에 들어갈지 불확실하므로 특정 페이지만 지우는 방식은 불가능). 상세 캐시는 건드릴 필요 없음(신규 productId는 캐시에 아직 없어 첫 조회가 자연히 미스).
  - `ProductService.update(...)` 완료 시 → 해당 `productId`의 상세 캐시 무효화 + 목록 캐시 전체 무효화(이름·`basePrice`·`bestPrice`가 바뀌면 그 상품이 포함된 페이지가 달라지는데 어느 페이지인지 특정 불가).
  - `ProductService.delete(...)` 완료 시 → 위와 동일(해당 productId 상세 캐시 무효화 + 목록 캐시 전체 무효화).
- **TTL**: 정책상 안전장치로 필수. 목록·상세 각각 별도 TTL을 둘지, 같은 값을 쓸지는 Generate가 판단(구체 값도 Generate 결정).
- **계층 제약**: 캐싱 로직은 `ProductService`(Service 계층)에만 — Controller·Repository엔 두지 않는다(`docs/policy/caching.md` 그대로).
- **직렬화**: `mypage/seller-revenue`에서 확정한 접근(캐시별로 타입을 고정한 JSON 직렬화기, 예: `JacksonJsonRedisSerializer<>(대상타입.class)`)을 재사용. 기존 `CacheConfig`에 캐시 이름 2개(목록/상세)를 추가하는 형태로 확장할지, 별도 설정으로 분리할지는 Generate가 판단.
- **영향 계층**: `ProductService.java`만 수정(캐싱·무효화 로직). `CacheConfig.java` 확장. Controller(`ProductController`)·Repository는 변경 없음.

## 태스크

- [ ] `CacheConfig`에 상품 목록/상세 캐시(이름, TTL, 타입 고정 JSON 직렬화기) 추가
- [ ] `ProductService.list(page, size)`에 캐싱 적용 (키: page+size)
- [ ] `ProductService.detail(productId)`에 캐싱 적용 (키: productId)
- [ ] `ProductService.register(...)`에 목록 캐시 전체 무효화 추가
- [ ] `ProductService.update(...)`에 상세 캐시(해당 productId) + 목록 캐시 전체 무효화 추가
- [ ] `ProductService.delete(...)`에 상세 캐시(해당 productId) + 목록 캐시 전체 무효화 추가
- [ ] 캐싱 동작 테스트 작성 (히트/각 트리거별 무효화 시나리오, `mypage/seller-revenue` 때처럼 의미 있는 규모의 더미 데이터 + 캐시 우회 데이터 변경으로 "진짜 캐시된 값"인지 증명하는 방식 유지)
- [ ] 직렬화 왕복 검증 테스트 추가 (`CacheConfigTest`에 상품 목록/상세 캐시용 케이스 추가하는 방식 검토)

## 평가(통과) 기준

- 기존 `ProductControllerTest`(12케이스) 회귀 없이 통과
- 신규 캐싱 테스트 통과:
  1. 동일 page/size 반복 조회 시 2회차부터 레포지토리 재호출 안 함(캐시 히트), 동일 productId 상세 반복 조회도 동일하게 검증
  2. `register` 이후 목록 재조회 시 새 상품이 반영됨(무효화 확인)
  3. `update` 이후 해당 상품 상세·목록 재조회 시 최신 값 반영
  4. `delete` 이후 목록 재조회 시 해당 상품이 빠짐, 상세 재조회 시 `404 PRODUCT_NOT_FOUND`
- `./gradlew build` 전체 통과
- 캐싱 로직이 Service 계층에만 있는지 컨벤션 확인
- 캐시 대상 DTO들이 실제 Redis 직렬화기로 안전하게 왕복되는지(비직렬화 예외·타입 소실 없음) 검증 — 이전 캐싱 작업에서 두 번 겪은 결함을 재발 방지 테스트로 미리 잡을 것

## 리스크/전제

- 목록 캐시는 register/update/delete 시 "전체 무효화" 방식이라, 캐시 적중률이 `mypage/seller-revenue`(판매자 단위 정밀 무효화)보다 낮을 수 있음 — 상품 등록/수정/삭제가 잦으면 캐싱 효과가 떨어질 수 있다는 점은 알리되, 해결(부분 무효화 전략 등)은 Generate/추후 과제로 남긴다.
- `findAllWithSeller`에 `ORDER BY`가 없어 페이지 결과의 안정성 자체가 이미 애매한 상태(DB가 정렬을 보장 안 함) — 이건 이번 캐싱 작업이 만든 문제가 아니라 기존 구현의 전제이지만, 캐싱을 얹으면 "정렬 불안정 + 캐시로 인한 지연 반영"이 겹쳐 체감 이슈가 커질 수 있다는 점은 리스크로 남긴다.
- 캐시 대상 DTO 4종(`ProductPageResponse`, `ProductResponse`, `ProductSummaryResponse`, `PriceTierResponse`) 모두 non-serializable record — 직렬화 설정을 빠짐없이 해야 함(1개라도 빠지면 이전과 같은 클래스의 결함 재발).
- 로컬 검증엔 Docker(`mysql:8`, `redis:7`)가 필요 — 이번 세션에서 이미 로컬 Docker 환경 구성 완료(WSL2 활성화, Docker Desktop 정상 기동 확인됨)라 추가 설치 이슈는 없을 것으로 예상.
