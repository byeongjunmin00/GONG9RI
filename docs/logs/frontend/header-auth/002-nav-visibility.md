# 002-nav-visibility — 헤더 nav 링크 숨김 처리 (로그)

## Attempt 1 — 2026-08-10

- 시도:
  - 계획 문서(`docs/dev/ongoing/header-nav-visibility.md`) 태스크를 그대로 구현.
  - `src/main/resources/static/partials/header.html`
    - `nav` 안의 `data-role="SELLER"` 링크 2개(판매 물품 등록, 판매자 마이페이지)와 `data-role="BUYER"` 링크 1개(구매자 마이페이지)에 `hidden` 속성을 기본값으로 추가. "메인" 링크(`data-role` 없음)는 그대로 둠.
    - 상단 주석 중 "역할 무관 항상 노출, 강조만" 서술을, "기본값 `hidden`이며 header-auth.js가 역할 일치 링크만 `hidden = false`로 노출한다"는 서술로 정정. 각 페이지 자체의 서버 401/403 사후 판정 원칙은 이 헤더 표시와 무관하게 유지된다는 문장도 남김.
    - 로고, 로그인/회원가입 영역(`#header-auth-guest`), `#header-auth-user` 영역은 손대지 않음.
  - `src/main/resources/static/js/header-auth.js`
    - `applyLoggedInState(member)` 안, 역할이 일치하는 `data-role` 링크에 기존 `nav-link--role-active` 클래스 추가에 더해 `link.hidden = false;`를 추가해 명시적으로 노출. 강조 클래스는 계획 문서 지침대로 그대로 유지(제거하지 않음).
    - 역할이 일치하지 않는 링크는 아무 처리도 하지 않아 마크업 기본값(`hidden`)이 그대로 유지되도록 함.
    - 비로그인 흐름(`.catch` 블록)은 원래부터 아무 것도 하지 않는 구조라 수정하지 않음 — 기본 `hidden` 상태가 그대로 유지됨.
    - `bindLogout()`/`init()`/이벤트 구독 로직(`gong9ri:includes-ready`)은 전혀 건드리지 않음.
    - 파일 상단 docstring 중 "링크 자체는 숨기지 않는다" 서술도 새 동작(기본 hidden, 역할 일치 시에만 노출)에 맞게 정정(코드 자체를 변경하는 것은 아니고, 바로 아래에서 수정한 `applyLoggedInState` 동작 설명이 코드와 어긋나지 않도록 문서 주석만 갱신).
  - `css/components.css` 등 다른 파일은 변경하지 않음(계획 문서에서 이미 `<a>` 태그가 `display`를 선언하는 클래스가 없어 `[hidden]` specificity 보정이 불필요하다고 확인됨).
  - `./gradlew compileJava` → `BUILD SUCCESSFUL`(Java 변경 없음, 정적 리소스만 변경이라 회귀 확인 목적).
  - 브라우저 수동 확인(비로그인/BUYER/SELLER/로그아웃 시 nav 노출 상태)은 이번 Generate 단계에서 수행하지 않음 — Evaluate 단계 몫으로 남김.

- 결과: ✅ **PASS** (버그 없음)
- 원인: (해당 없음)
- 증거 (도커 MySQL/Redis + `bootRun`, 실제 브라우저 확인):
  - **비로그인**: nav 링크 `hidden` 상태 — 메인=false, 판매 물품 등록=true, 판매자 마이페이지=true, 구매자 마이페이지=true.
  - **BUYER 로그인**(`navbuyer1`): 메인=false, 판매 물품 등록=true, 판매자 마이페이지=true, **구매자 마이페이지=false**(정확히 일치하는 링크만 노출).
  - **로그아웃**: 로그아웃 버튼 클릭 → 페이지 새로고침 → 다시 전부 숨김(메인 제외) 상태로 복귀.
  - **SELLER 로그인**(`navseller1`): 메인=false, **판매 물품 등록=false, 판매자 마이페이지=false**, 구매자 마이페이지=true(숨김).
  - **콘솔**: 이번 변경으로 인한 새 에러 없음(남아있던 401/403 로그는 이전 세션들의 잔여 메시지).
  - `./gradlew compileJava` 재확인 → `BUILD SUCCESSFUL`.
  - 평가 종료 후 테스트 계정(`navbuyer1`, `navseller1`) 정리 완료.
