# 관리자 상품 상태별 이모지 썸네일 직관화 및 데드 코드 정리

대상: frontend/admin
담당: 전용운

## 배경 및 개편 목적

- **피드백 반영**: 초고밀도 상품 카드 썸네일/아이콘 뷰(`createCompactProductThumb`)에 상품 상태별 특화 이모지를 다르게 적용하여 시각적 직관성을 극대화한다:
  - ⏱️ **오픈 예정**: `⏱️` (시계)
  - ⚠️ **숨김/제재**: `⚠️` (경고)
  - 🚀 **추천/인기 푸시**: `🚀` (로켓)
  - 📦 **일반 공개**: `📦` (상자)
- **배지 및 카드 슬림화**: 썸네일 아이콘만으로 상태가 시각적으로 직관 구분되므로 중복 텍스트 배지(`statusBadgeGroup`)를 정리하여 초고밀도 카드를 한층 더 시원하고 깔끔하게 다듬는다.
- **데드 코드 정리**: `admin-members.js`의 `createAvatarElement()` 및 `admin-products.js`의 `createThumbnailElement()` 제거.

## 변경된 파일 목록

- `src/main/resources/static/js/admin-members.js`: 미사용 `createAvatarElement()` 제거
- `src/main/resources/static/js/admin-products.js`: `getStatusEmoji(product)` 상태별 이모지 썸네일 구현, 미사용 `createThumbnailElement()` 및 중복 텍스트 배지 정리
- `docs/dev/admin/design.md`: 디자인 SSOT 갱신
- `docs/logs/frontend/admin/011-admin-status-emoji-thumb-redesign.md`: 실행 로그 (Attempt 1)

## 평가 결과

- `./gradlew compileJava` 빌드 검증 성공.
- 상품 상태별 이모지 썸네일(⏱️, ⚠️, 🚀, 📦)이 한눈에 직관적으로 들어오며, 텍스트 배지 제거로 초고밀도 카드가 한층 더 깔끔하게 슬림화됨을 확인.
