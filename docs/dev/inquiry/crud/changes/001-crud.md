# 상품 상세페이지 문의하기

대상: inquiry/crud (완료 시 `docs/dev/inquiry/crud/design.md` + `changes/001-crud.md`로 정리)
담당: 전용운

## 배경 / 요구

상품 상세페이지(`product.html`)에 리뷰(`review`)와 환불 요청(`refund/request`)은 이미 있지만, 구매 전
질문(배송/옵션 등)을 남길 수 있는 "문의하기" 기능은 아직 없다. 사용자가 상품 상세페이지에 문의 기능을
추가해 달라고 요청했다.

- `docs/ERD.md`, `docs/WIREFRAME.md`에는 "문의" 관련 내용이 없다(신규 개념).
- `docs/dev/ai/buyer-chatbot`(구매자 챗봇)은 일반 자연어 Q&A 도우미이지, 특정 상품에 대해 판매자가
  직접 답변하는 공개 Q&A 게시판이 아니다 — 겹치지 않는 별개 기능이다.
- 가장 가까운 참고 모델은 `review`(entity/controller/service 3계층 구조, 상품 상세 공개 조회 +
  본인 CRUD 패턴)와 `refund/request`(하나의 레코드가 "요청 → 결정(승인/거절)" 상태를 갖는 패턴, 이번
  기능의 "문의 → 답변" 구조와 유사).

## 설계

### 개념 범위 (포함 / 제외를 명확히 함 — 휴먼 게이트에서 조정 가능)

**포함**
- 로그인한 회원이면 구매 이력 없이 상품에 문의(텍스트) 작성 가능 — 리뷰와 달리 "구매 전 질문"이 핵심
  용도이므로 구매 이력 게이트를 걸지 않는다.
- 문의 목록은 비로그인 포함 누구나 조회 가능(상품 상세 공개 정보, 리뷰와 동일).
- 작성자 본인만 자신의 문의를 수정/삭제 가능 — 단, **답변이 이미 달린 문의는 수정/삭제 불가**(질문-답변
  정합성 보존, 아래 "정책 확인 필요" 참고).
- 그 상품을 등록한 판매자 본인만 답변 등록/수정/삭제 가능. 문의 1건당 답변은 0~1개(스레드형 다중 답변
  없음).
- 답변 삭제는 문의 자체는 남기고 답변만 지워 "미답변" 상태로 되돌린다.
- 상품 상세 페이지(`product.html`/`product.js`) 통합: 문의 목록/작성 폼(누구나 로그인 시 노출) + 답변
  폼(현재 로그인한 회원이 그 상품의 판매자일 때만 노출, `product.sellerId === currentMemberId` 비교로
  판별 — `review` 섹션이 이미 쓰는 `gong9ri:auth-resolved` 이벤트의 `currentMemberId` 패턴을 그대로
  재사용).

**제외 (다음 단계 후보로 남김, 지금 만들지 않음)**
- **비밀글(작성자/판매자만 보이는 문의)** — 이번 스코프는 전체 공개만 지원한다.
- **알림 연동** — 문의 작성 시 판매자에게, 답변 등록 시 작성자에게 알림을 남기는 것은 자연스러운
  확장이지만, 기존 `notification` 테이블이 `related_team_id`만 갖고 있어 공유 테이블 스키마 변경이
  필요하다(`docs/db/notification.md`). 이번 라운드는 문의 CRUD 자체에 집중하고 알림은 별도 계획으로
  분리한다.
- **판매자 마이페이지 "내 상품 문의함" 모아보기** — 이번 스코프는 판매자가 각 상품 상세 페이지를 직접
  방문해 답변하는 방식만 지원한다. 여러 상품에 걸친 미답변 문의를 한곳에 모아보는 목록(`GET
  /api/seller/mypage/inquiries`류)은 만들지 않는다.
- **구매자 마이페이지 "내 문의 목록"** — 리뷰도 마이페이지에 별도 목록이 없는 것과 동일한 선례를 따름.
- **페이지네이션** — 리뷰와 동일하게 목록 전체를 한 번에 반환한다(아래 리스크 참고).

### 정책 확인 필요 (계획 단계의 명시적 결정, 승인 시 확정)

1. **문의 작성 자격**: 구매 이력 불필요, 로그인만 필요(리뷰와 다름).
2. **답변 후 수정/삭제 금지**: 답변이 달린 문의는 작성자가 더 이상 손댈 수 없다. (반대 선택지: 답변 후에도
   수정 허용 — 이 경우 판매자 답변과 질문 내용이 어긋날 수 있어 채택하지 않음.)
3. **작성자 노출 방식**: 리뷰와 동일하게 실명(닉네임) 그대로 노출, 별도 마스킹 없음.
4. **삭제 정책**: 하드 삭제(리뷰와 동일, `deleted_at` 없음).

### 영향 계층

- **entity**: `Inquiry` 신규 (`product` FK, `member` FK 작성자, `content`, `answerContent`,
  `answeredBy` FK nullable, `answeredAt` nullable, `createdAt`/`updatedAt`)
