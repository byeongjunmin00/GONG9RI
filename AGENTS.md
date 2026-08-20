# AGENTS.md

이 저장소에서 코딩 에이전트(Claude Code, Codex 등)가 **반드시 따라야 하는 작업 방식**을 정의한다.
이 파일은 도구에 상관없이 적용되는 **공통 진실의 원천(source of truth)**이다.

---

## 실행 흐름 (Execution Workflow)

모든 작업은 아래 **3단계(Plan → Generate → Evaluate)**를 순서대로 밟는다.
Plan과 Generate 사이에는 **휴먼 게이트(사람 승인)**라는 관문이 있다. 단계를 건너뛰지 않는다.

```
1. Plan (계획)
      │
   ══[ 휴먼 게이트: 사람 승인 ]══   ← 단계가 아니라 관문. 승인 전엔 코드 생성 금지
      │
2. Generate (생성)
      │
3. Evaluate (평가)
      │
   통과? ──no──> 루프 (아래 규칙)
      │yes
      ▼
    완료
```

### 1. Plan - 계획
- 코드를 만들기 **전에** 계획(무엇을·어떻게·통과 기준)을 문서로 제시한다. 계획 없이 생성하지 않는다.
- **계획은 채팅창이 아니라 `docs/dev/ongoing/{작업}.md` 문서로 남긴다** — 승인도 이 문서를 기준으로 받는다.
- 상세 절차: **`docs/workflow/plan-guide.md`**.

### 휴먼 게이트 - 사람 승인 (Plan → Generate 관문)
- 사람이 계획 문서를 **명시적으로 승인**하기 전에는 Generate로 넘어가지 않는다.
- 승인 없이 파일을 수정/생성하지 않는다.
- 이것은 작업 단계가 아니라 **통과 관문**이다.
- (Claude Code 사용 시: Plan Mode로 이 관문을 기계적으로 강제한다)

### 2. Generate - 생성
- **승인된 계획대로만** 구현한다. 범위를 임의로 넓히지 않는다 (벗어나면 Plan으로 재승인).
- 상세 절차: **`docs/workflow/generate-guide.md`**.

### 3. Evaluate - 평가
- **계산적 평가**(`./gradlew test`) + **추론적 평가**(계획·규칙·정책 준수)를 모두 수행하고, 결과를 사실대로 보고한다.
- 통과 시: design.md 갱신 + ongoing→changes 채번 이동. 상세 절차: **`docs/workflow/evaluate-guide.md`**.

### 루프 규칙 (Evaluate 통과 실패 시)
- **같은 접근으로 고칠 수 있는 실패** → Generate로 돌아가 2~3만 반복한다 (재승인 불필요).
- **접근 자체를 바꿔야 하는 실패** → Plan으로 돌아가 **새 계획을 승인(휴먼 게이트)**받은 뒤 다시 진행한다.
- 무한 반복을 피한다: 같은 방식으로 3회 연속 실패하면 멈추고 사용자에게 상황을 보고한다.

### 실행 모드 — 지금은 기본(gated) 모드만

- 지금은 **기본(gated) 모드만** 사용한다: Plan → **휴먼 게이트(승인)** → Generate → Evaluate.
- **격리 = 브랜치**: `main`에서 작업 브랜치를 만들어 진행하고(`git switch -c feature/{개념}-{기능} main`), 완료 후 `main`으로 merge한다. 상세: `docs/branch-guide.md`.
- **자율 모드(워크트리 오케스트레이션)는 아직 도입 전이다.** 휴먼 게이트를 생략하고 Plan→Generate→Evaluate→Finalize를 자동으로 도는 방식인데, 지금은 팀 규모·과제 성격상 보류했다. 나중에 필요해지면 `~/Downloads/basic-harness/`의 `.claude/workflows/plan-generate-evaluate-loop.js`, `run-autonomous.sh`, `.claude/hooks/log-activity.sh`를 참고해서 추가한다.

---

## 프로젝트 개요 (Project Overview)

- 이름: **GONG9RI** (`com.gong9ri`)
- 도메인: **공동구매 플랫폼** — 참여자가 모일수록 가격이 낮아지는 "공구팀" 거래 시스템. 판매자가 상품을 등록하고, 구매자는 혼자 구매하거나 공구팀을 신설/참가한다.
- 목적: 부트캠프 최종 프로젝트 (2인 팀: 민병준·전용운)
- 스택: Java 17 (toolchain) · Spring Boot 4.1.0 · Gradle (wrapper) · Spring MVC · Spring Data JPA · Spring Security(세션 기반 인증) · Bean Validation · Lombok · MySQL · Redis(캐싱) · JUnit 5

