# 구매자 마이페이지 (frontend/buyer-mypage) — Design

## 개요

구매자가 자신의 구매 완료 목록과 참여한 공구팀 목록(성사/미성사 포함)을 한 페이지(`/buyer/mypage.html`)에서 확인한다. 판매자 계정으로 접근하면 API가 `403`을 반환해 사후 판정된다. `seller/mypage.html` 등 seller-mypage 관련 산출물, `SecurityConfig.java`, `css/components.css`는 이번 작업에서 건드리지 않는다(기존 클래스만 재사용).

## 인터페이스 / 산출물

```
src/main/resources/static/
├── buyer/
│   └── mypage.html               # 마이페이지 본체(구매 목록/공구 참여 목록)
└── js/
    └── buyer-mypage.js           # 2개 API 호출·렌더링, 상태별 분기
```

- `css/components.css`는 수정하지 않음 — 기존 `.mypage-section`/`.mypage-list`/`.mypage-list-item`(+`__info`/`__title`/`__meta`)/`.badge`/`.badge-recruiting`/`.badge-success`/`.badge-failed`/`.product-status`(+`--error`)/`.form-alert`(+`--error`) 클래스만으로 표현 가능해 신규 스타일 불필요.
- `partials/header.html`: nav("판매자 마이페이지" 옆)에 "구매자 마이페이지"(`/buyer/mypage.html`) 링크 추가, 로그인 여부/역할과 무관하게 항상 노출.
- `SecurityConfig.java`는 변경 없음(이미 `/**/*.html` permitAll 매처가 서브디렉토리 html을 허용).

## 데이터 연동

- `buyer/mypage.html` 로드 시 `GET /api/buyer/mypage/purchases`를 먼저 호출해 `latestPurchases`를 채운 뒤 `GET /api/buyer/mypage/teams`를 호출하는 **순차 호출**(공구 참여 목록의 `SUCCESS` 항목이 구매 목록과 매칭하려면 `purchases`가 먼저 로드돼 있어야 함). 로그인 사전 확인 없음.
- 401(UNAUTHORIZED)이면 공통 배너(`#page-alert`)로 로그인 안내+링크를 띄우고 두 섹션(`#mypage-sections`) 전체를 숨긴다. 403(FORBIDDEN, 판매자 계정)/기타 에러는 해당 섹션의 상태 영역(`#purchases-status`/`#teams-status`)에만 표시하고 다른 섹션은 독립적으로 계속 렌더링된다.
- 구매 목록: `status`가 `REFUNDED`면 `badge-failed`+"환불됨", `PAID`면 `badge-success`+"결제 완료"로 표시. 금액은 `toLocaleString('ko-KR')` 포맷, `paidAt`은 서버 문자열 그대로 표시.
- 공구 참여 목록: `status`(`RECRUITING`/`SUCCESS`/`FAILED`)별로 분기.
  - `RECRUITING`: `badge-recruiting`+"모집중". `deadline`과 현재 시각 차이를 `formatRemaining()`으로 "N일 N시간 남음"/"N분 남음"/"마감 임박"으로 문구화, `currentCount`/`maxParticipants`로 인원(`X / Y명`) 표시.
  - `SUCCESS`: `badge-success`+"성사 완료". `findMatchingPurchase(team)`으로 `latestPurchases`에서 `productId`가 같고 `status === 'PAID'`인 첫 결제를 best-effort로 찾아 매칭되면 금액+결제일시를 구매 목록과 동일한 `.mypage-list-item` 마크업으로 표시, 매칭 실패 시 에러 없이 인원 정보만 표시(한 상품에 결제 이력이 여러 번 있으면 어느 결제가 해당 팀 것인지 API로 구분 불가 — 알려진 한계).
  - `FAILED`: `badge-failed`+"미성사(환불 처리됨)". 인원 정보만 표시(마감 지난 팀은 정책상 스케줄러가 이미 환불 처리를 끝냈다고 가정, `docs/policy/refund-trigger.md`).
- 서버 응답 문자열(상품명/에러 message/일시 등)은 전부 `textContent`로만 대입(XSS 방지, `innerHTML` 미사용).

## 규칙 / 검증

- 서브디렉토리 페이지(`buyer/mypage.html`)의 CSS/JS/partial 참조는 반드시 절대경로.
- `RECRUITING`의 남은 기간은 페이지 로드 시점 1회 계산 — 새로고침 없이는 실시간 갱신되지 않는다(후속 과제).
- `SUCCESS` 팀의 결제 상세 매칭은 `productId` 기준 best-effort이며 `paymentId` 매칭이 아니므로 완전한 정확도를 보장하지 않는다.

## 관련 코드 위치

- `src/main/resources/static/buyer/mypage.html`, `js/buyer-mypage.js` — 신규
- `src/main/resources/static/partials/header.html` — "구매자 마이페이지" 링크 추가
- `css/components.css` — 변경 없음(기존 클래스 재사용)
- 경위: `docs/dev/frontend/buyer-mypage/changes/001-buyer-mypage.md`, 실행 로그: `docs/logs/frontend/buyer-mypage/001-buyer-mypage.md`
