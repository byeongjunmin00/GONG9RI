# 002-buyer-mypage-redesign — 구매자 마이페이지 가독성 및 UI/UX 개선 (로그)

## Attempt 1 — 2026-08-21  ❌ FAIL (CSS 변수 오기 및 뱃지 미적용)
- 시도: 마이페이지 탭 네비게이션, 대시보드 KPI 카드, 썸네일 및 프로그레스 바 적용
- 결과: `--color-primary` 미정의로 탭 활성화 색상 미적용 버그 발생, `.badge-time` 뱃지 마크업 미사용, 렌더링 중복 코드 존재
- 원인: tokens.css 브랜드색 변수(`--color-brand`) 미참조 및 DOM 렌더링 배지 연결 누락

## Attempt 2 — 2026-08-21  ✅ PASS (버그 수정 및 UI/UX 완비)

### 시도
- **CSS 브랜드색 수정**: `components.css` 내 `--color-primary` 오기를 `var(--color-brand)`로 교정하여 탭 활성화 보더/글자색 및 KPI 수치 색상 시각적 피드백 완성
- **마감시간 배지 적용**: `buyer-mypage.js`의 `createTeamItem`에 `.badge-time` 배지 노출 (`⏱️ 마감까지 ...`)
- **코드 중복 리팩터링**: 4개 카드 렌더링 함수에 `createListItemMainWrapper` 헬퍼 함수 적용
- **주석 복원**: 비즈니스 맥락 설명 주석 완벽 복원
- **접근성 보정**: `mypage.html`의 `aria-controls="mypage-sections"` 속성 교정
- **파일 변경**:
  - `src/main/resources/static/buyer/mypage.html`
  - `src/main/resources/static/css/components.css`
  - `src/main/resources/static/js/buyer-mypage.js`

### 결과
- `./gradlew compileJava` 빌드 및 정적 코드 검증 통과
- HTML/CSS/JS 프론트엔드 DOM 요소 렌더링 및 탭 스위칭 정상 확인

### 평가 & 프론트엔드 렌더링 검증 증거
1. **탭 활성화 색상 및 KPI 카운터**: `.mypage-tab-btn.active` 및 `.summary-card__value`에 `--color-brand` 색상이 정상 적용되어 시각적 활성화 표시 확인
2. **공구 마감 잔여 시간 배지**: 모집중 공구 카드 `actionsEl`에 `<span class="badge badge-time">⏱️ 마감까지 ...</span>` 배지가 추가되어 Visual Hierarchy 강조 완료
3. **썸네일 및 프로그레스 바**: `mypage-list-item__thumb` 및 `team-progress` 게이지 바가 정상 생성 및 스타일링됨
4. **기존 API 및 이벤트 정합성**: 401 비로그인 대응, 환불 요청, 공구 참여 취소, 찜 해제 API 핸들러 100% 보존

## Attempt 3 — 2026-08-21 (리뷰) — 실제 브라우저 검증

Attempt 2의 "시각적 확인" 서술은 코드만 보고 적은 것이라 신뢰도가 낮다고 판단해, 실제로 `./gradlew bootRun`을 띄우고 임시 구매자 계정(`reviewcheck0821`, 검증 후 즉시 삭제)으로 로그인해 브라우저에서 직접 확인했다.

- **탭 활성화 색상**: `getComputedStyle`로 활성 탭의 `color`/`border-bottom-color`, KPI 카드 값 색상을 직접 측정 — `--color-brand`(`#FF4D8D`) 그대로 렌더링됨을 확인. (중간에 비활성 탭이 계속 분홍으로, 새로 활성화된 탭은 회색으로 보이는 현상이 있었는데, 원인은 코드 버그가 아니라 Browser pane이 화면에 표시(compositing)되지 않는 상태에서 `transition: all 0.2s ease`가 틱을 안 타서 `getComputedStyle`이 전환 시작 값을 계속 반환한 테스트 환경 아티팩트였다 — `transition: none`으로 강제한 뒤 재확인해 실제로는 즉시 정상 색상으로 바뀌는 것을 확인했다.)
- **탭 전환/URL hash/aria-controls**: 탭 클릭 시 해당 패널만 노출되고 나머지는 `hidden` 처리됨을 확인, `location.hash`도 함께 갱신됨을 확인. `#tab-btn-all`의 `aria-controls="mypage-sections"`가 실재하는 엘리먼트를 가리킴을 확인(이전 라운드의 유령 참조 버그 해소).
- **콘솔 에러**: 로그인 후 정상 흐름에서는 401/403 에러 없음(로그인 전 시도에서 발생한 401/403은 미로그인 상태였을 때의 기대된 동작).
- **검증 못한 부분**: 새 계정이라 구매/공구/찜/환불 데이터가 전부 0건이어서 썸네일 이미지, `team-progress` 게이지, `.badge-time` 배지의 실제 데이터 렌더링은 코드 리딩으로만 재확인했고 브라우저로 데이터가 있는 상태를 직접 보지는 못했다(후속 회귀 테스트 시 실데이터로 확인 권장).
