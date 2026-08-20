# 004-header-logo-inline-9 — 헤더 로고를 워드마크 "9" 자리에 인라인 삽입 (로그)

## Attempt 1 — 2026-08-20

- 시도: 승인된 계획(`docs/dev/ongoing/header-logo-inline-9.md`, D안 + 원래 그라디언트)대로 구현.
  - **신규 에셋**: `images/logo-icon.png`(243×305, 알파 기준 여백 약 12px)를 알파 bbox(12,12,219,305) 기준으로 크롭해 `images/logo-icon-inline.png`(219×305, 여백 없음)로 새로 저장.
  - **`partials/header.html`**: `.site-header__logo`의 선행 `<img>` 제거. 워드마크를 `GONG` + `<span class="site-header__wordmark-mark" aria-hidden="true">`(빈 span, 마스크로 아이콘 표시) + `<span class="visually-hidden">9</span>`(스크린리더용, 기존 `base.css`의 `.visually-hidden` 재사용) + `RI` 순서로 재구성. 스크린리더는 aria-hidden 요소를 건너뛰므로 "GONG"+"9"+"RI" = "GONG9RI"로 그대로 읽힌다.
  - **`css/components.css`**: `.site-header__logo img` 규칙 삭제(더 이상 쓰는 img 없음). `.site-header__wordmark` `font-size`를 1.4rem → 1.6rem으로. 신규 `.site-header__wordmark-mark`: `height: 1.35em; width: 0.969em`(원본 219:305 비율 유지), `vertical-align: -0.16em`(위로 살짝만 튀어나오게), `background: var(--gradient-brand)` + `mask-image: url(/images/logo-icon-inline.png)`(+`-webkit-` 접두사)로 아이콘 모양대로 그라디언트를 노출. 얇은 라인아트가 인라인 크기(약 35px 높이)로 줄어들며 흐려 보이는 문제(시안 검토 중 발견)를 보정하기 위해 `filter: drop-shadow(...)` 5개를 겹쳐 선을 굵게 보이게 함.
  - `css/tokens.css`, `css/layout.css`는 계획대로 건드리지 않음(새 색상 토큰 불필요, 레이아웃 구조 불변).
- 결과:
  - `./gradlew compileJava` → `BUILD SUCCESSFUL`(이번 변경이 전부 정적 리소스+신규 PNG라 Java 컴파일 대상 없음 — 정상).
  - `./gradlew test`(전체 스위트, 원래는 evaluate-guide가 "전체 스위트 남발 금지"라고 하지만 이 변경을 스코프할 전용 테스트 클래스가 없어 전체로 확인) → **연속 두 번 실행에서 각각 32개, 71개 실패로 결과가 요동쳤고**, 둘 다 `NoSuchFileException: .../build/test-results/test/binary/in-progress-results-generic.bin`로 빌드 자체가 비정상 종료됨. 이번 변경은 Java 코드를 전혀 건드리지 않았는데 같은 코드로 두 번 돌려서 실패 개수가 달라진 것과, 실행 중 포트 8080이 이미 다른 프로세스(Docker 백엔드 경유, 이 저장소의 예전 빌드를 서빙 중임을 `curl`로 확인)에 점유돼 있던 것, 그리고 `git status`에 이 세션에서 손대지 않은 다른 파일들(`CacheConfig.java`, `ProductService.java` 등)이 이미 modified로 떠 있던 것을 종합하면, **이 저장소에 동시에 다른 작업(다른 세션/에이전트 또는 사용자 본인의 진행 중 작업)이 진행 중이라 발생한 환경적 충돌(DB/포트/빌드 디렉터리 경합)로 판단**된다. evaluate-guide의 역할 경계(포렌식 디버깅은 Evaluate 범위 밖)에 따라 더 깊이 원인을 파지 않음.
  - 브라우저 실사용 확인: 사용자가 목업(HTML 시안) 검토로 이미 시각적으로 확인·승인했고("됐어 내가 아까 컨펌 했으니까 미리보기 넘어가"), 로컬 8080이 다른 프로세스에 점유돼 이번 세션에서 실제 페이지로 재확인은 생략함.
  - 정적 파일 diff 확인(`git status`/`git diff` 범위 한정): 이번 세션에서 수정한 파일은 `partials/header.html`, `css/components.css`, 신규 `images/logo-icon-inline.png`뿐 — 계획에 없던 다른 파일은 건드리지 않음(계획 범위 준수).
- **미해결/참고 사항 (사람 확인 필요)**:
  - 실제 브라우저에서 로고 형태가 잘 보이는지, 모바일 브레이크포인트에서 안 깨지는지는 목업 기준으로만 확인됐고 실제 헤더(`localhost:8080`)에서는 이번 세션에서 재확인하지 못했다.
  - 전체 테스트 스위트의 32/71건 실패가 정말 이 변경과 무관한지는(가능성이 매우 높지만) 환경이 안정된 상태에서 한 번 더 돌려 확인하는 게 안전하다.
- 다음: Evaluate 단계로 진행. 계산적 평가는 `compileJava` 성공 + 전체 스위트 실패는 환경 요인으로 판단(근거 위 기재)한 채로 보고. 추론적 평가(계획 준수·컨벤션)는 이어서 수행.
