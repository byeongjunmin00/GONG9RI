# 004-media-gap-and-kakao-share — 사진 칸 공백 제거 + 카카오 공유 버튼 위치·스타일 (로그)

## Attempt 1 — 2026-08-21 ✅ PASS
- 시도: (사용자 리포트) 상품 상세 페이지에서 왼쪽 사진 칸이 오른쪽 서머리 카드보다 훨씬 짧아
  아래에 큰 공백이 생기는 문제. 다음을 변경:
  - `components.css`의 `.product-detail-grid`를 `align-items: start` → `stretch`로 바꾸고
    컬럼 비율을 `1fr:1.1fr` → `1fr:1fr`로 조정해 왼쪽(미디어) 칸이 오른쪽(서머리) 칸과 같은
    행 높이를 갖게 함.
  - `.product-detail-media`를 flex-column으로 만들고, 사진(`.product-gallery`, 정사각형 고정)
    아래 `.product-gallery-thumbs`(썸네일 프리뷰)를 `flex: 1 1 auto`로 남는 세로 공간을
    채우도록 함. 처음엔 `grid-auto-rows: 100px`(고정값)로 했더니 flex-grow는 됐지만 grid 행
    자체는 안 늘어나고 컨테이너 안에 안 보이는 빈 공간만 생겨 시각적으로 원래와 동일한 문제가
    남았음 — `grid-auto-rows: minmax(100px, 1fr)`로 바꿔 행 자체가 늘어나게 해서 해결.
  - `product.js`의 `renderGallery()`에서 사진이 1장뿐이면 `.product-gallery-thumbs`를
    `hidden` 처리하던 것을 제거하고, 대신 점선 테두리 플레이스홀더 슬롯 3개
    (`.product-gallery-thumb--empty`)를 항상 렌더링하도록 변경 — "빈 앨범" 느낌을 주면서
    사진 개수와 무관하게 왼쪽 칸 높이가 안정적으로 유지되게 함(사용자 확인 후 결정).
  - 카카오톡 공유 버튼(`#kakao-share-btn`)을 사진 칸 아래(`.product-media-actions`, 무채색
    `btn-secondary` 풀폭 버튼)에서 오른쪽 서머리 상단 상품명 옆(`.product-title-row`)으로
    이동. 카카오 브랜드 컬러(`#FEE500`/`#191919`, `login.html`의 카카오 로그인 버튼과 동일
    톤)를 적용하고 말풍선 아이콘(인라인 SVG) + "카카오톡 공유" 한글 라벨을 붙인 작은 알약형
    버튼으로 재스타일링. JS 로직(`setUpKakaoShare`/`handleKakaoShare`)은 id 기반이라 마크업
    위치 변경만으로 그대로 동작.
- 결과: 로컬에 MySQL/Redis(Docker) 없이 실제 백엔드 기동은 생략하고, 같은 `css/`를 그대로
  불러오는 정적 목업 페이지(스크래치패드에서 PowerShell `HttpListener`로 임시 서빙, 작업 종료
  후 서버 종료 + 목업 파일 삭제)로 두 시나리오를 확인:
  - 사진 1장(플레이스홀더 3슬롯): `getBoundingClientRect()`로 측정한 결과 마지막 플레이스홀더
    하단과 서머리 카드 하단의 `visualGap = 0px` (수정 전엔 224px 공백 확인, 원인 진단 후 수정).
  - 사진 5장(실제 썸네일, 짧은 상품명 → 서머리가 원래 짧은 케이스): `visualGap = 0px`,
    썸네일이 자연스럽게 확대돼 표시됨.
  - 데스크톱(1100px)·모바일(400px, `max-width:991px` 브레이크포인트 아래 1열 스택) 두 뷰포트
    모두 스크린샷으로 확인 — 모바일에서도 제목+카카오 버튼 한 줄 배치, 썸네일 그리드 정상.
  - 별도 자동 테스트 스위트는 없다(정적 HTML/CSS/JS). 실제 백엔드 데이터·갤러리 넘기기 상호작용·
    카카오 SDK 초기화까지의 종단 검증은 이번 확인 범위 밖 — 다음에 로컬 스택(Docker MySQL/Redis)
    기동 가능할 때 실데이터로 재확인 권장.
- 증거(브라우저 스크린샷 + JS 측정):
  - 수정 전: `visualGap: 224.109375` (사진 1장 시나리오, 플레이스홀더 아래 공백 존재)
  - 수정 후: `visualGap: 0` (사진 1장·5장 시나리오 모두)
  - 카카오 버튼: 상품명 옆 노란색(`#FEE500`) 알약형 버튼, 말풍선 아이콘 + "카카오톡 공유" 라벨로
    렌더링 확인(스크린샷).