## 명령어 (Commands)

wrapper(`./gradlew`)를 사용한다. 전역 gradle 사용 금지.

```bash
./gradlew build          # 전체 빌드 + 테스트
./gradlew test           # 테스트만
./gradlew compileJava    # 빠른 컴파일 검증
./gradlew bootRun        # 앱 실행 (로컬 MySQL + Redis 둘 다 가동 필요)
./gradlew clean          # 산출물 정리
```

### 로컬 실행 전제 — MySQL **과 Redis** 둘 다 필요하다

`Redis 없이 띄우면 상품 목록·상세가 500이 나서 사이트가 사실상 안 열린다`(2026-08-21 실측). 캐시(`@Cacheable`)에 `CacheErrorHandler`가 없어 Redis 예외가 그대로 올라오기 때문이다.

| 기능 | Redis 없을 때 |
|---|---|
| 상품 목록 / 상세 | **500** (캐시 예외가 그대로 올라옴) |
| 실시간 인기 검색어 | 200이지만 **항상 빈 목록** (fail-open 설계) |
| 로그인 | 200 (rate limit이 fail-open) |

**띄우는 방법은 두 가지다. 둘 다 유효하다.**

```bash
# (권장) 저장소의 docker-compose로 MySQL·Redis·앱을 한 번에
docker compose up -d

# 또는 로컬에 직접 설치한 MySQL·Redis에 붙여서 앱만 실행
brew services start mysql && brew services start redis   # macOS 예시
./gradlew bootRun
```

`docker-compose.yml`에는 DB/Redis 접속값이 평문으로 들어 있는데 **로컬 전용 값이라 커밋해도 되는 것**이다. 반면 `SENDGRID_API_KEY`·`KAKAO_CLIENT_SECRET`·`PORTONE_API_SECRET` 같은 **진짜 비밀값은 `.env`(gitignore 대상)** 로 따로 둔다. 이 값들이 없으면 로컬에서 결제·카카오 로그인·메일 발송은 테스트할 수 없다(앱은 정상 기동한다).

> [!note] "실시간 검색어는 배포 환경에서만 되는 기능"이 아니다
> 로컬에서 비어 보이는 건 **검색 기록이 없어서**다. `SearchTrendService`는 오늘 날짜 키(`search-trend:yyyyMMdd`)의 ZSET에 검색어 점수를 쌓는 구조라, **검색을 직접 해봐야 순위가 생긴다.** TTL 2일이라 어제 것도 남지 않는다. 배포 환경에는 실사용 검색이 쌓여 있어서 보이는 것뿐이다.

## 컨텍스트 맵 (Context Map)

아래 상황이 되면 **해당 문서를 먼저 읽고** 그 규칙을 따른다. (필요할 때만 읽는 on-demand 참조)

| 이럴 때 | 읽을 문서 / 위치 |
|---------|------------------|
| **Plan(계획) 수립** | `docs/workflow/plan-guide.md` |
| **Generate(생성) 진행** | `docs/workflow/generate-guide.md` |
| **Evaluate(평가) 진행** | `docs/workflow/evaluate-guide.md` |
| **개발 이력·증거 기록 (성공/실패 매 시도)** | `docs/logs-guide.md` |
| **개발문서(ongoing, design.md, changes/) 작성·수정** | `docs/dev-doc-guide.md` |
| **API 명세 작성·참조** | `docs/api/` (README부터) |
| **테이블(스키마) 명세 작성·참조** | `docs/db/` (README부터) |
| **코드 작성·수정** | `docs/code-convention.md` |
| **커밋 메시지 작성** | `docs/commit-convention.md` |
| **브랜치·git 작업 (분기·merge)** | `docs/branch-guide.md` |
| **정책(비즈니스 규칙) 참조·작성** | `docs/policy/` (README부터) |
| 전체 진행 현황 확인 | `docs/dev/ongoing/` (폴더 자체가 현황판) |
| **ERD·와이어프레임 확인** | `docs/ERD.md`, `docs/WIREFRAME.md` |

- 각 문서가 **해당 규칙의 원천**이다. AGENTS.md는 "언제 어디를 볼지"만 가리키고 세부는 중복 설명하지 않는다.
- 개발문서를 쓰기 전 `docs/dev-doc-guide.md`, 코드 전 `docs/code-convention.md`를 **먼저** 읽는다.

## 규칙 (Rules)

- 커밋/푸시는 사용자가 명시적으로 요청할 때만 한다.
- 되돌리기 어려운 변경(DB, 실행 설정)은 진행 전 사용자에게 확인한다.
- 평가 결과를 사실대로 보고한다. 통과하지 않았으면 "통과"라고 말하지 않는다.
