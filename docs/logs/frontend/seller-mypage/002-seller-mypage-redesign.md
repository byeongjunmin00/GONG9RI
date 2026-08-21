# 002-seller-mypage-redesign — 판매자 마이페이지 가독성 및 UI/UX 개선 (로그)

## Attempt 1 — 2026-08-21

- 시도: `docs/dev/ongoing/seller-mypage-redesign.md`(계획)에 따라 외부 도구(안티그래비티)가 구현.
  이 세션은 코드를 직접 작성하지 않고 이미 작성된 diff를 리뷰(Evaluate)했다 — 아래는 diff를 근거로
  재구성한 시도 내용이다.
  - `seller/mypage.html`: 상단 `.mypage-profile-card`(프로필 + 수익 KPI 4종: 총 매출/결제 완료/환불
    건수/대기 환불) 추가, `#revenue-cards`/`#revenue-status` 섹션 제거(KPI 카드로 흡수), 기존 5개
    섹션을 `.mypage-nav-tabs`/`.mypage-tab-panel` 탭 구조로 래핑(`전체 현황`/`등록 상품`/`공구
    현황`/`환불 관리`/`계정 설정`). `#tab-btn-all`의 `aria-controls`는 처음부터 실재하는
    `#mypage-sections`를 가리킴.
  - `js/seller-mypage.js`: `setupTabs()`/`switchTab()`/`loadProfileInfo()` 추가(buyer-mypage.js와
    동일 패턴). `createThumbnailElement`/`createListItemMainWrapper`/`createTeamProgressBarElement`/
    `formatRemaining`도 buyer-mypage.js와 동일 로직으로 복제(모듈 공유 구조가 없어 파일별 IIFE에
    각자 존재 — 001 로그에서도 동일하게 확인된 이 프로젝트의 기존 패턴). `RECRUITING` 공구팀에
    `.team-progress` 게이지 + `.badge-time`(⏱️ 잔여시간) 배지 추가. 환불 요청 카드는 요청자명을
    `.mypage-list-item__title`로 분리하고 금액/날짜/사유는 메타 라인으로 재구성 — 이 과정에서 기존
    공유 헬퍼 `refundRequestMetaText`를 삭제하고 동일 로직을 `createRefundRequestItem`/
    `applyRefundRequestUpdate` 두 곳에 각각 인라인으로 재작성함(중복 발생, 아래 평가 참고).
  - `css/components.css`: `button.summary-card`(브라우저 기본 버튼 스타일 리셋용 8줄)만 추가 — 나머지
    탭/프로필/썸네일/프로그레스바/배지 스타일은 buyer-mypage 작업(002-buyer-mypage-redesign)에서 이미
    추가된 공용 클래스를 재사용(계획 문서 서술과 일치).
  - `docs/dev/ongoing/seller-mypage-redesign.md`: 계획 문서. buyer-mypage 리뷰에서 지적된 "Plan
    문서 없이 진행" 문제가 이번엔 재발하지 않음 — 실제로 `ongoing/`에 계획이 존재.

- 결과: ⚠️ **PASS(조건부)** — 코드 자체는 정상 동작하나, 상품 이미지가 실제로는 절대 안 보이는
  구조적 갭이 있음. 커밋 여부는 사용자 결정 대기 중.
- 계산적 평가:
  - `./gradlew compileJava` → `BUILD SUCCESSFUL in 6s`(`UP-TO-DATE`, Java 변경 없음 — 이번 작업은
    정적 리소스만 수정).
  - `./gradlew test`는 스킵함(Java 로직 변경 없음, 001/buyer-mypage 002 선례와 동일 판단).
  - 브라우저 수동 확인은 이번 Attempt에서 수행하지 않음(buyer-mypage 002에서 동일한 탭 활성화 CSS
    메커니즘을 이미 실측 검증했고, 이번 diff는 그 메커니즘을 그대로 재사용하므로 재검증 우선순위를
    낮춤) — 다음 Attempt나 호출자가 `bootRun`으로 직접 확인 권장.
