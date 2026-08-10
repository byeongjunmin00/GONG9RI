# 001-buyer-mypage — 구매자 마이페이지 (로그)

## Attempt 1 — 2026-08-10

- 시도: 승인된 계획(`docs/dev/ongoing/buyer-mypage.md`)대로 구매자 마이페이지 정적 페이지를 구현.
  - 신규 `src/main/resources/static/buyer/mypage.html`: `seller/mypage.html`을 템플릿으로 삼아 구조를 맞춤(헤더/푸터 include, 서브디렉토리라 CSS/JS 참조는 절대경로, `#page-alert`(401 공통 배너), `#mypage-sections` 안에 "구매 목록"/"공구 참여 목록" 두 섹션, 섹션별 독립 상태 영역(`#purchases-status`/`#teams-status`)).
  - 신규 `src/main/resources/static/js/buyer-mypage.js`:
    - `GET /api/buyer/mypage/purchases`를 먼저 로드해 `latestPurchases`를 채운 뒤 `GET /api/buyer/mypage/teams`를 로드하는 **순차 호출**로 구현(design.md의 "둘 다 로드된 이후 클라이언트에서 매칭" 결정을 그대로 반영). `seller-mypage.js`는 3개 API를 병렬로 쏘지만, 이 페이지는 SUCCESS 팀-결제 매칭에 `latestPurchases`가 필요해 의도적으로 순서를 바꿨다. 두 호출 모두 자체 `.catch`로 에러를 삼키므로 하나가 실패해도 다른 섹션은 독립적으로 계속 렌더링된다(요구사항 그대로).
    - 401/403 처리는 `seller-mypage.js`와 동일한 패턴(`handleUnauthorized` 공통 함수 → 401이면 페이지 공통 배너 + `#mypage-sections` 숨김, 403/기타는 해당 섹션 상태 영역에 서버 message만 표시).
    - 구매 목록: `status`가 `REFUNDED`면 `badge-failed`+"환불됨", 그 외(`PAID`)는 `badge-success`+"결제 완료"로 표시(기존 `.badge-*` 클래스 재사용, 신규 CSS 없음). 금액은 `toLocaleString('ko-KR')` 포맷, `paidAt`은 `checkout.js`의 기존 관례대로 서버가 준 문자열 그대로 표시(별도 날짜 파싱/포맷 없음).
    - 공구 참여 목록 — 상태별 분기(`teamStatusToBadgeClass`/`teamStatusToLabel`, `js/product.js`의 `statusToBadgeClass` 매핑을 참고하되 이 페이지 문구에 맞게 라벨만 다르게 함):
      - `RECRUITING`: `badge-recruiting` + "모집중". meta에 `currentCount/maxParticipants`(`X / Y명`)와 `formatRemaining(deadline)`(현재 시각 대비 남은 일/시/분을 계산해 "N일 N시간 남음" 등으로 문구화, 마감 지났으면 "마감 임박")을 함께 표시.
      - `SUCCESS`: `badge-success` + "성사 완료". `findMatchingPurchase(team)`으로 `latestPurchases`에서 `productId`가 같고 `status === 'PAID'`인 첫 결제를 best-effort로 찾아 매칭되면 금액+결제일시를 표시(구매 목록과 같은 `.mypage-list-item` 마크업이라 시각적 톤이 동일), 매칭 실패 시 에러 없이 팀 인원 정보(`X / Y명`)만 표시.
      - `FAILED`: `badge-failed` + "미성사(환불 처리됨)". meta는 인원 정보만.
    - 서버 문자열은 전부 `textContent`로만 대입(`innerHTML` 미사용).
  - `partials/header.html`: 네비게이션에 "구매자 마이페이지"(`/buyer/mypage.html`) 링크를 "판매자 마이페이지" 링크 바로 다음에 추가(로그인 여부/역할 무관 항상 노출, 기존 설명 주석도 구매자 마이페이지 언급하도록 갱신). `seller/mypage.html`, `js/seller-mypage.js` 등 기존 seller-mypage 산출물은 건드리지 않음.
  - `css/components.css`는 **수정하지 않음** — 구매 목록/팀 목록 항목 모두 기존 `.mypage-list`/`.mypage-list-item`/`.mypage-list-item__*`/`.badge-*`/`.product-status`/`.form-alert` 클래스만으로 표현 가능해 신규 스타일이 필요하지 않았다(계획 문서의 "필요한 신규 스타일 추가" 태스크는 "필요 시"로 해석, 이번엔 불필요). `[hidden]` specificity 문제도 새로 만든 요소들이 `display: flex/grid`를 선언한 클래스와 겹치지 않아 해당 없음(기존 `.product-status`/`#mypage-sections`와 동일 패턴).
