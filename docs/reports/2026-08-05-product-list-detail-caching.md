# 개발 보고서 — 상품 목록·상세(product/list, product/detail) Redis 캐싱

- **작성일**: 2026-08-05
- **작업자**: 전용운
- **대상 기능**: `product/crud` (기존 기능 수정 — 신규 기능 아님)
- **관련 문서**: [계획(완료 이관)](../dev/product/crud/changes/002-caching.md) · [design.md](../dev/product/crud/design.md) · [실행 로그](../logs/product/crud/002-caching.md) · [캐싱 정책](../policy/caching.md) · [선행 작업 보고서(seller-revenue)](2026-08-05-mypage-seller-revenue-caching.md)

---

## 1. 배경 / 목적

`docs/policy/caching.md`가 지정한 캐싱 대상 두 항목(`product/list`·`product/detail`, `mypage/seller-revenue`) 중 나머지 하나. `mypage/seller-revenue`는 이미 구현 완료된 상태였고, 이번에 남은 `product/list`·`product/detail`을 마저 구현했다.

## 2. 계획 단계에서 사용자와 협의해 확정한 사항

정책 문서의 무효화 트리거 표엔 `product/list`에 대해 `product/update`·`product/delete`만 적혀있고 **`product/register`(신규 등록)가 빠져 있었다.** 목록 조회 쿼리(`findAllWithSeller`)에 `ORDER BY`가 없어 새 상품이 어느 페이지에 나타날지 불확실하고, 이 목록은 비로그인 포함 모두가 보는 공개 카탈로그라 무효화하지 않으면 "방금 등록한 상품이 목록에 안 보이는" 문제가 TTL 만료 전까지 노출될 수 있다는 점을 사용자에게 설명했고, **register도 목록 캐시 무효화 대상에 포함하기로 합의**했다.

## 3. 설계 요약

| 캐시 | 대상 메서드 | 키 | 무효화 트리거 |
|---|---|---|---|
| `productList` | `ProductService.list(page, size)` | `page` + `size` | `register`(전체), `update`(전체), `delete`(전체) |
| `productDetail` | `ProductService.detail(productId)` | `productId` | `update`(해당 productId), `delete`(해당 productId) |

- **목록 캐시는 "전체 무효화" 방식**을 택했다 — 정렬 조건이 없어 특정 상품이 어느 페이지에 속하는지 알 수 없어, 부분 무효화가 불가능했기 때문. 계획 단계에서 사용자에게 미리 리스크로 공유한 트레이드오프(상품 변경이 잦으면 목록 캐시 적중률이 떨어질 수 있음)다.
- **TTL**: 목록·상세 각 30분. `mypage/seller-revenue`(10분)보다 길게 잡았는데, 이유는 무효화가 정밀하지 않은(전체 무효화) 만큼 캐시가 오래 유지되는 게 상대적으로 더 중요하다고 판단했기 때문.
- **선행 작업(seller-revenue)에서 확정한 직렬화 방식을 그대로 재사용**: 캐시별로 타입을 고정한 `JacksonJsonRedisSerializer<>(대상클래스.class)`. 이번엔 이 방식을 처음부터 적용해서, seller-revenue 때 겪었던 두 결함(비직렬화 예외, 타입 소실로 인한 `LinkedHashMap` 역직렬화)이 **재발하지 않았다.**
- **계층 제약**: 캐싱 로직은 `ProductService.java`에만 — Controller·Repository 미개입.
- **구현 방식**: seller-revenue 때는 sellerId가 메서드 본문에서 조회한 값이라 `CacheManager`를 직접 호출해야 했지만, 이번엔 `productId`가 메서드 파라미터 자체라 `@Cacheable`/`@CacheEvict`/`@Caching` 애노테이션만으로 선언적으로 표현 가능했다(더 단순함).

## 4. 변경 파일

| 파일 | 변경 내용 |
|---|---|
| `src/main/java/.../config/CacheConfig.java` | `productList`/`productDetail` 캐시 빈 2개 추가 (TTL 30분, 타입 고정 JSON 직렬화기) |
| `src/main/java/.../service/ProductService.java` | `list()`/`detail()`에 `@Cacheable`, `register()`에 `@CacheEvict(allEntries=true)`, `update()`/`delete()`에 `@Caching`으로 상세+목록 동시 무효화 |
| `src/test/java/.../config/CacheConfigTest.java` | 목록/상세 캐시 직렬화 왕복 검증 케이스 2건 추가(기존 1건 + 신규 2건 = 3건) |
| `src/test/java/.../service/ProductCachingTest.java` **(신규)** | 캐시 히트 2건 + register/update/delete 무효화 3건, 총 5케이스 |
| `docs/dev/product/crud/design.md` | 캐싱 구현 내용으로 최종 갱신 |
| `docs/dev/product/crud/changes/002-caching.md` | 계획 문서 채번 이관 |

