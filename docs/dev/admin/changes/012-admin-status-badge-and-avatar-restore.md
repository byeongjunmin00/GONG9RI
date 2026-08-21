# 관리자 상품/회원 카드 상태 가시성 복구 (텍스트 배지 + 좌측하단 이모지 + 회원 아바타)

대상: frontend/admin
담당: 전용운

## 배경 / 문제점

011(이모지 썸네일 재설계)에서 상품 카드의 텍스트 상태 배지(숨김/공개/추천푸시)를 없애고 썸네일 안에 상태별 이모지만 넣는 방식으로 바꿨는데, `createCompactProductThumb`가 `product.imageUrl`이 있으면 실제 사진을 보여주고 이모지는 사진이 없거나 로드 실패했을 때만 폴백으로 쓰이는 구조라, **사진이 있는 상품(실제 서비스의 대다수)은 상태를 전혀 구분할 수 없는 회귀**가 발생했다.

같은 흐름에서 회원 카드의 아바타(원형 사람 아이콘)도 009 개편 때 시각적으로 빠졌다가 011에서 관련 함수(`createAvatarElement`)까지 데드코드로 완전히 삭제됐다.

## 해결 및 구현 내용

1. **상품 카드 상태 텍스트 배지 복구 (`admin-products.js`)**:
   - Row 2(판매자/카테고리)를 flex로 바꿔 우측에 상태 배지 그룹 추가.
   - 숨김 → `⚠️ 숨김`, 오픈예정 → `⏱️ 오픈예정`, 그 외 → `공개` (상호배타), 추가로 추천 대상이면 `🚀푸시` 배지를 별도로 붙임.
   - 썸네일에 실제 상품 사진이 떠도 이 배지는 항상 노출되므로 상태 구분 가능.

2. **상품 카드 좌측하단 상태 이모지 1개 추가 (`admin-products.js`)**:
   - Row 4(액션 버튼 영역)를 `justify-content: space-between`으로 바꿔 좌측에 `getStatusEmoji(product)` 결과(⏱️/⚠️/🚀/📦) 하나만 표시, 우측에 기존 액션 버튼(상세/숨김/삭제)을 그룹으로 묶어 유지.

3. **회원 카드 아바타 복구 (`admin-members.js`)**:
   - `createAvatarElement()`를 다시 추가(원형 배경 + 사람 실루엣 SVG, 30px), Row 1 타이틀 그룹 왼쪽에 배치.

## 변경된 파일 목록

- `src/main/resources/static/js/admin-products.js`: 상태 텍스트 배지 복구, 좌측하단 상태 이모지 추가
- `src/main/resources/static/js/admin-members.js`: `createAvatarElement()` 복구 및 회원 카드 아바타 표시
- `docs/dev/admin/design.md`: 최종 SSOT 갱신

## 평가 결과

- Docker(`docker compose up -d --build`)로 로컬에 실제 반영해서 확인:
  - 이미지가 있는 상품 카드에서도 상태 배지(`⚠️ 숨김`)가 정상 노출됨을 실측 확인.
  - 카드 좌측하단에 상태와 일치하는 이모지 1개만 표시됨을 실측 확인(숨김 상품 → `⚠️`).
  - 회원 카드 전원(관리자/판매자/구매자)에 아바타 아이콘이 표시됨을 실측 확인.
- `./gradlew test --tests AdminControllerTest` → 전체 통과 (UPCOMING 필터 테스트 포함).