## Attempt 2 — 2026-08-21 ✅ PASS
- 시도: (사용자 리포트) Attempt 1로 상하 flush는 됐는데, ① 사진(왼쪽)과 서머리(오른쪽) 두 칸
  사이 가로 간격(`--space-8`, 64px)이 너무 넓다, ② 플레이스홀더가 3개뿐이라 `auto-fill` 그리드의
  마지막 트랙이 애매하게 비어 보인다, ③ 플레이스홀더를 4개로 늘리고 가로 스크롤로 만들어서 다음
  칸이 살짝 걸쳐 보이면 좋겠다는 추가 요청. 다음을 변경:
  - `.product-detail-grid`의 `gap`을 `--space-8`(64px) → 사용자가 직접 요청한 `--space-4`
    (16px)로 축소. 모바일 브레이크포인트(`max-width: 991px`)는 1열 스택이라 이 값이 세로
    간격이 되므로 성격이 달라 `--space-6`(32px) 그대로 유지.
  - `.product-gallery-thumbs`를 grid(auto-fill) → **flex-row + `overflow-x: auto`** 캐러셀로
    전환. 카드(`.product-gallery-thumb`)는 96×96px 고정 정사각형으로 되돌려 늘어나지 않게 함.
    플레이스홀더 개수 3 → 4(`product.js` `renderGallery()`).
  - 카드를 고정 크기로 되돌리면서 Attempt 1의 "썸네일이 세로로 늘어나 서머리 하단까지 채움"
    트릭이 빠지므로, 세로 flush를 다른 방식으로 유지해야 했다 — `.product-detail-media`를
    `justify-content: space-between`으로 바꿔서 사진(위)과 캐러셀(아래) 사이에만 남는 세로
    공간이 몰리게 하고, 캐러셀 하단은 항상 서머리 카드 하단과 flush 유지.
- 결과: 로컬 Docker 스택(`gong9ri-main-app-1`/`mysql-1`/`redis-1`, 이미 기동 중이던 걸 재사용)을
  `docker compose build app && docker compose up -d app`로 재빌드·재시작해 실데이터
  (`product.html?id=9288`)로 확인. Attempt 1 때와 동일하게 브라우저가 이전 `components.css`/
  `product.js`를 10분 캐시(`Cache-Control: max-age=600`)로 물고 있어서, `<link>` href·
  `<script>` src에 캐시버스팅 쿼리를 붙여 강제로 재요청한 뒤 측정:
  - 그리드 `column-gap`: `16px` (요청대로 축소 확인)
  - `mediaH`/`summaryH`: `549.140625` / `549.140625` — 카드가 고정 크기로 바뀌었어도 flush 유지
    확인
  - 플레이스홀더 `itemCount`: `4`
  - 캐러셀 스크롤: 기본(아이템 4개, 폭 487px) 상태에서는 `scrollWidth(487) === clientWidth(487)`
    라 스크롤 불필요(정상 — 컬럼이 넓으면 4개가 한 번에 다 들어가는 게 맞음). 아이템을 8개로
    늘려 임시 테스트하니 `scrollWidth(824) > clientWidth(487)`로 스크롤 활성화되고, 스크린샷에서
    5번째 카드가 오른쪽에 살짝 걸쳐 보이는 것과 하단 스크롤바 확인 — 요청한 "반 장 정도 더 보이는"
    효과 그대로 동작.
  - 별도 자동 테스트 스위트는 없다(정적 HTML/CSS/JS).
- 증거(JS 측정 + 스크린샷):
  - `{"gridGap":"16px","mediaH":549.140625,"summaryH":549.140625,"itemCount":4}`
  - 아이템 8개 임시 주입 시 `{"scrollWidth":824,"clientWidth":487,"isScrollable":true}` +
    스크린샷에서 5번째 카드 peek·하단 스크롤바 확인.

