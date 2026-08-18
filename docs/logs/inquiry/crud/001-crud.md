# 001-crud — 상품 상세페이지 문의하기 (로그)

## Attempt 1 — 2026-08-18

- 시도: 승인된 계획(`docs/dev/ongoing/product-inquiry.md`)대로 문의 CRUD + 판매자 답변 CRUD를
  구현. `review`(entity/repository/service/controller/dto 3~4계층, 공개 조회 + 본인 CRUD)와
  `refund/request`("요청→답변/결정" 상태 패턴)를 참고 모델로 그대로 따름.
  - `entity/Inquiry`: `product`/`member`(작성자) FK, `content`, `answerContent`(nullable),
    `answeredBy`(Member FK, nullable), `answeredAt`(nullable), `createdAt`/`updatedAt`
    (`@CreatedDate`/`@LastModifiedDate`, Member/Review와 동일한 팀 관례를 따름 — db 문서의
    "답변 등록/수정 시각과는 별개"라는 설명은 컬럼의 의미를 설명한 것으로 해석하고, 기존 코드베이스가
    이미 전역적으로 쓰는 `@LastModifiedDate` 패턴에서 벗어나지 않았다). `isAnswered()`는
    `answerContent != null`로 파생.
  - `repository/InquiryRepository`: `findByProductIdOrderByCreatedAtDesc` 하나만 추가(review와 동일
    패턴, "내 문의 목록" 조회가 스코프에 없어 `member_id` 인덱스/쿼리 메서드는 추가하지 않음).
  - `ErrorCode`에 `INQUIRY_NOT_FOUND`(404)/`INQUIRY_ALREADY_ANSWERED`(409)/`ANSWER_NOT_FOUND`(404)
    3종 추가.
  - `dto`: `InquiryCreateRequest`/`InquiryAnswerRequest`(둘 다 `@NotBlank @Size(max=1000) content`
    하나만 가짐 — review와 달리 문의 content는 API 문서상 필수라 `@NotBlank`를 명시), `InquiryResponse`
    (`answered` 파생 필드 포함), `InquiryListResponse`(count + 목록, review와 달리 평균 개념 없음).
  - `service/InquiryService`: `list`(상품 존재 검증만, 목록은 항상 200)/`create`(구매 이력 게이트
    없음)/`update`/`delete`(소유자 검증 → 미답변 검증 순서)/`registerAnswer`(판매자 검증 → 미답변
    검증 순서)/`updateAnswer`/`deleteAnswer`(판매자 검증 → 이미 답변됨 검증 순서). 판매자 검증은
    `inquiry.getProduct().getSeller().getId()`와 principal 비교(review의 `requireOwner`와 대칭되는
    `requireSeller`). `deleteAnswer`는 문의는 남기고 `answerContent`/`answeredBy`/`answeredAt`만
    `null`로 되돌림.
  - `controller/InquiryController`: `docs/api/inquiry.md`의 7개 엔드포인트를 review 컨트롤러와
    동일한 얇은 위임 패턴으로 구현. `GET /api/products/{id}/inquiries`는 기존 `SecurityConfig`의
    `GET /api/products/**` permitAll 규칙이 이미 커버해서 별도 보안 설정 변경 없음(review가 명시적으로
    추가한 것과 달리 이미 더 넓은 규칙이 존재해 중복 추가하지 않음, 범위 최소화).
  - `product.html`/`product.js`: `reviews-section` 바로 아래 `inquiries-section`을 병렬로 추가
    (목록 + 항상 노출되는 작성/수정 폼). 답변 UI는 review처럼 페이지에 폼 하나를 두지 않고 문의
    항목마다 개별 인라인 폼을 동적으로 생성(여러 문의에 동시에 답변 가능하게, design.md에 명시된
    범위는 아니었지만 review의 "폼 하나 재사용" 패턴이 판매자가 여러 문의에 답할 때는 부적합해서
    UX상 자연스러운 방식을 택함 — API 계약이나 정책엔 영향 없음). `currentSellerId`를
    `renderProduct()`에서 `product.sellerId`로 채우고, `currentMemberId === currentSellerId`일
    때만 각 문의 항목에 답변 등록/수정/삭제 버튼을 노출(review가 쓰는 `gong9ri:auth-resolved`
    `currentMemberId` 패턴 재사용). `loadInquiries()`는 init() 초기 호출 + `loadProduct()` 성공 후
    (currentSellerId 확정 후 재조회) + `gong9ri:auth-resolved` 도착 시, 총 세 지점에서 트리거.
  - 테스트: `InquiryServiceTest`(Mockito 순수 단위 테스트, 15케이스 — 소유권/판매자/답변상태 검증
    로직 위주, `PaymentRefundServiceTest` 스타일)와 `InquiryControllerTest`(MockMvc + 실제 DB,
    25케이스 — `ReviewControllerTest` 스타일로 계획 문서의 "평가(통과) 기준" 시나리오를 전부 커버:
    비로그인 목록조회 200/작성 401, 구매이력 없는 작성 201, 타인 수정/삭제 403, 답변된 문의
    수정/삭제 409, 판매자 아닌 답변등록 403(구매자/타판매자 둘 다), 이미 답변된 문의 재등록 409,
    답변 없는 문의 수정/삭제 404 ANSWER_NOT_FOUND, 판매자 본인 답변 수정/삭제 성공 및 타판매자 403,
    존재하지 않는 상품/문의 404).
  - 계획에 없는 범위(비밀글, 알림 연동, 마이페이지 목록, 페이지네이션)는 추가하지 않음.

