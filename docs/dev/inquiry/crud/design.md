# 상품 상세페이지 문의하기 (inquiry/crud) — Design

## 개요

상품 상세페이지에서 로그인한 회원이면 누구나(구매 이력과 무관하게) 상품에 텍스트 문의를 남길 수
있고, 그 상품을 등록한 판매자 본인만 문의에 답변을 등록·수정·삭제할 수 있다. 리뷰(`review`)와
달리 "구매 전 질문"이 핵심 용도라 구매 이력 게이트가 없고, 환불 요청(`refund/request`)과 유사하게
하나의 레코드가 "요청(문의) → 결정(답변)" 상태를 갖는다.

- 문의 목록은 비로그인 포함 누구나 조회 가능(상품 상세의 공개 정보, 리뷰와 동일).
- 문의 작성자 본인만 자신의 문의를 수정/삭제할 수 있고, **답변이 이미 달린 문의는 수정/삭제할 수
  없다**(질문-답변 정합성 보존).
- 문의 1건당 답변은 0개 또는 1개(스레드형 다중 답변 없음). 답변 삭제는 문의 자체는 남기고 답변만
  지워 "미답변" 상태로 되돌린다.
- 비밀글, 알림 연동, 판매자/구매자 마이페이지 목록, 페이지네이션은 이번 스코프에 없다(향후 확장
  후보, 아래 "제외 범위" 참고).

## API / 인터페이스

7개 엔드포인트. 상세 요청/응답/에러는 `docs/api/inquiry.md` 참조.

| 메서드 | 경로 | 설명 | 인증 |
|---|---|---|---|
| GET | `/api/products/{productId}/inquiries` | 문의 목록 조회 | 불필요(공개) |
| POST | `/api/products/{productId}/inquiries` | 문의 작성 | 필요(로그인만, role 무관) |
| PUT | `/api/inquiries/{inquiryId}` | 문의 내용 수정 | 필요(작성자 본인) |
| DELETE | `/api/inquiries/{inquiryId}` | 문의 삭제 | 필요(작성자 본인) |
| POST | `/api/inquiries/{inquiryId}/answer` | 판매자 답변 등록 | 필요(그 상품 판매자 본인) |
| PUT | `/api/inquiries/{inquiryId}/answer` | 판매자 답변 수정 | 필요(그 상품 판매자 본인) |
| DELETE | `/api/inquiries/{inquiryId}/answer` | 판매자 답변 삭제 | 필요(그 상품 판매자 본인) |

- `SecurityConfig`는 별도 규칙을 추가하지 않는다. 기존 `.requestMatchers(HttpMethod.GET,
  "/api/products/**").permitAll()`이 **GET 메서드에만** 적용되므로 문의 목록 조회(GET)만 자동으로
  커버하고, 작성(POST)·수정/삭제(PUT/DELETE, 경로가 `/api/inquiries/**`라 애초에 겹치지도 않음)는
  `anyRequest().authenticated()`로 인증이 요구된다.

## 데이터 모델

- `inquiry` 테이블 — 상세: `docs/db/inquiry.md`
- 관계: `inquiry.product_id → product.id`, `inquiry.member_id → member.id`(작성자),
  `inquiry.answered_by → member.id`(답변자, nullable, 항상 `product.seller_id`와 같아야 함 — 서비스
  레이어에서 검증)
- 인덱스: `idx_product`(product_id)만 — "내 문의 목록" 조회 기능이 스코프에 없어 `member_id` 전용
  인덱스는 추가하지 않음(review와 동일한 근거).

## 규칙 / 검증

- **작성 자격**: 로그인만 필요, 구매 이력 불필요(리뷰와 다름). `content`는 `@NotBlank @Size(max =
  1000)`.
- **수정/삭제(작성자)**: 작성자 본인만(`FORBIDDEN`), 아직 답변이 없는 문의만
  (`INQUIRY_ALREADY_ANSWERED`, 409).
- **답변 등록/수정/삭제(판매자)**: 그 상품을 등록한 판매자 본인만(`FORBIDDEN`, 구매자 계정·다른
  상품의 판매자 모두 거절). 등록은 아직 답변이 없을 때만(`INQUIRY_ALREADY_ANSWERED`, 409). 수정/
  삭제는 이미 답변이 등록돼 있을 때만(`ANSWER_NOT_FOUND`, 404).
- **존재하지 않는 리소스**: 상품 없음(`PRODUCT_NOT_FOUND`, 404), 문의 없음(`INQUIRY_NOT_FOUND`,
  404).