## Attempt 3 — 2026-08-21 ✅ PASS
- 시도: (사용자 리포트) Attempt 2까지 해도 "사진만 붕 떠있는 느낌"이라는 지적. 원인은 사진이
  여전히 1:1 정사각형 고정이라, `justify-content: space-between`이 사진과 캐러셀 사이에 빈
  공간을 몰아넣어 사진이 위쪽에 혼자 떠 있는 것처럼 보였던 것. 사용자 요청대로 다음을 변경:
  - `.product-detail-image`의 `aspect-ratio: 1/1` 고정을 없애고(`aspect-ratio: auto`로 상위
    `.card-image` 공용 규칙의 1:1을 취소), `.product-gallery`/`.product-detail-image` 모두
    `flex: 1 1 auto`로 바꿔 **사진이 캐러셀 시작 지점까지 세로로 꽉 차게** 함(빈틈 var(--space-2)
    수준만 남김). `.product-detail-media`의 `justify-content: space-between`은 제거(더 이상
    빈 공간을 분배할 필요가 없어짐).
  - 이어서 사용자가 "우측으로도 꽉 채워 서머리까지"라고 추가 요청 — `.product-detail-grid`의
    `column-gap`을 16px → **0**으로. 사진 오른쪽 끝이 서머리 카드 왼쪽 끝에 완전히 붙음.
  - **버그 발견·수정**: 처음엔 모바일(`max-width: 991px`, 1열 스택) 폴백으로 `.product-gallery`/
    `.product-detail-image`에 `flex: 0 0 auto` + `aspect-ratio: 1/1`을 기존 그리드 미디어쿼리
    블록(파일 앞쪽) 안에 추가했는데, 실측해보니 모바일에서 사진이 **높이 0으로 완전히 사라짐**
    (`imgHeight: 2`, 사실상 안 보임). 원인: 미디어쿼리는 특이도를 더해주지 않고, 데스크톱 기본
    규칙(`flex: 1 1 auto` 등)이 파일 순서상 그 미디어쿼리 블록보다 **뒤에** 있어서 캐스케이드상
    기본 규칙이 이겨버림(모바일 여부와 무관하게). 모바일 오버라이드 블록을 데스크톱 기본 규칙
    "다음"으로 옮겨서 해결.
- 결과: `docker compose build app && up -d app`로 재빌드·재시작 후 실데이터
  (`product.html?id=9288`)로 데스크톱(1100px)·모바일(420px) 둘 다 재확인.
  - 데스크톱: `columnGap: "0px"`, `galleryRight(542.5) === summaryLeft(542.5)`(사진이 서머리
    카드에 완전히 붙음), `thumbsBottom(692.53) === summaryBottom(692.53)`(여전히 flush),
    `mediaH === summaryH === 571.53`.
  - 모바일(수정 전): `imgAspectRatio: "auto"`, `imgHeight: 2` — 사진이 사실상 안 보임(회귀 발견).
  - 모바일(수정 후): `imgAspectRatio: "1 / 1"`, `imgHeight: 400` — 정상 복구, 스크린샷으로도
    사진이 캐러셀 위에 정상 노출됨을 확인.
  - 별도 자동 테스트 스위트는 없다(정적 HTML/CSS/JS).
- 증거(JS 측정 + 스크린샷):
  - 데스크톱: `{"columnGap":"0px","mediaH":571.53125,"summaryH":571.53125,"galleryRight":542.5,"summaryLeft":542.5,"thumbsBottom":692.53125,"summaryBottom":692.53125}`
  - 모바일 회귀(수정 전): `{"imgAspectRatio":"auto","imgHeight":2,"viewportWidth":425}`
  - 모바일(수정 후): `{"imgAspectRatio":"1 / 1","imgHeight":400,"viewportWidth":425}`

## Attempt 4 — 2026-08-21 ✅ PASS
- 시도: Attempt 3까지 해도 사용자가 "여전히 서머리랑 사진 사이 뜬다"고 재차 지적. 처음엔 브라우저
  캐시(반복된 원인) 의심했으나, 사용자가 직접 "캐러셀만 갤러리 형식이고 위에 상품이미지는 갤러리
  필요 없어" / "갤러리가 공간 잡아먹고 있었네" / "갤러리 빼버리고 사진으로 빡 땡겨"라고 원인과
  방향을 특정 — 화살표·카운터 오버레이용 `.product-gallery` 래퍼 div 자체를 걷어내고 `#product-image`
  를 `.product-detail-media`의 직계 자식으로 단순화하기로 함:
  - `product.html`: `.product-gallery` 래퍼·좌우 화살표 버튼(`#product-gallery-prev/next`)·
    카운터(`#product-gallery-counter`) 마크업 전부 제거. `#product-image`만 남김.
  - `product.js`: `galleryPrevBtn`/`galleryNextBtn`/`galleryCounterEl` 참조와 `moveGallery()`,
    관련 클릭 바인딩 전부 제거. 사진 전환은 캐러셀 썸네일 클릭(`renderGallery()` 안의 기존
    로직)만으로 하게 됨 — 갤러리 개념 자체는 캐러셀에만 남음.
  - `components.css`: `.product-detail-media .product-gallery` 규칙 삭제, `.product-gallery-arrow*`/
    `.product-gallery-counter` 죽은 규칙 삭제.
  - **진짜 원인 발견**: 래퍼를 걷어내고 다시 재보니 `imageRight(448) !== summaryLeft(542.5)`로
    여전히 94.5px 뜸 — `.product-detail-image`(단일 클래스, 특이도 낮음) 레거시 규칙이 파일 앞쪽
    (line 536, Option A 리디자인 이전 잔재)에 `max-width: 400px`로 남아있었던 게 원인. `width`
    속성은 서로 달라 캐스케이드로 안 덮이고 `max-width`만 별도로 적용돼 사진 폭을 400px로
    캡핑하고 있었다 — 지금까지 세 번의 시도 내내 이 규칙이 조용히 사진을 좁혀왔던 것. 삭제.