- 결과: `./gradlew compileJava` 성공. `./gradlew test --tests "*Inquiry*"` →
  `InquiryControllerTest` 25/25, `InquiryServiceTest` 15/15 전부 통과. `./gradlew test`(전체
  스위트) → 312 tests, 0 failures, 0 errors, BUILD SUCCESSFUL.

- 증거(API 샘플, `InquiryControllerTest` 기준):
  - `GET /api/products/{id}/inquiries` (비로그인) → `200 {"data":{"count":1,"inquiries":[{"answered":false,...}]}}`
  - `POST /api/products/{id}/inquiries` (구매 이력 없는 로그인 구매자) → `201 {"data":{"content":"옵션 색상이 궁금해요","answered":false,...}}`
  - `PUT /api/inquiries/{id}` (답변 달린 문의, 작성자 본인) → `409 {"code":"INQUIRY_ALREADY_ANSWERED",...}`
  - `POST /api/inquiries/{id}/answer` (구매자 계정) → `403 {"code":"FORBIDDEN",...}`
  - `POST /api/inquiries/{id}/answer` (이미 답변된 문의, 판매자 본인) → `409 {"code":"INQUIRY_ALREADY_ANSWERED",...}`
  - `PUT /api/inquiries/{id}/answer` (미답변 문의) → `404 {"code":"ANSWER_NOT_FOUND",...}`
  - `DELETE /api/inquiries/{id}/answer` (판매자 본인) → `204`, 재조회 시 `answerContent`/`answeredAt` null, `content`는 유지.

- 다음: Evaluate 단계에서 계산적 평가(`./gradlew test`)와 추론적 평가(계획/컨벤션/정책 준수)를
  진행. 통과 시 `docs/dev/inquiry/crud/design.md` 작성 + 이 계획 문서를 `changes/001-crud.md`로
  채번 이동.

## Evaluate — 2026-08-18  ✅ PASS

- 결과: generator의 보고를 그대로 믿지 않고 직접 재실행해 검증함.
  - `./gradlew test --tests "*Inquiry*" --rerun-tasks` → `BUILD SUCCESSFUL`. 캐시(UP-TO-DATE)로
    속지 않으려 `--rerun-tasks`로 강제 재실행했고, XML 리포트(`build/test-results/test/TEST-*.xml`)를
    직접 집계해 `InquiryControllerTest` `tests="25" failures="0" errors="0"`,
    `InquiryServiceTest` `tests="15" failures="0" errors="0"`를 확인함(생성자 보고와 일치).
  - `./gradlew test --rerun-tasks`(전체 스위트) → `BUILD SUCCESSFUL`. 전체 40개 XML 리포트를 집계해
    `tests=312 skipped=0 failures=0 errors=0` 확인(생성자가 보고한 "312 tests, 0 failures"와 일치,
    로컬 MySQL 기반 통합 테스트 다수 포함 — DB 미가동으로 인한 실패는 없었음).
  - 결론: 계산적 평가 통과. generator 보고가 사실과 일치함.

