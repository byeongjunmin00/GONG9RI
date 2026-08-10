# 상품 이미지 (product/image) — Design

## 개요

`Product`가 `imageUrl`(nullable, String, `length=500`) 필드를 실제로 채우고 응답에 노출한다. 판매자가 등록/수정 시 이미지 URL을 선택 입력하면 목록·상세 조회 응답에 그대로 반영된다. 컬럼 자체는 이전부터 있었지만 어떤 코드 경로도 값을 채우지 않는 죽은 컬럼이었고, 이 작업으로 생성자·`update()`부터 DTO·응답까지 배관이 연결됐다. 실제 파일 업로드는 지원하지 않는다 — 저장/표시는 URL 문자열뿐이다.

## API / 인터페이스

- `GET /api/products`(목록), `GET /api/products/{id}`(상세), `POST /api/products`(등록), `PUT /api/products/{id}`(수정) — 4개 엔드포인트 모두 요청/응답에 `imageUrl`(String, nullable) 포함. 상세: `docs/api/product.md`.

## 데이터 모델

- `product.image_url` 컬럼 — 상세: `docs/db/product.md`. 마이그레이션 불필요(컬럼은 이미 존재, nullable이라 기존 데이터 영향 없음).

## 규칙 / 검증

- `imageUrl`은 선택 입력이며 URL 형식 검증 애노테이션이 없다 — 서버는 문자열을 그대로 저장할 뿐, 실제 이미지를 가리키는지 검증하지 않는다.
- 캐시(`ProductService.list()`/`detail()`의 `@Cacheable`)는 필드 추가로 캐시 키·무효화 전략에 영향 없음 — 기존 `register()`/`update()` 시 `@CacheEvict`/`@Caching` 로직을 그대로 재사용한다.
- `controller`/`repository`/`SecurityConfig`는 변경 없음(기존 필드에 값을 채우는 배관 확장이라 계층 추가 없음).

## 관련 코드 위치

- `entity/Product.java` — 생성자·`update()`에 `imageUrl` 파라미터 추가
- `dto/ProductRegisterRequest.java`, `dto/ProductResponse.java`, `dto/ProductSummaryResponse.java` — `imageUrl` 필드 추가 + `of()` 매핑
- `service/ProductService.java` — `register()`/`update()`에서 `imageUrl` 전달
- 경위: `docs/dev/product/image/changes/001-image.md`, 실행 로그: `docs/logs/product/image/001-image.md`
