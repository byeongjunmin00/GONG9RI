# 관리자 상담 관리 — 방 카드 레이아웃 축소

대상: admin                     <!-- 완료 시 docs/dev/admin/changes/013-*.md로 채번 이동(현재 012까지 있음) -->
담당: 전용운

## 배경 / 요구

`docs/dev/todo-backlog.md` 4번 항목(사용자 요청):

> 관리자 상담 관리: 회원 카드와 상담 삭제 사이 구분선 제거 + 상담 삭제 버튼을 메타 정보에 맞춰 한 줄로 배치. 그에 따라 카드 상하 폭 축소

`/admin/support.html`의 왼쪽 상담방 목록(`support-admin-room-card`)은 현재 3단으로 쌓여 있다.

1. 아바타 + 회원명 (선택 버튼, `.support-admin-room`)
2. 상태·마지막 메시지 시각 메타 (같은 선택 버튼 안, `.support-admin-room__meta`)
3. 구분선(`border-top`) 아래 "상담 삭제" 버튼만 있는 별도 줄(`.support-admin-room-card__actions`)

카드 하나가 불필요하게 길어서, 메타 정보 줄과 삭제 버튼 줄을 하나로 합치고 그 사이 구분선을 없애 카드 높이를 줄인다.

## 설계 (접근 방향)

(2026-08-22 사용자 피드백 두 차례 반영: DOM을 물리적으로 재구성하는 게 목적이 아니라, 지금 구분선 + 전체 폭 줄로 된 "크고 못생긴" 삭제 버튼 영역을 없애고, **삭제 버튼을 작게 줄여 메타 텍스트 우측에 겹쳐 배치**하는 게 목적. 메타는 지금처럼 선택 버튼 안에 그대로 둔다 — 그러면 선택 동작도 손댈 필요 없이 그대로 유지된다.)

- **DOM 구조는 그대로 둔다.** 메타 span(`.support-admin-room__meta`)은 지금처럼 선택 버튼(`<button class="support-admin-room">`) 안에 남는다 — 이름/메타 어디를 눌러도 상담이 선택되는 지금 동작을 코드 변경 없이 그대로 유지한다.
- **삭제 버튼만 절대 위치로 겹쳐 배치**: `<button>` 안에 `<button>`을 중첩할 수 없다는 기존 제약(2026-08-21 결정, 컴포넌트 CSS 주석에 기록됨) 때문에 삭제 버튼은 여전히 선택 버튼의 형제 엘리먼트여야 한다. 지금처럼 별도 줄(구분선 + 전체 폭 flex row)로 만드는 대신, 카드(`.support-admin-room-card`)를 `position: relative`로 두고 삭제 버튼을 `position: absolute`로 카드 우측 하단(메타 텍스트가 있는 줄 높이)에 작게 배치한다. 별도 박스/구분선이 사라지고 카드는 시각적으로 "이름 줄 + 메타 줄" 2단짜리 하나의 카드로 보인다.
  - 메타 span에 오른쪽 여백(`padding-right`)을 줘서 삭제 버튼과 텍스트가 겹치지 않게 한다.
  - 삭제 버튼 자체의 폰트 크기·패딩을 줄여 "작게" 배치한다(기존 `btn-sm`보다 더 축소).
- **CSS 변경**: `.support-admin-room-card__actions`를 flex row 박스(구분선·전체 폭 패딩)에서 절대 위치 소형 버튼으로 전면 교체. `.support-admin-room-card`에 `position: relative` 추가. `.support-admin-room__meta`에 `padding-right` 추가. 별도 줄이 사라지므로 카드 세로 높이가 자연히 줄어든다.
- 영향 파일: `static/js/admin-support.js`(`renderRooms()` — 삭제 버튼에 클래스 지정 방식만 소폭 조정, 구조는 그대로), `static/css/components.css`(`.support-admin-room-card`, `.support-admin-room-card__actions`, `.support-admin-room__meta` 규칙).
- `support.html` 자체는 변경 없음(정적 마크업이 아니라 JS가 카드를 렌더링).

## 태스크

- [x] `static/css/components.css`: `.support-admin-room-card`에 `position: relative` 추가. `.support-admin-room-card__actions`를 구분선·전체 폭 박스에서 `position: absolute`(우측 하단, 소형 크기)로 교체. `.support-admin-room__meta`에 삭제 버튼과 안 겹치도록 `padding-right` 추가.
- [x] `static/js/admin-support.js`의 `renderRooms()`: DOM 구조(메타가 선택 버튼 안에 있는 것)는 그대로 두고, 삭제 버튼 클래스/마크업만 위 CSS에 맞게 조정(래핑용 `actionsEl` div 제거, `delBtn`에 위치 클래스 직접 부여).
- [x] `docs/dev/admin/design.md` 갱신(상담 관리 카드 레이아웃 축소 한 줄 추가).

## 평가(통과) 기준 — 결과

- `./gradlew compileJava` — **BUILD SUCCESSFUL** (정적 리소스만 바뀐 변경이라 예상대로 영향 없음).
- 브라우저 실측: 실제 로그인·DB 데이터 없이 검증하기 위해, 실제 `renderRooms()`와 동일한 DOM(아바타 포함, `avatar.js` 재사용)과 실제 `components.css`를 그대로 로드하는 정적 프리뷰 페이지를 임시로 만들어(테스트 후 삭제) PowerShell 정적 서버로 띄우고 Claude Browser로 확인:
  - 카드가 "아바타+이름" 줄, "메타(진행중/종료 · 시각)" 줄 2단으로만 보이고 구분선 없음 — 스크린샷으로 확인.
  - 이름 클릭·메타 텍스트 클릭 둘 다 select 콘솔 로그 발생(선택 동작 그대로 유지) — 확인.
  - 삭제 버튼 클릭은 delete 콘솔 로그만 발생하고 select는 트리거하지 않음(이벤트 버블링 충돌 없음) — 확인.
  - 메타 텍스트에 초 단위까지 포함된 긴 시각 문자열("진행중 · 2026. 8. 22. 오후 12:42:26", 실제 텍스트 폭 179px)로도 삭제 버튼(왼쪽 시작 235px)과 19px 간격을 두고 안 겹침을 `getBoundingClientRect`로 정밀 확인.
  - 메타·삭제 버튼의 세로 중심이 거의 일치(75.89px vs 75.48px)해 같은 줄처럼 보임.
- 카드 높이: 기존 3단 구조(구분선 박스 별도 줄) 대비 2단으로 줄어 세로 폭이 축소됨(측정 카드 높이 74.5px, 별도 액션 박스+구분선이 사라진 만큼 감소).
- 참고: 관리자 로그인 + 실제 상담방 데이터가 있는 풀 스택 환경(MySQL+Redis) 실측은 이번 세션에서 진행하지 않았다(로컬 DB에 상담 데이터가 없고, 이 변경은 순수 CSS/DOM 레이아웃이라 정적 프리뷰로 시각·클릭 동작 검증이 충분하다고 판단). 실제 배포/로컬 전체 스택에서 육안 확인은 후속으로 가능.