- **삭제 정책**: 하드 삭제(`deleted_at` 없음), 리뷰와 동일.
- **응답 형식**: 공통 규칙(`docs/api/README.md`)을 따름 — 성공 `{ "success": true, "data": {...}
  }`, 실패 `{ "success": false, "code": "...", "message": "..." }`.

## 프론트엔드

- `product.html`: `inquiries-section`(목록 + 항상 노출되는 작성/수정 폼)은 이제 "상품정보/리뷰/문의"
  탭 UI의 `inquiries-panel` 안에 배치된다(리뷰 패널과 나란히가 아니라 탭으로 전환해서 봄). 내부
  DOM id·로직은 그대로이며 감싸는 위치만 바뀌었다. 탭 구조의 SSOT는
  `docs/dev/frontend/product-detail/design.md` — 이 문서는 문의 콘텐츠의 배치만 최신화한다.
- `product.js`:
  - `loadInquiries()`가 `GET /api/products/{id}/inquiries`로 목록을 불러온다. init() 초기 호출 +
    `loadProduct()` 성공 후(판매자 판별용 `currentSellerId` 확정 후 재조회) + `gong9ri:auth-
    resolved` 도착 시, 총 세 지점에서 트리거된다.
  - 작성/수정 폼은 로그인 여부와 무관하게 항상 노출하고, 비로그인 제출은 서버의 401
    (`UNAUTHORIZED`) 응답을 그대로 안내한다.
  - 각 문의 항목에서 작성자 본인이고 미답변이면 수정/삭제 버튼을, 로그인한 회원이 그 상품의
    판매자(`currentMemberId === currentSellerId`, `currentSellerId`는 `product.sellerId`로 채움,
    review가 쓰는 `gong9ri:auth-resolved`의 `currentMemberId` 패턴 재사용)면 답변 등록/수정/삭제
    UI를 개별 인라인 폼으로 노출한다(review처럼 페이지에 폼 하나를 재사용하지 않고, 여러 문의에
    동시에 답변 가능하게 문의 항목마다 별도 생성).

## 제외 범위 (다음 단계 후보)

- 비밀글(작성자/판매자만 볼 수 있는 문의) — 이번 스코프는 전체 공개만 지원.
- 알림 연동 — `notification` 테이블이 `related_team_id`만 가져 스키마 변경이 필요, 별도 계획으로
  분리.
- 판매자 "내 상품 문의함" 모아보기, 구매자 "내 문의 목록" — 마이페이지 목록 없음(리뷰와 동일한
  선례).
- 페이지네이션 — 목록 전체를 한 번에 반환(리뷰와 동일한 리스크 상속).

## 관련 코드 위치

- `entity/Inquiry.java`
- `repository/InquiryRepository.java` — `findByProductIdOrderByCreatedAtDesc`
- `service/InquiryService.java` — `list`/`create`/`update`/`delete`/`registerAnswer`/
  `updateAnswer`/`deleteAnswer`, 소유자/판매자/답변상태 검증(`requireOwner`/`requireSeller`/
  `requireNotAnswered`/`requireAnswered`)
- `controller/InquiryController.java`
- `dto/{InquiryCreateRequest,InquiryAnswerRequest,InquiryResponse,InquiryListResponse}.java`
- `common/exception/ErrorCode.java` — `INQUIRY_NOT_FOUND`(404)/`INQUIRY_ALREADY_ANSWERED`(409)/
  `ANSWER_NOT_FOUND`(404) 추가
- 프론트: `product.html`/`js/product.js`(`inquiries-section`, `loadInquiries`/
  `handleInquiryFormSubmit`/`createAnswerForm` 등)
- 테스트:
  - `controller/InquiryControllerTest.java`(25케이스) — 비로그인 목록조회 200/작성 401, 구매
    이력 없는 작성 201, 타인 수정/삭제 403, 답변된 문의 수정/삭제 409, 판매자 아닌 답변등록
    403(구매자/타판매자), 이미 답변된 문의 재등록 409, 답변 없는 문의 수정/삭제 404
    `ANSWER_NOT_FOUND`, 판매자 본인 답변 수정/삭제 성공 및 타판매자 403, 존재하지 않는 상품/문의
    404.
  - `service/InquiryServiceTest.java`(15케이스) — Mockito 순수 단위, 소유권/판매자/답변상태 검증
    로직 위주.
