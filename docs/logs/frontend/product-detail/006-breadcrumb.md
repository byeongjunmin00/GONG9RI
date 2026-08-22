# 006-breadcrumb — 상품 상세 카테고리 브레드크럼 (로그)

별도 `docs/dev/ongoing/` 계획 문서 없이 사용자 요청으로 바로 진행한 소규모 UI 추가(정식 Plan
단계 생략, 승인은 채팅으로 받음).

## Attempt 1 — 2026-08-22  ✅ PASS

- 시도: 제품 사진 위쪽에 "홈 > 카테고리 > 상품명" 브레드크럼 추가.
  - `ProductCategory`가 하위 분류 없는 평평한 1단계 enum이라 딱 3단계까지만 나온다(사용자에게
    확인 후 이 범위로 축소 — 다단계 하위 카테고리는 별도 계획 필요라고 안내함).
  - `product.js`: `CATEGORY_LABELS`(main.js 카테고리 필터 바와 동일 한글 표기 재사용),
    `renderBreadcrumb(category, productName)` 추가. 카테고리 링크는 `/?category=` 로 연결
    (main.js가 이 쿼리를 그대로 읽어서 반영하는 걸 확인하고 재사용).
  - `product.html`: `#product-breadcrumb` 추가, 처음엔 `.product-detail-media` 안(사진 바로 위)에
    넣음.
  - 커밋: `1758c60 feat(frontend/product-detail): 상품 상세에 카테고리 브레드크럼 추가`.
- 결과·증거: 브라우저에서 `홈 > 식품 > Test Product for Todo Review` 정상 렌더 확인
  (`get_page_text`).

## Attempt 2 — 2026-08-22  ⚠️ 사용자 확인 대기 중 (미커밋)

- 시도: Attempt 1의 배치(사진 칸 안)가 사진만 브레드크럼 높이만큼 아래로 밀고 서머리 카드는
  그대로라 두 칸 윗선이 어긋나는 문제를 사용자가 스크린샷으로 리포트. `#product-breadcrumb`를
  `.product-detail-media` 안에서 `.product-detail-grid` 밖(그리드 앞 형제, 전폭)으로 옮겨 두
  칸이 브레드크럼 아래에서 같이 시작하게 함. `margin-bottom`을 브레드크럼 자체에 직접 추가
  (더 이상 `.product-detail-media`의 flex gap을 못 받으므로).
- 결과: 사용자가 스크린샷으로 재확인, 사진 윗선과 카드 윗선이 브레드크럼 바로 아래에서 나란히
  시작하는 것으로 보임(카드 안쪽 32px 패딩 때문에 텍스트 자체는 사진보다 살짝 아래지만, 이건
  브레드크럼 추가 전부터 있던 정상적인 카드/사진 구조 차이).
- **미완료**: 이 상태로 확정해도 되는지, 아니면 재빌드(`docker compose up -d --build app`)까지
  마저 진행할지 사용자 답변 대기 중. 확정되면 별도 커밋(`fix(frontend/product-detail): 브레드크럼
  위치를 그리드 밖으로 이동`)으로 남길 예정.

## Attempt 3 — 2026-08-22  ✅ PASS

- 시도: Attempt 2 상태를 사용자가 다시 리포트 — 전폭 배치가 헤더와 사이 간격이 너무 벌어져
  보임. 사용자가 그림판으로 직접 잘라 붙인 목업(뒤로가기 링크와 같은 줄, 카드 오른쪽 끝)을
  그대로 반영: `#product-breadcrumb`를 그리드 밖에서 다시 `.product-detail-summary` 카드
  안으로, `.product-back-link`와 같은 줄(`.product-detail-summary-top`, flex
  space-between)에 배치. 사진/서머리 칸은 그리드 최상단(원래 자리)으로 복귀.
  커밋: `2f158fa`.
- 결과·증거: `docker compose up -d --build app` 재기동 후 브라우저 확인.
  - `mediaTop === summaryTop === 121px` — 사진 칸과 서머리 카드가 다시 같은 줄에서 시작(헤더와의
    간격도 원래 수준으로 복귀).
  - buyer1: "목록으로" / "홈 > 식품 > Test Product for Todo Review"가 카드 맨 위 한 줄에 좌우로
    나란히 노출, 그 아래 기존 구매 UI 전부 정상.
  - admin1(비구매): 같은 줄 노출 유지, 그 아래는 안내 문구(`#purchase-role-notice`)로 정상 대체.
