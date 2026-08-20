# 상품 상세 페이지(product.html) 모던 2열 Split-Hero 리디자인 — Design

## 개요
상품 상세 페이지(`/product.html`)를 와디즈·무신사·아이디어스 스타일의 **Option A (모던 2열 Split-Hero 그리드 & 스티키 구매 카드)** 레이아웃으로 업그레이드한다.

## 관련 파일
- `src/main/resources/static/product.html`: 2열 그리드 마크업 구조
- `src/main/resources/static/css/components.css`: `.product-detail-grid`, `.product-detail-summary`, `.product-price-box` 등 CSS 규칙
- `src/main/resources/static/js/product.js`: 기존 DOM ID 100% 보존하여 백엔드 및 JS 기능 전체 정상 연동

## UI/UX 사양
1. **2열 Split Grid 레이아웃 (Desktop)**:
   - 좌측: 1:1 비율 대형 썸네일 이미지 (`product-detail-media`), 스티키 위치 고정 (`top: 100px`)
   - 우측: 상품 제목, 5성 별점, 가격 구간 카드, 팀 목표 선택 라디오, 환불 체크박스, 스티키 액션 버튼 (`product-detail-summary`)
2. **하단 전폭 영역 (`product-detail-wide`)**:
   - 모집 중인 공구팀 카드 목록 (`#team-list`)
   - 상품정보/리뷰/문의 탭 섹션 (`.product-tabs-section`)
3. **호환성 보장**:
   - 기존 모든 DOM ID 및 클래스명을 유지하여 팀 신설, 참가, 리뷰 작성, 카카오 공유 기능 호환 100% 보장.
