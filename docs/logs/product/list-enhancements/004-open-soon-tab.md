# 004-open-soon-tab — 카테고리 바 "오픈예정" 탭 (로그)

## Attempt 1 — 2026-08-20

- 시도: `docs/dev/ongoing/product-open-soon-tab.md`(승인된 계획)의 태스크를 그대로 구현.
  - `ProductRepositoryCustom`/`ProductRepositoryImpl.findAllWithSeller()`에 `boolean openSoon` 파라미터
    추가. QueryDSL 조건: `openSoon=true`면 `openAt`이 있고 아직 미래인 상품만, `openSoon=false`이면서
    `category != null`이면 반대로 아직 공개 전인 상품을 제외(`openAt`이 없거나 이미 지남), `category`도
    `openSoon`도 없으면 조건 없음(기존과 동일, 회귀 없음). `content`/`total` 두 쿼리 모두에 조건 반영.
  - `ProductService.list()`에 `openSoon` 파라미터 추가 + `@Cacheable` key에 `#openSoon` 추가(카테고리
    제외 규칙 자체는 새 캐시 축이 아니라는 계획의 결론에 따라 그 외 키 구성은 그대로 둠).
  - `ProductController.list()`에 `@RequestParam(defaultValue = "false") boolean openSoon` 추가, 그대로
    `productService.list()`에 전달.
  - `ProductCachingTest`/`ProductControllerTest`의 기존 `productService.list(...)`/`findAllWithSeller(...)`
    호출부(시그니처 변경으로 컴파일 깨지는 지점) 전부 5번째 인자(`false`/`anyBoolean()`) 추가해 갱신.
  - 신규 테스트:
    - `ProductControllerTest`: 카테고리 탭에서 아직 오픈 전(openAt 미래)인 그 카테고리 상품이 제외되고
      이미 지났거나 openAt이 없는 상품은 포함되는 케이스, `openSoon=true`가 오픈예정 상품만 반환하는
      케이스, `category`/`openSoon` 둘 다 생략한 전체 조회가 오픈예정 상품도 포함하는 회귀 방지 케이스,
      `category` 없이 `keyword`만 있는 검색이 오픈예정 여부와 무관하게 기존과 동일하게 동작하는 회귀
      방지 케이스.
    - `ProductCachingTest`: `openSoon=true`/`false`가 같은 page/size/category/sort라도 서로 다른 캐시
      엔트리를 쓴다는 것(그리고 각자 캐시 히트 시 레포지토리를 우회한다는 것)을 스파이로 검증.
  - 프론트(`static/js/main.js`): `CATEGORIES` 배열에 `{ value: null, label: '오픈예정', openSoon: true }`를
    "전체" 바로 다음(2번째)에 추가. "전체"와 "오픈예정" 둘 다 `value`가 `null`이라 `isCategorySelected()`
    헬퍼로 `openSoon` 플래그까지 함께 봐서 활성 pill을 구분(둘 다 `state.category === null`만으로는
    구분 불가). 클릭 시 `state.category`/`state.openSoon`을 배타적으로 설정하고 URL 쿼리파라미터
    (`category`/`openSoon`)도 그에 맞게 set/delete, `resetAndReload()`로 목록 재조회. `fetchProducts()`가
    `state.openSoon`이면 `openSoon=true`를 쿼리에 추가. 오픈예정 탭 전용 빈 목록 문구
    ("아직 오픈예정으로 등록된 상품이 없습니다...") 추가.
  - 프론트(`static/js/header-search.js`): 카테고리 바로가기 정적 목록 맨 앞에 `{ openSoon: true, label:
    '오픈예정' }` 추가, 클릭 시 `goToOpenSoon()`(`/index.html?openSoon=true`로 이동)이 실행되게 분기.
    `partials/header.html`은 정적 마크업에 카테고리 목록이 없고 전부 JS가 렌더링하는 구조라 별도 수정
    없음(계획에서 언급한 "동기화"는 header-search.js의 JS 배열만 해당).
  - 문서: `docs/dev/product/list-enhancements/design.md`에 "오픈예정 필터(openSoon)" 절 추가(탭별 노출
    규칙, 캐시 키 결정, 관련 코드 갱신). `docs/dev/product/product-launch/design.md`의 5번째 줄(별도
    브라우징 탭을 만들지 않는다는 서술)과 22번째 줄(검색/카테고리/정렬이 오픈예정과 무관하다는 서술)을
    수정된 스코프에 맞게 갱신.
  - 건드리지 않은 것(격리 지침 준수): `PaymentRepositoryCustom`/`Impl`, `BuyerMypageService`,
    `PaymentService`, `SellerMypageService`, `PaymentConfirmConcurrencyTest`,
    `BuyerMypageControllerTest`/`SellerMypageControllerTest`(payment 동시성 로그인 부분), 알림
    페이지네이션/헤더로고 관련 ongoing 문서들.
- 결과: `./gradlew compileJava`/`compileTestJava` 성공, `./gradlew test` 전체 통과(`BUILD SUCCESSFUL`,
  전체 스위트 실패 0). `ProductControllerTest` 31 tests(신규 4개 포함, `openSoon` 관련 테스트 포함) 전부
  통과, `ProductCachingTest` 6 tests(신규 1개 포함) 전부 통과 — 테스트 XML 확인
  (`build/test-results/test/TEST-com.gong9ri.gong9ri.controller.ProductControllerTest.xml`,
  `TEST-com.gong9ri.gong9ri.service.ProductCachingTest.xml`, 둘 다 `failures="0" errors="0"`).
- 남은 것(Evaluate 몫): 프론트 수동 확인(index.html 카테고리 바 순서·클릭 동작·헤더 오버레이 동기화)은
  코드 리뷰 수준으로만 확인했고 실제 브라우저 수동 조작 확인은 하지 않았다 — Evaluate 단계에서 필요하면
  수행.
