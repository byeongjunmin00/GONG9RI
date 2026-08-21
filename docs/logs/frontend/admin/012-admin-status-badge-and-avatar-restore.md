# 012-admin-status-badge-and-avatar-restore — 상품/회원 카드 상태 가시성 복구 (로그)

## Attempt 1 — 2026-08-21  ✅ PASS

- 시도: 011에서 사라진 상태 가시성 복구.
  - `admin-products.js`: Row 2에 상태 텍스트 배지(숨김/공개/오픈예정 + 추천푸시) 복구 — 썸네일이 실제 상품 사진으로 가려져도 항상 보이도록. Row 4 좌측에 상태 이모지 1개(`getStatusEmoji`) 추가.
  - `admin-members.js`: `createAvatarElement()` 복구, 회원 카드 Row 1에 아바타 아이콘 재배치.
- 결과: ✅ **PASS**
- 계산적 평가:
  - `./gradlew compileJava compileTestJava` → `BUILD SUCCESSFUL`.
  - `./gradlew test --tests AdminControllerTest` → 클린 로컬 MySQL(docker compose down -v 후 재기동)로 전체 통과, UPCOMING 필터 테스트 포함.
- 추론적 평가:
  - 실제로 `docker compose up -d --build`로 로컬에 반영한 뒤 브라우저에서 확인: 이미지 있는 상품 카드에서도 상태 배지 노출, 숨김 처리 시 배지가 즉시 `⚠️ 숨김`으로 바뀜, 카드 좌측하단 이모지가 상태와 일치, 회원 카드 5개(관리자/판매자/구매자) 전부 아바타 아이콘 노출.
- 증거:
  - `AdminControllerTest`: 27개 전체 통과.
  - 브라우저 실측: `row2Text: "판매자: 박판매 · LIVING⚠️ 숨김"`, `row4` 좌측에 `<span>⚠️</span>`, 회원 카드 5개 전부 아바타 SVG 확인.
