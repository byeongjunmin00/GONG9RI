# 001-image — 상품 이미지 지원 (백엔드, 로그)

## Attempt 1 — 2026-08-10
- 시도: 계획 문서(`docs/dev/ongoing/product-image.md`) 그대로 `imageUrl` 배관 작업을 진행.
  - `entity/Product.java`: 생성자와 `update()`에 `String imageUrl` 파라미터 추가(nullable, 검증 애노테이션 없음). 컬럼 자체는 기존에 이미 있었으므로 매핑 필드/게터는 그대로 두고 대입 경로만 추가.
  - `dto/ProductRegisterRequest.java`: `imageUrl` 필드를 record 마지막 컴포넌트로 추가(검증 없음, 선택 입력).
  - `dto/ProductResponse.java`, `dto/ProductSummaryResponse.java`: 각각 `imageUrl` 필드 추가 + `of()` 정적 팩토리에서 `product.getImageUrl()` 매핑.
  - `service/ProductService.java`: `register()`의 `new Product(...)` 호출과 `update()`의 `product.update(...)` 호출에 `request.imageUrl()`을 그대로 전달. 캐시 무효화(`@CacheEvict`/`@Caching`) 로직은 기존 그대로 재사용(필드 추가만이라 전략 변경 없음).
  - `docs/api/product.md`: `GET /api/products`(목록), `GET /api/products/{id}`(상세), `POST /api/products` 요청/응답 예시에 `imageUrl` 필드 추가(예시 값은 Plan에서 확정한 Pexels URL 사용). `PUT`은 "POST와 동일" 문서 구조라 별도 수정 불필요.
- 기존 테스트 회귀 대응: `Product` 생성자·`update()`, `ProductRegisterRequest`, `ProductResponse`, `ProductSummaryResponse` 시그니처가 늘어나 컴파일이 깨지는 지점을 전부 찾아 `null`(또는 `ProductCachingTest`의 `registerRequest`는 `null`)로 채워 넣었다. 새 케이스는 추가하지 않음(계획에 신규 테스트 요구 없음).
  - 수정한 테스트 파일: `PaymentRepositoryTest`, `SellerRevenueSummaryTest`, `SellerRevenueSummaryConcurrencyTest`, `SellerMypageControllerTest`, `ProductCachingTest`, `TeamConcurrencyAtomicTest`, `TeamDeadlineServiceTest`, `TeamConcurrencyTest`, `TeamControllerTest`, `PaymentControllerTest`, `ProductControllerTest`, `BuyerMypageControllerTest`, `CacheConfigTest`(레코드 직렬화 왕복 테스트라 `ProductResponse`/`ProductSummaryResponse` 생성자 호출부 2곳).
- 결과: `./gradlew compileJava compileTestJava` 성공. `./gradlew test`(도커 MySQL/Redis 컨테이너 `gong9ri-main-mysql-1`/`gong9ri-main-redis-1` 기동 상태에서 실행) 전체 통과(`BUILD SUCCESSFUL`).
- 다음(참고): 브라우저 실측(등록→상세→메인카드 렌더링, 프리필 등)은 Evaluate 단계에서 진행.

### Evaluate — 2026-08-10  ✅ PASS
- 계산적 평가: `docker ps`로 `gong9ri-main-mysql-1`/`gong9ri-main-redis-1` 정상 기동(healthy) 확인. `./gradlew clean test` 전체 스위트 실행 → `BUILD SUCCESSFUL`(테스트 실패 0). `./gradlew compileJava compileTestJava` 정상.
- 추론적 평가(코드 대조):
  - `entity/Product.java`, `dto/ProductRegisterRequest.java`, `dto/ProductResponse.java`, `dto/ProductSummaryResponse.java`, `service/ProductService.java` 5개 파일 `git diff` 전부 확인 — 계획대로 생성자/`update()`에 `imageUrl` 파라미터 추가, record 필드 추가, `of()` 매핑, `register()`/`update()` 전달까지 정확히 일치. 컨트롤러/레포지토리/`SecurityConfig` 변경 없음(`git diff` 결과 없음) — 계획대로 계층 확장 없이 필드만 배관.
  - **테스트 파일 13개 diff 전수 확인**(`CacheConfigTest`, `BuyerMypageControllerTest`, `PaymentControllerTest`, `ProductControllerTest`, `SellerMypageControllerTest`, `TeamControllerTest`, `PaymentRepositoryTest`, `ProductCachingTest`, `SellerRevenueSummaryConcurrencyTest`, `SellerRevenueSummaryTest`, `TeamConcurrencyAtomicTest`, `TeamConcurrencyTest`, `TeamDeadlineServiceTest`) — 전부 `new Product(...)`/`product.update(...)`/`new ProductRegisterRequest(...)`/`new ProductResponse(...)`/`new ProductSummaryResponse(...)` 호출부에 `null` 인자 1개를 마지막에 추가한 것뿐. 기존 assertion·검증 로직·테스트 의도 변경 없음, 삭제되거나 완화된 검증 없음(라인 단위로 diff 대조 완료).
  - `docs/api/product.md`: `GET /api/products`(목록)·`GET /api/products/{id}`(상세)·`POST /api/products`(요청/응답) 예시에 `imageUrl` 필드 반영 확인, 실제 DTO 필드명(`imageUrl`, String, nullable)과 일치. `PUT`은 "POST와 동일" 기존 문서 구조를 그대로 유지(원문 수정 불필요하다는 판단이 맞음).
- 원인: 해당 없음(PASS, 회귀 없음).
- 증거(API 문서 샘플, `docs/api/product.md`):
  - `GET /api/products` 목록 응답: `"imageUrl": "https://images.pexels.com/photos/2294477/pexels-photo-2294477.jpeg"`
  - `POST /api/products` 요청 필드표: `imageUrl | String | N | 상품 이미지 URL (없으면 프론트에서 그라디언트 placeholder 표시)`
- 판정: PASS. 계산적 평가(compileJava/test)와 추론적 평가(계획 대조, 테스트 파일 diff 안전성) 모두 통과.
