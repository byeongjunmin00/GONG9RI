# 상품 상세페이지 탭(상품정보/리뷰/문의) UI

대상: frontend/product-detail   <!-- 완료 시 docs/dev/frontend/product-detail/changes/로 이동 -->
담당: 전용운

## 배경 / 요구

사용자 요청: "상세페이지에 상품정보를 넣을 수 있는 공간이 있으면 좋겠다" → 이후 요구사항 구체화:
**상품정보 / 리뷰 / 문의** 3개로 나뉜 **탭(tab) UI**를 상세페이지에 두고, 탭을 클릭해서 각 콘텐츠를
전환하며 볼 수 있게 한다.

### 현재 상태 조사 결과

- `product` 엔티티/API에는 이미 `description`(상품 설명, `TEXT`) 필드가 있고 등록/수정 폼
  (`seller/products/new.html`)에서 `textarea`로 입력받아 상세 조회 응답(`GET /api/products/{id}`)에
  실려 내려온다. **새 데이터 필드는 필요 없다** — 이미 있는 이 값을 "상품정보" 탭 콘텐츠로 쓴다.
- 다만 현재 `product.html`에서는 이 값이 상품명 바로 아래 한 줄짜리 흐린(`text-muted`) 문단
  (`#product-description`, `js/product.js:228`에서 `textContent`로 대입)으로만 노출돼 있어, 독립된
  "공간"이라 부르기 어렵다.
- **리뷰**(`review`)와 **문의**(`inquiry`, 최근 커밋 `60ee085`로 추가)는 이미 완전히 구현되어 있고,
  `product.html`에 `reviews-section`, `inquiries-section`으로 각각 이미 노출되고 있다(목록 조회 +
  작성/수정/삭제 + 판매자 답변까지 백엔드·프론트 모두 완성 상태, 상세: `docs/api/review.md`,
  `docs/dev/inquiry/crud/design.md`). 둘 다 `team-list-section`(모집 중인 공구팀) 아래에 위/아래로
  순서대로 나열돼 있을 뿐 탭으로 묶여 있지 않다.
- 따라서 이번 작업은 **새 기능 추가가 아니라 기존 3개 콘텐츠(상품정보 신설 + 리뷰/문의 재배치)를
  탭 UI로 재구성하는 프론트엔드 전용 작업**이다. 엔티티/DTO/컨트롤러/서비스/DB 변경 없음.

## 설계

### 영향 범위 (계층)

- 정적 리소스(뷰)만 변경: `src/main/resources/static/product.html`,
  `src/main/resources/static/js/product.js`, `src/main/resources/static/css/components.css`.
- 백엔드(controller/service/repository/entity/dto/DB)는 **변경 없음** — `docs/api/product.md`,
  `docs/api/review.md`, `docs/api/inquiry.md`, `docs/db/product.md` 등 계약 문서도 변경 없음.

### 레이아웃 방향

- 상품 가격/액션 영역(`product-price-box`, `product-actions`)까지는 그대로 둔다.
- **`team-list-section`(모집 중인 공구팀)은 탭 밖에 그대로 둔다** — 요청받은 탭은 "상품정보/리뷰/
  문의" 3개뿐이고, 팀 목록은 구매 액션(참가하기)과 직결된 영역이라 "콘텐츠 열람" 성격의 탭과 성격이
  다르다고 판단. 탭 섹션은 `team-list-section` 다음, 기존 `reviews-section`/`inquiries-section`이
  있던 자리에 놓는다.
- 탭 3개 + 패널 3개 구성:
  - **상품정보 패널**: 상품명 옆의 `#product-description` 문단을 헤더 영역에서 이 패널로 옮긴다
    (헤더에는 상품명/판매자만 남긴다 — 같은 내용을 두 군데 보여주지 않는다). 설명이 비어 있으면
    빈 상태 문구를 보여준다(기존 상태 표시 패턴 재사용, 정확한 문구는 Generate 재량).
  - **리뷰 패널**: 기존 `reviews-section`(제목의 평균 평점 표시 `#review-average`, 목록, 작성/수정
    폼)을 통째로 옮긴다. 내부 로직·엔드포인트 변경 없음.
  - **문의 패널**: 기존 `inquiries-section`(제목의 개수 표시 `#inquiries-count`, 목록, 작성/수정
    폼, 판매자 답변 인라인 폼)을 통째로 옮긴다. 내부 로직·엔드포인트 변경 없음.
