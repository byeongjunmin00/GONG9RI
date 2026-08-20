# 상품 이미지 (product/image) — Design

## 개요

상품 하나에 **이미지 여러 장**을 붙인다. 이미지는 **판매자가 올린 파일**일 수도, **외부 URL**일 수도 있고, 화면과 API는 그 둘을 구분하지 않는다.

1차(2026-08-17)에는 `Product.imageUrl` 문자열 컬럼 하나뿐이라 **구조적으로 한 장**이었고, 파일 업로드도 없었다. 파일 업로드를 안 한 이유는 **저장할 곳이 없어서**였다 — 컨테이너 파일시스템은 재배포하면 초기화된다. 2차(2026-08-20)에 이 전제가 이미 깨져 있다는 걸 확인해서(프로젝트가 이미 Railway 볼륨을 쓰고 있었다) 두 제약을 함께 해소했다.

## 저장 위치 — Railway 볼륨

`gong9ri-volume`을 GONG9RI 서비스의 `/data`에 마운트하고 `UPLOAD_DIR=/data/uploads`로 지정한다. 볼륨은 재배포와 무관하게 유지된다.

| 항목 | 실측값 (2026-08-20 프로덕션) |
|---|---|
| 마운트 | `/data`, `/dev/zd3360` |
| 가용 용량 | **434MB** (할당 500MB — `lost+found`와 파일시스템 오버헤드를 뺀 실제 값) |

### 알려진 한계 (정직하게 기록)

- **CDN이 없다.** 이미지 요청이 전부 앱 서버를 거친다. 현재 규모에선 문제없지만 트래픽이 크면 부담이다.
- **다중 인스턴스로 늘리면 깨진다.** 인스턴스마다 다른 볼륨을 보게 된다. 현재 단일 인스턴스 전제이며, 확장이 필요해지면 오브젝트 스토리지로 옮겨야 한다.
- 용량이 유한하다. 아래 "용량" 참고 — 축소 저장 덕에 데모/과제 규모에는 충분하다.

## API / 인터페이스

| 엔드포인트 | 설명 |
|---|---|
| `POST /api/seller/products/images` | multipart 파일 1개 업로드 → `{"url":"/uploads/2026/08/{uuid}.jpg"}`. 판매자만 |
| `GET /uploads/**` | 업로드 파일 서빙(정적 리소스 핸들러, `UPLOAD_DIR` 기준) |
| `POST /api/products`, `PUT /api/products/{id}` | `imageUrls`(List&lt;String&gt;, 선택) 추가. **생략하면 기존처럼 `imageUrl` 한 장만** 쓴다 |
| `GET /api/products/{id}` | 응답에 `imageUrls` 추가 |

## 데이터 모델

- `product_image` 신규 — 상품 1 : 이미지 N, `display_order`로 순서를 갖는다(첫 번째 = 대표). 인덱스 `idx_product_display_order(product_id, display_order)`.
- `product.image_url`은 **대표 이미지로 그대로 유지**한다. 의도적인 비정규화다 — 목록 화면이 상품마다 이미지 테이블을 조회하면 N+1이 된다.
- **마이그레이션 없음.** `product_image`가 비어 있으면 `imageUrl` 한 장으로 폴백한다. 기존 상품 35개가 외부 URL(pexels)을 쓰고 있어서 이 하위호환이 설계의 제약이었다.

## 규칙 / 검증

### 업로드는 신뢰할 수 없는 입력이다

파일 업로드는 전형적인 공격 표면이라, 테스트도 정상 동작보다 **거절 경로**에 더 많다.