- 검증: `./gradlew compileJava` 성공(BUILD SUCCESSFUL). 로컬 docker-compose MySQL/Redis 컨테이너(`gong9ri-main-mysql-1`/`gong9ri-main-redis-1`)가 떠 있어 `./gradlew test`도 실행 — 전체 통과(BUILD SUCCESSFUL, 기존 테스트에 변경 없음이므로 회귀 없음 확인).
- 다음(참고): Evaluate 단계에서 `./gradlew bootRun` 후 브라우저로 로그인/비로그인/판매자 계정 시나리오를 계획 문서의 "평가(통과) 기준" 항목대로 직접 확인 필요(이 Attempt에서는 정적 컴파일/테스트만 확인했고 브라우저 수동 확인은 수행하지 않음).

- 결과: ✅ PASS
  - 계산적 평가: `docker ps` 확인 결과 `gong9ri-main-mysql-1`/`gong9ri-main-redis-1` 모두 `Up (healthy)` 상태로 이미 가동 중이었다(별도 기동 불필요). `./gradlew compileJava` → `BUILD SUCCESSFUL`. `./gradlew test --rerun`(캐시 무시 강제 재실행) → `BUILD SUCCESSFUL`, 실패 0건(기존 테스트 회귀 없음).
  - 추론적 평가(계획 대조, 실제 파일 확인):
    - `git status --porcelain`으로 변경분 확인: `M partials/header.html`(수정), `?? buyer/mypage.html`, `?? js/buyer-mypage.js`, `?? docs/dev/ongoing/buyer-mypage.md`, `?? docs/logs/frontend/buyer-mypage/`, `?? .claude/launch.json`(무관). **`seller/` 하위 파일, `js/seller-mypage.js`, `SecurityConfig.java`, `css/components.css`, `js/api.js`, `js/include.js`, `css/tokens.css`/`base.css`/`layout.css`는 전부 `git status`에 잡히지 않음 = 미수정 확인.**
    - `buyer/mypage.html`/`js/buyer-mypage.js`를 직접 읽어 확인: `GET /api/buyer/mypage/purchases`·`GET /api/buyer/mypage/teams`를 순차 호출(purchases 먼저 → teams), `docs/api/mypage.md`의 실제 필드명(`paymentId`/`productId`/`productName`/`amount`/`status`/`paidAt`, `teamId`/`currentCount`/`maxParticipants`/`status`/`deadline`/`joinedAt`)과 코드에서 쓰는 필드명이 정확히 일치. `REFUNDED`는 `badge-failed`+"환불됨"으로 `PAID`와 구분 표시. `RECRUITING`은 `formatRemaining(deadline)`으로 잔여기간 + `currentCount/maxParticipants` 인원 표시, `SUCCESS`는 `findMatchingPurchase`로 `productId`+`status==='PAID'` 기준 best-effort 매칭 후 구매 목록과 동일한 `.mypage-list-item` 마크업으로 표시(매칭 실패해도 에러 처리 안 하고 인원 정보로 대체), `FAILED`는 "미성사(환불 처리됨)"로 표시 — 계획한 3분기 모두 구현 확인. 401은 `handleUnauthorized`가 공통 배너(`#page-alert`) + `#mypage-sections` 숨김으로 처리, 403/기타는 `purchases-status`/`teams-status` 각 섹션에 독립적으로 표시(한 섹션 실패가 다른 섹션에 전파 안 됨) — `.catch`가 섹션별로 분리돼 있어 확인됨.
    - `git diff -- seller/`, `js/seller-mypage.js` 등: git status에 애초에 안 잡혀 diff 없음 → seller-mypage 관련 산출물 전혀 미수정 확인.
    - `SecurityConfig.java`: git status에 안 잡힘 → 미수정 확인.
    - `js/api.js`, `js/include.js`, `css/tokens.css`, `css/base.css`, `css/layout.css`: 전부 git status에 안 잡힘 → 미수정 확인. `js/api.js` 내용도 재확인해 `err.status`/`err.code`/`err.message` 형태가 `buyer-mypage.js`의 `handleUnauthorized`/에러 처리와 실제로 맞는 계약임을 확인.
    - `css/components.css`: git status에 안 잡힘 → 미수정 확인(주장 사실). 그리고 `buyer/mypage.html`/`buyer-mypage.js`가 실제로 참조하는 클래스(`.mypage-list`, `.mypage-list-item`, `.mypage-list-item__info/__title/__meta`, `.mypage-section`, `.badge`, `.badge-recruiting`/`.badge-success`/`.badge-failed`, `.product-status`, `.product-status--error`, `.form-alert`, `.form-alert--error`)가 전부 `components.css`(또는 `base.css`의 `.text-gradient`, `layout.css`의 `.section__head`)에 이미 정의돼 있음을 grep으로 직접 확인 — "기존 클래스 재사용" 주장이 사실임을 검증. 단, `product-status--loading`/`product-status--empty`는 전용 CSS 규칙이 없어 베이스 `.product-status` 스타일만 적용되는데, 이는 `checkout.js`/`main.js`/`product.js`/`seller-mypage.js`/`seller-product-edit.js`에서도 동일하게 쓰는 기존 관례라 이번 작업만의 결함이 아님(스코프 이탈 아님).
    - 서버 응답 문자열 대입 방식: `buyer-mypage.js` 전체에서 `innerHTML` 사용 없음, 상품명/에러 메시지/금액/일시 등 서버 유래 문자열은 전부 `textContent`로만 대입(`titleEl.textContent`, `metaEl.textContent`, `badgeEl.textContent`, `pageAlertTextEl.textContent`, `showStatus`의 `el.textContent`) — grep/직접 코드 리딩으로 확인.
    - `partials/header.html` diff 확인: 기존 "메인"/"판매 물품 등록"/"판매자 마이페이지"/로그인/회원가입 링크는 그대로 유지, "구매자 마이페이지"(`/buyer/mypage.html`) 링크가 "판매자 마이페이지" 다음에 정확히 1줄 추가됨. 상단 설명 주석도 구매자 마이페이지를 언급하도록 갱신됨. 스코프 이탈 없음.
  - 원인(참고, PASS이므로 해당 없음): 없음.
  - 증거:
    - `./gradlew compileJava` → `BUILD SUCCESSFUL in 1s`.
    - `./gradlew test --rerun` → `BUILD SUCCESSFUL in 20s` (5 actionable tasks: 1 executed, 4 up-to-date), 실패 0건.
    - `docker ps` → `gong9ri-main-redis-1  Up 50 minutes (healthy)`, `gong9ri-main-mysql-1  Up 50 minutes (healthy)`.
    - `docs/api/mypage.md` 필드명 vs `buyer-mypage.js` 사용 필드명 1:1 일치 확인(코드 리딩, 위 서술 참고).
  - 판정: **PASS**. Java 도메인 로직 변경 없음(git status에 `src/main/java` 변경분 없음), `css/components.css`/`SecurityConfig.java`/seller-mypage 관련 파일 미수정 확인, 신규 페이지가 계획된 API·분기·XSS 방지 원칙을 그대로 구현했음을 확인. 브라우저 수동 확인(로그인/비로그인/판매자 계정 시나리오, `bootRun` 실기동)은 Evaluate 역할 밖이라 수행하지 않았으며 호출자가 직접 확인해야 한다.

