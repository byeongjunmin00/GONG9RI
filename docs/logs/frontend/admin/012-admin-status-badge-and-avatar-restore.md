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

## Attempt 2 — 2026-08-21  ✅ PASS (팀 리뷰 지적 수정)

- 시도: 병합 시 돌리는 미정의 CSS 변수 검사에서 `.admin-card`(009에서 안티그래비티가 추가)의
  `transition: border-color var(--transition-fast), box-shadow var(--transition-fast);`가
  걸림 — `--transition-fast`는 `tokens.css`를 포함해 이 저장소 어디에도 정의된 적 없는 변수였다.
  CSS는 미정의 커스텀 프로퍼티가 있어도 에러 없이 그 선언만 조용히 무효화하므로, 화면은 정상
  렌더링되고 `.admin-card:hover`의 테두리/그림자 전환만 트랜지션 없이 즉시 바뀌는 상태였다 —
  깨진 걸 알아채기 어려운 종류의 버그.
  - 이 저장소는 트랜지션에 커스텀 프로퍼티를 쓰는 컨벤션이 없고(`components.css` 전체가
    `0.15s ease`/`0.2s ease` 하드코딩), 안티그래비티가 다른 토큰(`--space-3` 등) 패턴을 보고
    있지도 않은 변수명을 추정해서 넣은 것으로 보인다.
  - `border-color 0.15s ease, box-shadow 0.15s ease`로 다른 카드 hover 트랜지션과 동일한
    패턴으로 수정.
- 결과: ✅ **PASS**
- 증거:
  - `grep -rn "\-\-transition-fast" src/main/resources/static/css/` → 수정 후 매치 없음.

## Attempt 3 — 2026-08-21  ✅ PASS (사용자 지적 수정)

- 시도: 관리자 상품 현황 "공개 상품"(`status=VISIBLE`) 탭에 아직 안 열린 오픈예정 상품까지
  같이 뜬다는 지적. `ProductRepositoryImpl.findAllForAdmin`의 VISIBLE 조건이 `hidden.isFalse()`
  뿐이라 "숨기지 않음"만 봤지 "오픈예정 아님"은 안 봤던 게 원인 — 오픈예정 상품은 숨김 처리된
  게 아니라서 그대로 VISIBLE에도 잡혔다.
  - VISIBLE 조건에 `product.openAt.isNull().or(product.openAt.loe(now))`를 추가해
    오픈예정 상품을 제외하도록 수정. 이제 VISIBLE(공개)과 UPCOMING(오픈예정)이 상호배타적.
  - `products_withVisibleStatusFilter_excludesUpcomingProducts` 테스트 추가.
  - 테스트 도중 기존 HIDDEN 테스트가 "expected 1 but was 2"로 깨졌는데, 코드 버그가 아니라
    이전 세션에서 브라우저로 직접 숨김 처리했던 테스트 상품(productId 21)이 로컬 DB에 남아있어
    생긴 오염이었음 — `docker compose down -v` 후 완전히 새 DB로 재확인해 해결.
- 결과: ✅ **PASS**
- 계산적 평가:
  - `./gradlew test --tests AdminControllerTest` (완전히 새로 만든 MySQL 볼륨 기준) → 전체 통과.
- 증거:
  - `AdminControllerTest`: 29개 전체 통과(오염 없는 클린 DB 기준).