- 원인(추론적 평가 — 코드를 직접 읽고 확인):
  - **계획 대비 시나리오 커버리지**: `docs/dev/ongoing/product-inquiry.md`의 "평가(통과) 기준" 9개
    시나리오를 `InquiryControllerTest`(25케이스)에서 테스트 이름이 아니라 실제 검증 내용(요청/응답
    상태코드·에러코드·DB 재조회 결과)까지 읽어 하나씩 대조함 — 전부 실제로 그 의미대로 구현돼
    있음을 확인:
    - 비로그인 목록조회 200(`list_success_public`)/작성 401(`create_unauthorized`, `jsonPath
      $.code == "UNAUTHORIZED"`) — 확인.
    - 구매 이력 없는 로그인 구매자 작성 201(`create_success_withoutPurchaseHistory`, 구매 이력
      세팅을 아예 하지 않고 성공하는지 확인) — 확인.
    - 본인만 수정/삭제, 타인 403(`update_forbidden_notOwner`/`delete_forbidden_notOwner`, 삭제
      쪽은 `assertTrue(...isPresent())`로 실제로 지워지지 않았음까지 확인) — 확인.
    - 답변된 문의 작성자 수정/삭제 409(`update_alreadyAnswered`/`delete_alreadyAnswered`, 삭제
      쪽은 row가 남아있음을 재조회로 확인) — 확인.
    - 판매자만 답변 등록(구매자 403 `registerAnswer_forbidden_buyer`, 다른 판매자 403
      `registerAnswer_forbidden_otherSeller`), 이미 답변된 문의 재등록 409
      (`registerAnswer_alreadyAnswered`) — 확인.
    - 답변 없는 문의 수정/삭제 시도 404 `ANSWER_NOT_FOUND`(`updateAnswer_answerNotFound`/
      `deleteAnswer_answerNotFound`) — 확인.
    - 판매자 본인 답변 수정/삭제 성공, 다른 판매자 403(`updateAnswer_success`/
      `updateAnswer_forbidden_otherSeller`/`deleteAnswer_success`/`deleteAnswer_forbidden_
      otherSeller`, `deleteAnswer_success`는 재조회로 `answerContent`/`answeredAt`이 null이 되고
      `content`는 유지됨을 검증) — 확인.
    - 존재하지 않는 상품/문의 404(`list_productNotFound`/`create_productNotFound`/
      `update_inquiryNotFound`/`registerAnswer_inquiryNotFound`/`deleteAnswer_inquiryNotFound`) —
      확인.
    - `InquiryServiceTest`(15케이스, Mockito 순수 단위)는 소유권/판매자/답변상태 검증 로직을
      독립적으로 재검증 — 컨트롤러 통합 테스트와 성격이 다르므로 중복이 아니라 보강임을 확인.
  - **SecurityConfig 직접 검증**(가장 중요한 판단 포인트): `SecurityConfig.java:57`의
    `.requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()`은 **GET 메서드에만**
    적용되는 규칙이라 `GET /api/products/{id}/inquiries`(문의 목록)만 커버하고, `POST
    /api/products/{id}/inquiries`(작성)는 메서드가 다르므로 이 규칙에 안 걸리고
    `anyRequest().authenticated()`(line 75)로 떨어져 인증을 요구한다. `PUT/DELETE
    /api/inquiries/{id}`, `POST/PUT/DELETE /api/inquiries/{id}/answer`는 애초에 경로가
    `/api/products/**`와 안 겹쳐(`/api/inquiries/**`) 같은 이유로 인증이 필요하다. 실제
    `create_unauthorized` 테스트가 비로그인 POST에 401을 받는 것으로 이 판단이 실증됨 — generator의
    "별도 보안 설정 변경 불필요" 판단이 코드·테스트 양쪽에서 맞는 것으로 확인됨.
  - **API/DB 계약 일치**: `docs/api/inquiry.md`의 7개 엔드포인트·에러코드(`INQUIRY_NOT_FOUND`
    404/`INQUIRY_ALREADY_ANSWERED` 409/`ANSWER_NOT_FOUND` 404)가 `ErrorCode.java` diff·
    `InquiryController.java`·`InquiryService.java`와 1:1로 일치함을 확인. `docs/db/inquiry.md`의
    컬럼(`product_id`/`member_id`/`content`/`answer_content`/`answered_by`/`answered_at`/
    `created_at`/`updated_at`)과 인덱스(`idx_product`만)가 `entity/Inquiry.java`와 일치.
  - **code-convention.md 준수**: 계층 분리(controller는 위임만, 비즈니스 로직 없음)/생성자 주입
    (`@RequiredArgsConstructor`, 필드 `final`)/`@Transactional(readOnly = true)` 기본 + 쓰기
    메서드에만 `@Transactional` 명시/적절한 상태코드(201/200/204/400/403/404/409)/SLF4J 로깅
    (`@Slf4j`, `System.out.println` 없음, `log.info`에 `inquiryId`/`productId`/`memberId` 등
    식별자 포함) 모두 확인. `InquiryRepository.findByProductIdOrderByCreatedAtDesc`는 fetch
    join이 없어 목록 조회 시 N+1 가능성이 있으나, `code-convention.md`의 "N+1 방지" 표에 열거된
    현재 대상 목록에 리뷰 목록 조회도 포함돼 있지 않고 `ReviewRepository`가 이미 동일 패턴
    (`findByProductIdOrderByCreatedAtDesc`, fetch join 없음)을 쓰고 있어 기존 팀 관례를 그대로
    따른 것으로 판단 — 신규 위반이 아니라 기존에 이미 감수한 리스크의 연장.
  - **docs/policy/ 확인**: `README.md`/`caching.md`/`refund-trigger.md`/
    `team-success-criteria.md` 4개 전부 확인. 셋 다 문의(inquiry) 도메인과 겹치지 않는 정책
    (캐싱 대상, 환불 트리거 시점, 공구팀 성사 기준)이라 이번 기능에 적용되는 정책이 없음을 확인.
  - **프론트엔드**: `product.html`에 `inquiries-section`(목록 + 작성/수정 폼)이 review 섹션과
    병렬 구조로 추가됨, `product.js`가 `GET /api/products/{id}/inquiries`로 목록을 불러오고
    `currentSellerId`(product.sellerId, 기존에 이미 노출되던 필드)와 `currentMemberId`
    (`gong9ri:auth-resolved`, review와 동일 패턴)를 비교해 판매자에게만 답변 등록/수정/삭제 UI를
    보여주는 로직을 코드로 직접 확인함(수동 브라우저 확인은 하지 않음, 코드 리딩 기준).
  - **스코프 준수**: 계획에서 제외한 비밀글/알림연동/마이페이지 목록/페이지네이션이 코드에 추가되지
    않았음을 grep으로 확인(`related_inquiry`, `seller/mypage/inquiries`, `page`/`size` 파라미터
    등 없음).

- 증거: `Attempt 1`의 API 샘플에 더해, 재실행으로 재확인한 계산적 평가 수치를 위에 기록함(중복
  방지를 위해 API 샘플 자체는 재수집하지 않음 — Attempt 1의 샘플이 이번에도 그대로 유효함을
  테스트 재실행으로 확인).

- 판정: **PASS**. `docs/dev/inquiry/crud/design.md` 신규 작성 + 이 문서를
  `docs/dev/inquiry/crud/changes/001-crud.md`로 채번 이동함.
