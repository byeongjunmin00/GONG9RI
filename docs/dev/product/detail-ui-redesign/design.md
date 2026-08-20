# 상품 상세 페이지(product.html) 모던 2열 Split-Hero 리디자인 — Design

## 개요
상품 상세 페이지(`/product.html`)를 와디즈·무신사·아이디어스 스타일의 **Option A (모던 2열 Split-Hero 그리드 & 스티키 구매 카드)** 레이아웃으로 업그레이드한다.

## 관련 파일
- `src/main/resources/static/product.html`: 2열 그리드 마크업 구조
- `src/main/resources/static/css/components.css`: `.product-detail-grid`, `.product-detail-summary`, `.product-price-box` 등 CSS 규칙
- `src/main/resources/static/js/product.js`: 기존 DOM ID 100% 보존하여 백엔드 및 JS 기능 전체 정상 연동

## 레이아웃 구조 (2026-08-20 수정)

```
#product-detail  (flex column, gap)
├── .product-detail-grid   ← 2열 그리드는 여기까지만
│   ├── .product-detail-media    (sticky)
│   └── .product-detail-summary
├── .team-list-section     ← 그리드 밖 형제
└── .product-tabs-section  ← 그리드 밖 형제
```

처음엔 전폭 섹션(공구팀 목록·탭)까지 **같은 그리드 안에 넣고 `grid-column: 1 / -1`로 다시 전폭으로 되돌리는** 구조였다. 그 결과 좌측 썸네일의 `position: sticky`가 아래 행까지 따라 내려와 **공구팀 목록을 덮는 버그**가 있었다(2026-08-20 사용자 리포트).

전폭 섹션을 그리드 밖 형제로 빼서, **sticky의 컨테이닝 블록이 상단 2열 래퍼로 한정**되게 했다 — 구조적으로 아래 섹션을 넘어갈 수 없다. 섹션 간 간격은 부모(`.product-detail`)의 `flex column` + `gap`이 담당하므로 `.product-detail-wide` 클래스 자체가 필요 없어져 함께 제거했다("그리드에 넣고 다시 그리드를 무효화한다"는 구조 자체가 냄새였다).

`#product-detail`은 JS가 `hidden`으로 토글하는 대상이라 전폭 섹션도 **그 안에 남아 있어야** 한다 — 그래서 밖으로 완전히 빼지 않고 그리드 래퍼만 안쪽에 새로 두는 형태가 됐다.

## UI/UX 사양
1. **2열 Split Grid 레이아웃 (Desktop)**:
   - 좌측: 1:1 비율 대형 썸네일 이미지 (`product-detail-media`), 스티키 위치 고정 (`top: 100px`)
   - 우측: 상품 제목, 5성 별점, 가격 구간 카드, 팀 목표 선택 라디오, 환불 체크박스, 스티키 액션 버튼 (`product-detail-summary`)
2. **하단 전폭 영역 (`product-detail-wide`)**:
   - 모집 중인 공구팀 카드 목록 (`#team-list`)
   - 상품정보/리뷰/문의 탭 섹션 (`.product-tabs-section`)
3. **호환성 보장**:
   - 기존 모든 DOM ID 및 클래스명을 유지하여 팀 신설, 참가, 리뷰 작성, 카카오 공유 기능 호환 100% 보장.