## 5. 테스트

`ProductCachingTest.java` — `mypage/seller-revenue` 때 확립한 패턴(무효화 경로를 거치지 않고 데이터를 직접 바꿔 "진짜 캐시된 값"인지 증명)을 그대로 적용:

1. 목록 캐시 히트: 재조회 전 레포지토리를 우회해 상품을 추가해도 이전 목록이 그대로 반환됨을 확인
2. 상세 캐시 히트: 동일한 방식으로 상세 정보 변경 후에도 이전 값 유지 확인
3. `register` 후 목록 재조회 시 새 상품 반영(무효화 확인)
4. `update` 후 해당 상품 상세·목록 재조회 시 최신 값 반영
5. `delete` 후 목록에서 빠지고 상세 조회 시 `404 PRODUCT_NOT_FOUND`

## 6. 구현 중 발견된 이슈 (Generate 단계 자체 발견 및 수정)

- **캐시 오염**: `@SpringBootTest`가 캐시 빈(싱글톤)을 테스트 메서드 간 공유하는데, DB는 `@Transactional`로 롤백돼도 캐시는 롤백되지 않아 이전 테스트의 캐시 값이 다음 테스트로 새어 들어가는 문제가 실제로 재현됐다. 각 테스트가 서로 다른 `size` 값(101~104)을 써서 캐시 키를 격리하는 방식으로 해결했다.
- Evaluate에서 이 해결 방식에 대해 "현재는 동작하지만 근본 해결은 아닌 임시방편(테스트가 더 늘어나면 값 재사용 시 재발 가능)"이라는 평가를 남겼다 — 통과 판정에는 영향 없었으나, 향후 테스트 추가 시 재점검이 필요한 리스크로 기록됨.

## 7. 검증 결과

로컬에 이미 구성된 Docker(`mysql:8`, `redis:7`, `mypage/seller-revenue` 작업 때 셋업)를 그대로 재사용해 실제 인프라로 검증했다.

| 항목 | 결과 |
|---|---|
| `ProductCachingTest` (신규, 5케이스) | ✅ 5/5 통과 |
| `CacheConfigTest` (3케이스, 기존 1 + 신규 2) | ✅ 3/3 통과 |
| `ProductControllerTest` (기존, 12케이스, 회귀 확인) | ✅ 12/12 통과 |
| `./gradlew build` (저장소 전체) | ✅ BUILD SUCCESSFUL, 전체 83개 테스트 전부 통과 |

## 8. 컨벤션·정책 준수 확인

- ✅ 캐싱 로직 Service 계층 한정
- ✅ TTL 안전장치 부여 (목록/상세 각 30분)
- ✅ 무효화 범위가 계획(register 포함 여부, update/delete의 상세+목록 동시 무효화)과 정확히 일치
- ✅ 계획 외 범위(검색/정렬 파라미터, Controller/Repository 변경) 없음
- ✅ 캐시 대상 DTO 4종(`ProductPageResponse`, `ProductResponse`, `ProductSummaryResponse`, `PriceTierResponse`) 직렬화 왕복 검증 완료

## 9. 남은 사항 / 참고

- `docs/policy/caching.md`가 지정한 캐싱 대상 두 항목(`product/list`·`product/detail`, `mypage/seller-revenue`)이 이번으로 **모두 구현 완료**됐다.
- 목록 캐시가 "전체 무효화" 방식이라 상품 변경이 잦은 환경에선 캐시 효과가 기대보다 낮을 수 있음 — 필요 시 부분 무효화(정렬 기준 추가 후 페이지 특정 등) 전략은 추후 별도 과제.
- 테스트의 캐시 격리 방식(메서드별 다른 size 값)은 임시방편이라, 이 캐시를 다루는 테스트가 더 늘어나면 `@BeforeEach` 캐시 clear 등 더 근본적인 방식 재검토 권장.
- 커밋/푸시는 아직 수행하지 않음 — 사용자 확인 후 진행 예정.