- **클라이언트가 보낸 파일명을 쓰지 않는다** — 서버가 UUID로 새 이름을 만든다(경로 탈출 차단).
- **확장자·Content-Type 선언을 믿지 않는다** — `ImageIO.read()`로 실제 디코딩되는지만 본다. 확장자만 바꾼 파일은 여기서 걸린다.
- **크기 상한을 서버가 강제한다**(5MB). 프론트 검증은 우회 가능하다.
- **재인코딩이 EXIF를 떨궈낸다** — 사진에 남은 위치정보가 공개되는 걸 막는다(부수효과지만 중요).
- 리소스 루트를 절대경로로 정규화해서 임의 경로가 노출되지 않게 한다.

### 용량 — 추정이 아니라 실측

원본을 그대로 저장하면 `5MB × 5장 × 20개 상품 = 500MB`로 **상품 20개에서 볼륨이 찬다**. 그래서 저장 시 긴 변을 1600px로 줄이고 JPEG(품질 0.82)로 재인코딩한다.

| 입력 | 저장 결과 |
|---|---|
| 4000×3000 / 1.96MB JPEG (노이즈 많은 합성 이미지 = JPEG 최악 조건) | **1600×1200 / 498KB** |
| 1200×900 PNG (단색) | 17.7KB (JPEG로 재인코딩) |

한 장 500KB를 최악값으로 잡으면 434MB에 **약 870장 = 상품 170개(5장씩)**. 실제 사진은 합성 노이즈 이미지보다 훨씬 잘 압축되므로 이보다 더 담긴다.

### 용량 초과는 400이지 500이 아니다

**톰캣 multipart 파서가 컨트롤러 진입 전에 거절**하기 때문에, 서비스 계층의 5MB 검사(`ProductImageStorage`)는 이 경우 **도달하지 못한다**. 핸들러가 없으면 `MaxUploadSizeExceededException`이 catch-all로 흘러 500이 된다(5.5MB 사진으로 실제 재현). `GlobalExceptionHandler`에서 잡아 `IMAGE_FILE_TOO_LARGE`(400)로 맞춘다.

> 이 회귀 테스트는 **실제 톰캣을 띄워야 한다**(`RANDOM_PORT`). MockMvc는 multipart 파서를 타지 않아 크기 제한이 아예 적용되지 않고, **핸들러가 없어도 통과해버려 회귀를 못 잡는다.** 인증도 실제로 해야 한다 — 비로그인은 시큐리티가 먼저 401로 끊어 파서까지 가지 않는다.

### 프론트

- 판매자 화면은 파일 선택 시 **즉시 업로드**한다(폼 제출을 기다리지 않아 미리보기를 바로 보여준다). 여러 장은 **순차 업로드** — 고른 순서가 곧 표시 순서이고 첫 장이 대표라 순서가 의미를 갖는다.
- 업로드만 `window.Api`가 아니라 `fetch`를 직접 쓴다. multipart는 브라우저가 boundary를 포함한 `Content-Type`을 스스로 만들어야 해서 직접 지정하면 오히려 깨진다.
- 상세 페이지 갤러리는 **사진이 1장이면 화살표·카운터·썸네일 줄을 전부 숨겨** 이전과 똑같이 보인다.

## 관련 코드 위치

- `entity/ProductImage.java`, `repository/ProductImageRepository.java`
- `service/ProductImageStorage.java`(저장·검증·축소), `controller/ProductImageController.java`
- `config/WebMvcConfig.java`(`/uploads/**` 서빙), `application.yaml`(`app.upload.dir`, `spring.servlet.multipart`)
- `common/exception/GlobalExceptionHandler.java`(`MaxUploadSizeExceededException`)
- `static/js/product-image-picker.js`(등록·수정 공용), `static/js/product.js`(갤러리)
- 경위: `docs/dev/product/image/changes/001-image.md`, `002-multi-upload.md`

> **주의:** `app.upload.dir`은 `src/test/resources/application.yaml`에도 있어야 한다. 테스트 설정 파일은 메인 설정을 **병합이 아니라 대체**하므로, 여기 빠뜨리면 컨텍스트 로딩이 실패해 전체 테스트가 무너진다(실제로 360개가 깨졌다).
