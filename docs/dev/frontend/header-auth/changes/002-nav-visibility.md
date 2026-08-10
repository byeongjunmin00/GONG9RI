# 헤더 nav 링크 숨김 처리 (header-auth 후속 수정)

대상: frontend/header-auth (기존 개념, 완료 시 changes/002로 채번 이동)
담당: 전용운

## 배경 / 요구

직전 작업(`docs/dev/frontend/header-auth/design.md`, `changes/001-header-auth.md`)에서 역할별 nav 링크(판매 물품 등록/판매자 마이페이지/구매자 마이페이지)를 "숨기지 않고 강조만" 하기로 결정했었다. 실제 배포 사이트에서 확인한 사용자가 이 결정을 뒤집어달라고 요청: 비로그인 상태에서도 "판매 물품 등록" 등이 그대로 보이는 게 어색하다 — **로그인하지 않았거나 역할이 다르면 해당 nav 링크 자체를 숨긴다.**

## 설계

- `partials/header.html`: `nav` 안의 `data-role="SELLER"`/`data-role="BUYER"` 링크 3개에 `hidden` 속성을 기본값으로 추가한다("메인" 링크는 `data-role`이 없어 항상 노출 유지).
  - `<a>` 태그는 `base.css`에서 `display`를 선언하지 않는다(`color`/`text-decoration`만) — 이 세션에서 반복된 `[hidden]` specificity 버그(클래스가 `display`를 선언해 `hidden`을 이기는 문제) 대상이 아니므로 별도 보정 규칙 없이 기본 `hidden` 동작으로 충분하다(코드 확인 완료).
- `js/header-auth.js`의 `applyLoggedInState()`: 로그인 성공 시 역할이 일치하는 링크만 `link.hidden = false`로 보이게 하고(기존 강조 클래스 `nav-link--role-active`는 유지해도 무방), 일치하지 않는 링크는 그대로 `hidden` 유지. 로그인 실패(비로그인)로 판정되면 아무것도 하지 않아 기본값(`hidden`)이 그대로 유지된다.
- **스코프 경계 변경 고지**: 이 변경으로 "역할과 무관하게 링크는 항상 노출, 서버 401/403으로 사후 판정"이라는 기존 프로젝트 전역 원칙(product-detail/checkout/seller-product-new 등 여러 design.md에 명시)이 **헤더 nav에 한해서만** 뒤집힌다. 각 페이지 자체의 401/403 서버 사후 판정 로직(비로그인으로 URL 직접 접근 시 등)은 그대로 유지되며 이번 변경과 무관하다 — 헤더에서 "보이는 진입점"만 좁아질 뿐, 직접 URL 접근에 대한 서버 측 보호는 원래도 이 헤더 표시와 무관하게 서버가 담당해왔다.
- 로그아웃 버튼 동작, `GET /api/auth/me` 연동 방식 등 나머지는 기존 그대로 변경 없음.

## 태스크

- [ ] `partials/header.html` — `data-role` 링크 3개에 `hidden` 기본값 추가, 상단 주석 갱신(강조만 → 숨김으로 정정)
- [ ] `js/header-auth.js` — `applyLoggedInState()`에서 역할 일치 링크만 `hidden = false`로 노출

## 평가(통과) 기준

- 비로그인 상태로 아무 페이지 접속 시 헤더 nav에 "메인"만 보이고 판매/구매 관련 3개 링크는 전부 숨겨진다.
- `BUYER` 로그인 시 "구매자 마이페이지"만 보이고 "판매 물품 등록"/"판매자 마이페이지"는 숨겨진다.
- `SELLER` 로그인 시 "판매 물품 등록"/"판매자 마이페이지"만 보이고 "구매자 마이페이지"는 숨겨진다.
- 로그아웃 시 다시 전부 숨겨진 상태로 돌아간다.
- 콘솔 에러 없음.

## 리스크 / 전제

- 로그인 상태 확인(`GET /api/auth/me`)이 끝나기 전 짧은 시간 동안 nav 링크가 전부 숨겨진 상태로 보인다(로그인 사용자도 마찬가지) — 네트워크가 느리면 깜빡임처럼 보일 수 있으나, 기존에도 `#header-auth-user` 영역이 같은 방식으로 동작해왔으므로 새로운 리스크는 아니다.
- 직접 URL 접근(예: 비로그인 상태로 `/seller/products/new.html` 주소를 직접 입력)은 이번 변경과 무관하게 계속 가능하다 — 헤더에 링크가 없을 뿐 페이지 자체의 접근 제어는 기존과 동일(서버 401/403 사후 판정).

## 문서 산출물

- 이 계획 문서: `docs/dev/ongoing/header-nav-visibility.md`
- Evaluate 통과 시 `docs/dev/frontend/header-auth/design.md` 갱신(강조 → 숨김으로 최신화) + 이 ongoing 문서를 `docs/dev/frontend/header-auth/changes/002-nav-visibility.md`로 채번 이동.