- 기존 DOM id(`review-average`, `reviews-status`, `reviews-list`, `review-form` 등,
  `inquiries-count`, `inquiries-status`, `inquiries-list`, `inquiry-form` 등)는 **그대로 유지**한다 —
  `product.js`의 기존 조회/제출 로직이 이 id들을 참조하므로 이름을 바꾸면 불필요한 회귀 위험이
  생긴다. 이번 작업은 "감싸는 위치"만 바꾼다.
- 기본으로 활성화되는 탭은 "상품정보"로 한다.
- 탭 전환은 **표시/숨김만** 바꾼다 — 리뷰/문의 데이터는 기존과 동일하게 페이지 로드 시점(및 기존
  트리거: `loadProduct()` 성공 후, `gong9ri:auth-resolved` 도착 시)에 미리 불러와 두고, 안 보이는
  탭이어도 데이터는 이미 채워진 상태로 둔다(재조회 시점·조건은 바꾸지 않는다 → 회귀 범위 최소화).
- 탭 버튼/패널의 정확한 마크업(예: `role="tablist"` 등 ARIA 처리 수준, 클래스명, 클릭 핸들러 배치)은
  Generate 단계에서 정한다. 이 문서는 "3개 탭으로 전환 가능해야 한다"는 요구와 콘텐츠 배치까지만
  고정한다.

### 문서 갱신 대상 (Evaluate 통과 시)

- `docs/dev/frontend/product-detail/design.md` — 탭 구조 반영(현재는 탭 UI 이전 상태만 서술돼 있음).
- `docs/dev/inquiry/crud/design.md`의 "프론트엔드" 절 — "`reviews-section` 바로 아래 `inquiries-
  section`을 병렬로 추가" 서술이 탭 구조로 바뀌므로 최신화 필요(SSOT는 `frontend/product-detail`
  쪽이 되고, 이 문서는 배치 설명만 갱신).

## 태스크

- [ ] `product.html`: 헤더에서 `#product-description` 제거, 탭 네비게이션(상품정보/리뷰/문의) +
      3개 탭 패널 컨테이너 추가, 기존 `reviews-section`/`inquiries-section`을 각 패널 안으로 이동
- [ ] `css/components.css`: 탭 네비게이션(활성/비활성 상태) + 탭 패널 표시/숨김 스타일 추가
- [ ] `js/product.js`: 탭 버튼 클릭 시 패널 전환(활성 탭 토글) 로직 추가. 기존
      `loadReviews`/`loadInquiries`/설명 렌더링 호출 지점·조건은 유지
- [ ] `docs/dev/frontend/product-detail/design.md` 갱신 (탭 구조 반영)
- [ ] `docs/dev/inquiry/crud/design.md`의 프론트엔드 배치 서술 갱신
- [ ] ongoing 문서를 `docs/dev/frontend/product-detail/changes/00X-product-detail-tabs.md`로 채번 이동

## 평가(통과) 기준

- `./gradlew test` 전체 통과 — 백엔드 변경이 없으므로 기존
  `ProductControllerTest`/`ReviewControllerTest`/`InquiryControllerTest` 등은 회귀 없이 그대로
  통과해야 한다.
- (수동 확인, 이 리포의 자동화 테스트 범위 밖) 브라우저에서 상세페이지 진입 시 "상품정보" 탭이
  기본 활성화되어 상품 설명이 보이고, "리뷰"/"문의" 탭 클릭 시 해당 패널만 보이며 다른 탭은
  숨겨진다. 탭을 여러 번 왕복 클릭해도 정상 전환된다.
- 리뷰 작성/수정/삭제, 문의 작성/수정/삭제 및 판매자 답변 등록/수정/삭제가 탭 재배치 이전과
  동일하게 동작한다(엔드포인트·요청/응답 불변이므로 순수 회귀 확인).
- `description`이 비어있는 상품도 "상품정보" 탭 진입 시 에러 없이 빈 상태로 보인다.

## 리스크 / 전제

- 순수 정적 리소스(HTML/CSS/JS) 변경이라 DB 마이그레이션·서버 설정 변경 없음. 백엔드 재빌드 없이
  브라우저 새로고침만으로 반영 확인 가능.
- 탭 클릭 UI 동작 자체는 `./gradlew test`로 자동 검증되지 않는다(JS 단위 테스트 인프라가 이
  저장소에 없음) — 수동 브라우저 확인이 필요하다는 점을 리스크로 남긴다(해결책은 Generate/사용자
  확인 몫).
- `docs/dev/review/` 개념 폴더 자체가 없다(리뷰 기능이 구현은 됐지만 별도 design.md가 없는 것으로
  보인다) — 이번 작업 범위 밖의 기존 문서 공백이라 이번 계획에서 새로 만들지는 않고, 발견 사실만
  기록해둔다.