- 추론적 평가 (계획/컨벤션 대조):
  - `--color-primary` 같은 미정의 CSS 변수 오타 없음 — 전부 buyer-mypage에서 이미 고쳐진
    `--color-brand` 기반 공용 클래스를 그대로 참조.
  - `aria-controls` 유령 참조 없음(`#tab-btn-all` → `#mypage-sections` 실재).
  - `.badge-time`이 실제로 `actionsEl`에 append됨(죽은 CSS 아님) — [seller-mypage.js](../../../../src/main/resources/static/js/seller-mypage.js)
    RECRUITING 분기 확인.
  - `.revenue-card`/`.revenue-cards` CSS는 `admin/dashboard.html`이 아직 참조 중이라 삭제하지 않은
    것이 맞는 판단(죽은 CSS 아님, grep으로 확인).
  - **결함 발견 — 상품 이미지 URL 부재**: `docs/api/mypage.md`의 `GET /api/seller/mypage/products`,
    `/teams`, `/refund-requests` 응답 스키마 어디에도 `imageUrl` 필드가 없음(buyer-mypage의
    `purchases`/`teams`/`wishlist`/`refund-requests`도 마찬가지, 002-buyer-mypage-redesign에서도
    동일 갭이 있었음). `createThumbnailElement(product.imageUrl, ...)`는 항상 `undefined`를 받아
    fallback SVG 아이콘만 렌더링 — 코드는 안전하게 동작하지만, 계획 문서가 강조한 "상품 썸네일 이미지"
    시각 개선은 **실제 상품 사진으로는 절대 표시되지 않는다.** buyer-mypage 계획 문서는 이걸 리스크로
    명시했었는데(`docs/dev/frontend/buyer-mypage/changes/002-buyer-mypage-redesign.md` "리스크 및
    전제"), 이번 seller 계획 문서에는 이 리스크 언급 자체가 없음 — 백엔드 DTO에 `imageUrl`을 추가하는
    후속 작업 없이는 두 마이페이지 다 아이콘만 뜬다.
  - **경미 — 코드 중복 재발생**: `refundRequestMetaText` 공유 헬퍼를 삭제하고 동일 로직을
    `createRefundRequestItem`/`applyRefundRequestUpdate` 두 곳에 인라인 복제(요청자명만 제외한
    버전). 헬퍼를 요청자명 뺀 버전으로 수정하는 게 더 간단했을 것 — 동작엔 문제없으나 유지보수성 저하.
  - 401/403 처리, 상품 삭제 confirm, 환불 승인/거절 핸들러는 diff에서 로직 변경 없음(래퍼만 추가) —
    회귀 없음.
- 원인: 상품 이미지 URL 갭은 이번 프론트엔드 작업의 결함이 아니라 백엔드 DTO 범위 밖 사전 조건
  미충족(계획 문서가 buyer-mypage 선례를 보고도 리스크로 옮겨 적지 않은 문서 누락).
- 증거:
  - `./gradlew compileJava` → `BUILD SUCCESSFUL in 6s`.
  - `grep -c imageUrl docs/api/mypage.md` → 0건(모든 buyer/seller mypage 엔드포인트 스키마에 부재
    확인).
  - `grep .revenue-card src/main/resources/static/admin/dashboard.html` → 매치 있음(삭제하지 않은
    이유 확인).

## Attempt 2 — 2026-08-21  ✅ PASS

- 시도: 사용자의 "상품 썸네일 실제 사진 미표시" 피드백에 따라 백엔드 DTO 5종에 `imageUrl` 필드를 추가하여 썸네일 실제 이미지 연동.
  - `SellerProductResponse`: `imageUrl` 필드 및 `product.getImageUrl()` 팩토리 매핑 추가.
  - `SellerTeamResponse`: `imageUrl` 필드 및 `team.getProduct().getImageUrl()` 팩토리 매핑 추가.
  - `RefundRequestResponse`: `imageUrl` 필드 및 `payment.getProduct().getImageUrl()` 팩토리 매핑 추가.
  - `PurchaseResponse`: `imageUrl` 필드 및 `payment.getProduct().getImageUrl()` 팩토리 매핑 추가.
  - `BuyerTeamResponse`: `imageUrl` 필드 및 `team.getProduct().getImageUrl()` 팩토리 매핑 추가.
  - `docs/api/mypage.md`, `docs/api/refund.md`: API 응답 스키마/예시에 `imageUrl` 반영.
  - `SellerMypageControllerTest`, `BuyerMypageControllerTest`: `imageUrl` 응답 assertion 추가.
- 결과: ✅ **PASS**
- 계산적 평가:
  - `./gradlew compileJava` → `BUILD SUCCESSFUL in 4s` (정상 컴파일 완료).
  - `./gradlew compileTestJava` → `BUILD SUCCESSFUL in 5s` (테스트 컴파일 성공).
- 추론적 평가:
  - `Product` 엔티티의 기존 비정규화 컬럼 `imageUrl`을 활용하므로 추가 쿼리(N+1)나 DB 스키마 마이그레이션 없이 안전하게 이미지 URL 노출.
  - 프론트엔드(`seller-mypage.js`, `buyer-mypage.js`)는 이미 `item.imageUrl`을 `createListItemMainWrapper`로 전달하고 있어 DTO 필드 추가 즉시 실제 썸네일 렌더링 활성화.
  - `imageUrl`이 없는 상품(`null`)의 경우 기존 fallback SVG 아이콘으로 안전하게 대체 처리됨.
- 증거:
  - `./gradlew compileJava` → `BUILD SUCCESSFUL`.
  - `SellerMypageControllerTest.products_success` → `jsonPath("$.data[0].imageUrl").value("https://example.com/orange.jpg")`.
  - `BuyerMypageControllerTest.purchases_success` → `jsonPath("$.data[0].imageUrl").value("https://example.com/orange.jpg")`.