- **repository**: `InquiryRepository` 신규 (상품별 목록 조회용 쿼리 메서드)
- **service**: `InquiryService` 신규 (작성/목록/수정/삭제 + 답변 등록/수정/삭제, 권한 검증)
- **controller**: `InquiryController` 신규
- **dto**: `InquiryCreateRequest`/`InquiryAnswerRequest`(둘 다 `content` 하나만 가짐, 같은 형태일 수
  있음)/`InquiryResponse`/`InquiryListResponse`
- **common/exception**: `ErrorCode`에 `INQUIRY_NOT_FOUND`/`INQUIRY_ALREADY_ANSWERED`/`ANSWER_NOT_FOUND`
  3종 추가 (`docs/api/inquiry.md` "신규 에러 코드" 참고)
- **frontend**: `product.html`(문의 섹션 마크업) + `product.js`(목록 조회/작성/수정/삭제/답변 폼 핸들러) —
  `review` 섹션과 동일한 구조를 그대로 병렬로 추가

### API / DB 계약

- API 명세: `docs/api/inquiry.md` (신규, 이번 Plan에서 작성 완료)
- 테이블 명세: `docs/db/inquiry.md` (신규, 이번 Plan에서 작성 완료)

## 태스크

- [ ] `Inquiry` 엔티티 + `InquiryRepository` (docs/db/inquiry.md 스키마대로)
- [ ] `ErrorCode`에 3종 코드 추가
- [ ] `InquiryService`: 작성/목록/수정/삭제 + 답변 등록/수정/삭제, 권한·상태 검증
- [ ] `InquiryController`: `docs/api/inquiry.md`의 7개 엔드포인트
- [ ] `product.html`/`product.js`: 문의 섹션(목록/작성 폼/조건부 답변 폼) — `review` 섹션 패턴 재사용
- [ ] 단위/통합 테스트(`InquiryServiceTest`, `InquiryControllerTest`) — 아래 평가 기준 시나리오 커버
- [ ] Evaluate 통과 후: `docs/dev/inquiry/crud/design.md` 작성 + 이 문서를 `changes/001-crud.md`로 채번 이동

## 평가(통과) 기준

- `./gradlew test` 전체 통과.
- 다음 시나리오가 테스트로 검증됨:
  - 비로그인 사용자: 목록 조회는 200, 작성 시도는 401.
  - 로그인한 구매자(해당 상품 구매 이력 없음): 문의 작성 성공(201) — 리뷰와 달리 구매 이력 게이트 없음.
  - 문의 작성자 본인만 수정/삭제 가능, 타인이 시도하면 403.
  - 답변이 등록된 문의를 작성자가 수정/삭제 시도 → 409 `INQUIRY_ALREADY_ANSWERED`.
  - 그 상품을 등록한 판매자만 답변 등록 가능(타 판매자/구매자 계정은 403), 이미 답변된 문의에 재등록
    시도 시 409 `INQUIRY_ALREADY_ANSWERED`.
  - 답변이 없는 문의를 수정/삭제 시도 시 404 `ANSWER_NOT_FOUND`.
  - 판매자 본인이 자신이 등록한 답변을 수정/삭제 가능, 다른 판매자는 403.
  - 존재하지 않는 상품/문의 접근 시 404(`PRODUCT_NOT_FOUND`/`INQUIRY_NOT_FOUND`).
  - `product.html`에서 문의 목록·작성 폼이 렌더링되고, 로그인한 회원이 그 상품의 판매자일 때만 답변
    폼이 노출됨(수동 확인 또는 프론트 테스트).

## 리스크 / 전제

- 신규 테이블(`inquiry`)이라 로컬/스테이징 DB에 스키마가 반영되어야 한다(반영 방식은 기존 프로젝트
  관례를 따르며 이 계획에서 규정하지 않음).
- 목록에 페이지네이션이 없어 문의가 많이 쌓이는 상품은 응답 크기가 커질 수 있다 — 리뷰가 이미 갖고
  있던 리스크를 그대로 상속한다.
- 문의 작성에 구매 이력 검증이 없어 리뷰보다 스팸/어뷰징 여지가 크다 — 이번 스코프에서 신고/차단
  기능은 만들지 않고 리스크로만 인지한다.
- `docs/ERD.md`의 "테이블 목록"이 이미 `review`/`refund_request`/`chat_*` 등 최근 테이블 다수를
  반영하지 못한 기존 드리프트가 있다(이번 팀 작업 관례로 보임). `inquiry`도 이번에 추가 반영하지
  않으면 같은 드리프트가 누적된다 — 이 계획에서 해소하지 않고 리스크로만 기록한다.
- 알림 연동을 포함하면 공유 테이블(`notification`) 스키마 변경이 필요해 이번 스코프보다 커진다(위
  "제외" 항목 참고) — 별도 후속 계획으로 분리하는 것을 전제로 한다.

## 관련 문서

- `docs/db/inquiry.md` (신규)
- `docs/api/inquiry.md` (신규)
- 참고 모델: `docs/db/review.md`, `docs/api/review.md`, `docs/db/refund_request.md`,
  `docs/api/refund.md`, `docs/dev/frontend/product-detail/design.md`