## Attempt 2 — 2026-08-10 (평가 기준의 브라우저 수동 확인)

- 시도:
  - 도커 MySQL/Redis + `bootRun`으로 판매자 1명(`bmpseller1`)·구매자 2명(`bmpbuyer1`/`bmpbuyer2`) 계정 생성. 상품 2개 등록(모집중용 max=3, 성사용 max=2) → `bmpbuyer1`이 두 상품 모두 팀 신설+결제 → `bmpbuyer2`가 max=2 상품의 팀에 참가+결제해 2/2로 채워 `SUCCESS` 전환 확인. 미성사/환불 상태는 실시간 스케줄러를 기다리는 대신 DB에서 팀 status를 `FAILED`, 해당 결제를 `REFUNDED`로 직접 세팅해 프론트 표시만 검증(백엔드 스케줄러 자체는 이번 평가 대상 아님).
  - 헤더 링크 → 구매 목록/공구 참여 목록(모집중/성사/미성사) 렌더링 → 로그아웃 401 → 판매자 계정 403 순으로 확인. 확인 후 테스트 계정 3개, 상품 2개, 팀/참가/결제/수익요약 전부 정리.
- 결과: ✅ **PASS** (버그 없음)
- 원인: (해당 없음)
- 증거:
  - **헤더 링크**: 로그인 상태에서 "구매자 마이페이지" 링크 `href="/buyer/mypage.html"` 확인.
  - **구매 목록**: 결제 2건("성사상품" 10,000원, "모집중상품" 20,000원)이 각각 상품명/금액/결제일시와 함께 정확히 표시, "결제 완료" 라벨.
  - **공구 참여 목록 — RECRUITING**: "모집중상품" 팀이 "1/3명 · 마감까지 6일 23시간 남음"으로 표시(`deadline` 기준 잔여기간 계산이 실제로 동작).
  - **공구 참여 목록 — SUCCESS**: "성사상품" 팀이 `productId` 매칭으로 대응 결제(10,000원·결제일시)를 찾아 함께 표시하고 "성사 완료"로 구매 목록과 유사한 톤으로 노출 — 계획의 best-effort 매칭 설계가 실제로 맞아떨어짐.
  - **REFUNDED/FAILED 표시**(DB 직접 세팅 후 재확인): 구매 목록의 해당 결제가 "환불됨"으로, 공구 참여 목록의 해당 팀이 "미성사(환불 처리됨)"로 정확히 전환 표시(더 이상 잔여기간 텍스트는 노출되지 않음 — 종료 상태이므로 적절).
  - **401**: `POST /api/auth/logout` → `204` → `buyer/mypage.html` 재접속 시 "로그인이 필요합니다. 로그인하기"만 표시, 페이지 안 깨짐.
  - **403**: `bmpseller1`(SELLER) 로그인 후 접속 → 구매 목록/공구 참여 목록 두 섹션 각각 독립적으로 "접근 권한이 없습니다." 표시.
  - **모바일(375×812)**: `scrollWidth === clientWidth`(가로 스크롤 없음).
  - **콘솔**: 이번 buyer-mypage 플로우에서 새로 발생한 처리되지 않은 에러 없음(남아있던 401/403 로그는 이전 seller-mypage 테스트 세션의 잔여 메시지).
  - 평가 종료 후 테스트 계정 3개, 상품 2개, 가격구간, 팀/참가/결제, `seller_revenue_summary` 전부 정리 완료.
