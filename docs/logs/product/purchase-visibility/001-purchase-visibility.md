# 001-purchase-visibility — 구매 불가 상황에서 구매 UI 노출 문제 (로그)

## Attempt 1 — 2026-08-22  ✅ PASS

- 시도: `docs/dev/ongoing/product-detail-purchase-visibility.md` 계획대로 구현.
  - `product.html`: `.product-actions`에 `id="product-actions"`, 환불 동의
    `<label>`에 `id="refund-notice-field"` 추가(js가 통째로 hidden 토글할 대상).
  - `product.js`: `currentMemberRole` 상태 추가(`gong9ri:auth-resolved`에서 `currentMemberId`와
    같은 시점에 채움). `isNonBuyerMember()`(로그인했지만 role이 BUYER가 아님)와
    `applyPurchaseRoleVisibility()`(product-actions/refund-notice-field/
    target-participants-field를 role에 따라 숨김, tier가 없어 이미 숨겨진 경우는 다시 보이게
    하지 않음) 추가. `renderTargetParticipantsOptions()`와 auth-resolved 핸들러 양쪽에서 호출.
  - 팀 목록의 "참가하기" 버튼(`createTeamItem`)도 `isNonBuyerMember()`일 때 아예 그리지 않도록
    수정(7번과 같은 원인의 8번 발견 사항). auth-resolved 핸들러에 `loadTeams(productId)` 추가 —
    이 이벤트가 초기 `loadTeams()`보다 늦게 도착하면 이미 그려진 참가 버튼이 role 확정 전 상태로
    남기 때문.

## Attempt 1 — 결과·증거 (같은 세션에서 Evaluate까지 수행)

- `./gradlew compileJava` → BUILD SUCCESSFUL (프론트 전용 변경, Java 영향 없음 확인용).
- 브라우저 수동 확인(`./gradlew bootRun` PORT=8081, 기존에 떠 있던 공유 mysql/redis 컨테이너에
  연결, 테스트 계정 3개 생성 후 확인 뒤 전부 삭제 — DB에 영구 흔적 없음):
  - 테스트 상품(가격구간 2명/10,000원) 생성 후 각 역할로 `product.html?id={id}` 접근.
  - **BUYER**: `#product-actions`/`#refund-notice-field`/`#target-participants-field` 전부
    `hidden=false` — 기존과 동일하게 노출 확인(get_page_text로 "🔥 신규 공구팀 신설...",
    "혼자 바로 구매하기", "계속 쇼핑하기", 환불 동의 문구 전부 렌더 확인).
  - **SELLER(자기 상품)**: 위 3개 전부 `hidden=true` — get_page_text에 구매 액션 문구가 전혀
    안 나오고 가격표 다음 바로 "모집 중인 공구팀"으로 이어짐(별도 CSS 없이 flex+gap로 자연스럽게
    줄어듦, 계획 문서의 예상대로).
  - **ADMIN**(DB에서 role만 ADMIN으로 승격시켜 재현): SELLER와 동일하게 전부 숨김 확인.
  - **비로그인**: 기존과 동일하게 전부 노출 확인(회귀 없음).
  - **팀 목록 "참가하기" 버튼**: BUYER로 공구팀 신설(teamId=301) 후 SELLER/ADMIN으로 같은 상품을
    보면 "참가하기"는 안 보이고 "참여자 보기"만 남음, 비로그인으로 보면 "참가하기"가 그대로
    보임(회귀 없음). BUYER 본인이 참여한 팀은 "참여 취소"로 정상 표시.
  - 콘솔 에러: 비로그인 상태의 `/api/auth/me` 401(기존부터 있던 정상 흐름) 외 새로운 에러 없음.

## Attempt 2 — 2026-08-22  ✅ PASS

별도 `docs/dev/ongoing/` 계획 문서 없이, 사용자가 실제 시딩한 데이터로 브라우저에서 직접
확인하다가 발견한 후속 버그 2건을 빠르게 고친 세션(정식 Plan 단계 생략, 사용자 승인은 채팅으로
받음).

- 시도:
  - 문제 1: `applyPurchaseRoleVisibility()`가 `#product-actions`를 통째로 숨기면 "계속 쇼핑하기"
    링크까지 같이 사라져 SELLER/ADMIN이 페이지를 나갈 방법이 마땅치 않았다.
  - 문제 2: 그리드(`align-items: stretch` 기본값)가 짧아진 `.product-detail-summary` 카드를
    사진 칸 높이까지 강제로 늘려 카드 안에 빈 흰 여백만 남았다.
  - 해결: `#product-actions` 밖에 항상 보이는 `.product-back-link`(목록으로) 추가, 그 자리에
    안내 문구 `#purchase-role-notice`("이 계정은 구매 권한이 없어...") 추가, `.product-detail-summary--compact`
    클래스(`align-self: start`)를 `applyPurchaseRoleVisibility()`에서 hide=true일 때만 토글.
  - 커밋: `502e23e fix(frontend/product-detail): 구매 권한 없는 계정에 뒤로가기/안내 문구 추가`.
- 결과·증거:
  - `./gradlew build` 전체 (509 tests) 통과, `docker compose up -d --build app`로 재기동.
  - 실제 시딩 계정(admin1/buyer1, `docs/dev/todo-backlog.md` 옆 세션에서 생성)으로 브라우저
    로그인 후 `product.html?id=1683` 확인.
  - **admin1(비구매)**: `getComputedStyle` 확인 결과 `product-detail-summary--compact` 클래스
    적용됨, `align-self: start`, 카드 높이 555.9px vs 사진 칸 727.7px(더 이상 강제로 안 늘어남),
    `#purchase-role-notice` 노출, `.product-back-link` 노출, `#product-actions` hidden.
  - **buyer1**: 위 3개 다 기존과 동일(회귀 없음) — `product-detail-summary--compact` 없음,
    `#product-actions`/환불체크박스/목표인원 토글 전부 노출.
