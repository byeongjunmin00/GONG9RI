# 구매자 마이페이지 가독성 및 UI/UX 개선 (002-buyer-mypage-redesign)

대상: frontend/buyer-mypage
담당: 전용운

## 배경 / 요구

현재 구매자 마이페이지(`buyer/mypage.html`)는 계정 정보, 구매 목록, 찜한 상품, 공구 참여 목록, 환불 요청 내역 5개 섹션이 한 페이지에 길게 수직 스크롤 형태로 나열되어 있어 가독성이 떨어지고 정보 탐색이 어렵다.
또한 시각적 위계(Visual Hierarchy)가 없고, 상품 이미지 썸네일이나 공구 진행률(Progress Bar)이 없이 텍스트 위주로 표기되어 핵심 정보를 빠르게 파악하기 어렵다.

## 설계 및 접근 방향

### 1. 상단 프로필 & Quick Summary 대시보드 추가
- 로그인 사용자 이름 및 이메일 요약 카드 (`#summary-user-name`, `#summary-user-email`)
- 핵심 현황 카운터 4종 (구매 내역, 참여 공구, 찜한 상품, 환불 내역) 한눈에 보기

### 2. 탭(Tab) 네비게이션 구조 도입
- `[전체 현황]` / `[구매 내역]` / `[공구 참여]` / `[찜한 상품]` / `[환불 내역]` / `[계정 설정]`
- 탭을 통해 사용자가 원하는 정보만 집중해서 볼 수 있도록 분리
- URL hash(`#purchases`, `#teams` 등) 바인딩 지원으로 직관적인 페이지 진입 지원

### 3. 마이페이지 카드 시각적 개선 (Visual Hierarchy)
- **상품 썸네일 이미지/아이콘 영역 배치**: 텍스트 전용 카드에서 상품 이미지(또는 기본 플레이스홀더)가 포함된 구조로 개선 (`.mypage-list-item__thumb`)
- **공구 진행률(Progress Bar) visual 표현**: 모집 중인 공구팀 카드에 인원 달성률(`currentCount / maxParticipants`)을 시각적 프로그레스 바로 표시 (`.team-progress`)
- **D-Day / 마감시간 배지 강조**: 모집 중 공구팀 마감 시간에 잔여 시간 배지 적용 (`.badge-time`)
- **상태 배지 강화**: 결제 완료, 모집중, 성사 완료, 환불됨 배지의 시각적 명확성 확보

### 4. 기존 비즈니스 로직 및 API 계약 100% 유지
- GET `/api/buyer/mypage/purchases`, `/teams`, `/wishlist`, `/refund-requests` 호출 구조 및 예외 처리(401/403) 유지
- 혼자구매 환불 요청, 공구 참여 취소, 찜 해제 동작 등 기존 이벤트 핸들러 완전 호환

## 영향 범위

- `src/main/resources/static/buyer/mypage.html`: 탭 네비게이션 및 프로필 요약 레이아웃 추가
- `src/main/resources/static/css/components.css`: 탭 바, 대시보드 KPI 카드, 프로그레스 바, 썸네일 카드, `.badge-time` 배지 스타일 추가
- `src/main/resources/static/js/buyer-mypage.js`: 탭 전환 제어, 요약 카운터 갱신, 썸네일/프로그레스바 DOM 렌더링 로직 확장

## 절차 관련 참고

이 작업은 `docs/dev/ongoing/`에 계획 문서를 먼저 만들고 사람 승인을 받은 뒤 구현하는 정규 절차(`AGENTS.md` Plan → 휴먼게이트 → Generate)를 거치지 않고, 외부 도구(안티그래비티)가 계획·구현·"평가 통과" 로그를 한 번에 작성해 `changes/`로 바로 채번했다. 이후 리뷰(코드 리뷰 2회전)로 발견된 버그(`--color-primary` 오기, `.badge-time` 미사용, 접근성 문제, 주석 삭제)는 수정했지만, 생략된 Plan 단계 자체는 소급 적용할 수 없다 — 정규 게이트는 "코드 작성 전 승인"이 핵심이라 사후에 문서만 채워서는 충족되지 않는다. 아래 태스크는 실제로 구현된 내용의 기록이며, "계획 수립 및 승인" 항목은 정규 절차대로 이뤄진 게 아님을 밝혀둔다.

## 완료된 태스크

- [ ] ~~구매자 마이페이지 가독성 개선 시안 계획 수립 및 승인~~ — 정규 Plan 절차 생략됨(위 참고)
- [x] `buyer/mypage.html` 탭 마크업 및 대시보드 요약 섹션 추가
- [x] `components.css` 브랜드 색상(`--color-brand`) 기반 마이페이지 탭, 프로필 카드, 리스트 카드, `.badge-time` 뱃지 스타일 작성
- [x] `buyer-mypage.js` 탭 전환 event listener, 썸네일, 공구 프로그레스 바 및 잔여 시간 배지 렌더링 추가
- [x] 회귀 확인 (401 비로그인 대응, 환불요청/참여취소/찜해제 API 동작 확인)
- [x] `./gradlew compileJava` 검증 및 static 프론트엔드 DOM 요소 렌더링 검증 완료

## 평가(통과) 기준

- `./gradlew compileJava` 성공
- 탭 메뉴 클릭 시 해당 섹션으로 원활하게 전환되며 활성 탭이 브랜드 색상(`--color-brand`)으로 시각적 강조되는가
- 공구 참여 목록 항목에 인원 달성률 프로그레스 바와 `.badge-time` 잔여시간 배지가 명확히 강조되었는가
- 구매 목록 / 찜 목록 등에 상품 썸네일 영역이 적용되어 시각적 구분이 용이한가
- 기존 401 로그인 배너 처리 및 환불/취소 기능이 정합성 있게 정상 동작하는가

## 리스크 및 전제

- 백엔드 DTO에 상품 이미지 URL(`thumbnailUrl` 등)이 제공되는지 확인 필요 (제공되지 않을 경우 기본 상품 아이콘/SVG 플레이스홀더 fallback 적용)
- existing id 및 API 바인딩을 훼손하지 않고 탭 레이아웃으로 감싸서 구현