- 결과: `docker compose build app && up -d app` 두 번(래퍼 제거 후 1차, `max-width` 규칙 삭제 후
  2차) 재빌드·재시작 후 실데이터로 확인.
  - 래퍼 제거 직후(수정 전, `max-width` 버그 아직 존재): `imageRight: 448`, `summaryLeft: 542.5`
    — 94.5px 갭 재현 확인.
  - `max-width: 400px` 삭제 후: `imageRight: 542.5 === summaryLeft: 542.5`, `mediaH === summaryH
    === 571.53`, `thumbsBottom === summaryBottom === 692.53` — 완전히 flush.
  - 모바일(420px) 스크린샷: 화살표·카운터 없이 사진 하나만 꽉 차게 보이고, 캐러셀 4슬롯 정상.
  - 별도 자동 테스트 스위트는 없다(정적 HTML/CSS/JS).
- 증거(JS 측정):
  - 래퍼 제거 직후(버그 존재): `{"imageRight":448,"summaryLeft":542.5,...}`
  - 최종: `{"mediaH":571.53125,"summaryH":571.53125,"imageRight":542.5,"summaryLeft":542.5,"thumbsBottom":692.53125,"summaryBottom":692.53125}`

## Attempt 5 — 2026-08-21 ✅ PASS
- 시도: Attempt 3~4의 "사진이 남는 세로 공간을 flex-grow로 다 채운다" 방식이 컨테이너
  `max-width`(1320px 부근)에서 사용자가 직접 지적한 새 문제를 만들었다 — 사진 높이가 서머리
  content 높이(폭과 무관, 거의 고정값)에 묶여있는데 컨테이너가 넓어질수록 폭만 늘어나서, 사진이
  옆으로 퍼진 비율(1.39:1, 실측: 컨테이너 1224px에서 612×441)이 됐다. "완벽한 flush"와 "사진
  비율이 정상적인 것"은 이 구조에서 근본적으로 양립 불가 — 서머리 높이가 상품마다 다른데 사진
  비율은 일정해야 하기 때문. 이번엔 후자를 우선하기로 결정:
  - `.product-detail-image`를 다시 `aspect-ratio: 1/1` 고정(`flex: 0 0 auto`)으로 되돌림.
    Attempt 3에서 추가했던 모바일 전용 오버라이드 미디어쿼리는 데스크톱과 규칙이 같아져서
    통째로 삭제(중복 제거).
  - `.product-detail-media`의 `gap`을 `--space-2` → `--space-3`로 소폭 키움(사진·캐러셀 사이
    최소 여백). 캐러셀 아래 서머리 하단까지 약간의 여백이 남을 수 있는데, 이번엔 이걸 "붕 뜬"
    걸로 보지 않기로 함 — 사진 자체가 정상 비율의 카드로 보이는 게 더 중요하다고 판단.
  - 컬럼 사이 가로 `column-gap: 0`(사진이 서머리 카드에 완전히 붙는 것)은 이번 이슈와 무관해
    그대로 유지.
- 결과: `docker compose build app && up -d app` 재빌드·재시작 후 뷰포트 1920px(컨테이너
  `max-width` 1224px 그대로 적용된 실측 전체 폭)에서 확인.
  - 수정 전(Attempt 3/4 상태): `{"imageWidth":612,"imageHeight":441.140625,"ratio":"1.39"}`
  - 수정 후: `{"containerWidth":1224,"imageWidth":612,"imageHeight":612,"ratio":"1.00"}` — 정확히
    정사각형.
  - 모바일(420px) 스크린샷: 여전히 정사각형 사진 + 캐러셀 4슬롯 정상.
  - 별도 자동 테스트 스위트는 없다(정적 HTML/CSS/JS).
- 증거(JS 측정): `{"containerWidth":1224,"imageWidth":612,"imageHeight":612,"ratio":"1.00","summaryBottom":845,"windowWidth":1920}`
