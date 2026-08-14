# 008-participants-list — 공구팀 상세: 참여자 목록 표시 (로그)

## Attempt 1 — 2026-08-14  ✅ PASS(구현 완료, Evaluate는 별도 단계)

- 시도: 승인된 계획(`docs/dev/ongoing/team-participants-list.md`)대로 구현. 스키마 변경 없음(계획대로).
  - **Repository**: `TeamParticipationRepositoryCustom`/`Impl`에
    `findAllByTeamIdWithMemberOrderByJoinedAtAsc(Long teamId)` 추가 — QueryDSL로
    `member`/`team`/`team.leader`를 fetch join(N+1 방지), `joinedAt` 오름차순.
    "리더 우선" 정렬까지 SQL `ORDER BY`에 CASE로 억지로 넣지 않고, 리더 여부는 서비스 계층에서
    안정 정렬(stable sort)로 마무리하는 쪽을 선택(리더 판정 로직을 리포지토리 QueryDSL 표현식이
    아니라 평범한 Java 비교로 두는 게 더 읽기 쉽다고 판단).
  - **DTO**: `dto/TeamParticipantResponse.java`(record) 신규 — `displayName`/`isLeader`/`joinedAt` 3개
    필드만. `memberId`/이메일/실명 원문 없음. `maskName()`: 첫 글자 노출 + 나머지 글자수만큼 `*`,
    1글자 이름은 극단 케이스로 판단해 `*` 하나로 전체 마스킹(계획의 리스크 항목에 대한 Generate
    재량 처리).
  - **Service**: `TeamService.participants(Long teamId)` 추가 — `groupBuyTeamRepository.existsById`로
    존재 검증(없으면 `TEAM_NOT_FOUND`) 후 위 repository 조회 결과를 `Comparator.comparing(리더여부).reversed()`로
    안정 정렬(joinedAt 오름차순으로 이미 온 리스트라 동일 그룹 내 순서는 유지됨) → DTO 매핑.
    기존 `join`/`create`와 동일하게 `@Transactional(readOnly=true)`(클래스 기본값) 그대로 사용,
    별도 쓰기 트랜잭션 불필요.
  - **Controller**: `TeamController.participants()` — `GET /api/teams/{teamId}/participants` 추가.
    인증 파라미터(`@AuthenticationPrincipal`) 없음 — 비로그인 공개.
  - **Security**: `SecurityConfig`에 `GET /api/teams/*/participants` permitAll 추가(기존
    `POST /api/teams/{teamId}/join`은 그대로 인증 필요 유지 — 경로 패턴이 GET만 매칭하도록 주의).
  - **API 문서**: `docs/api/team.md`에 신규 엔드포인트 섹션 추가(요청/응답 예시, 마스킹·정렬 규칙,
    `TEAM_NOT_FOUND` 에러 표).
  - **프론트**: `product.js`의 `createTeamItem()`에 "참여자 보기" 토글 버튼 + 펼치기 패널
    (`.participants-panel`) 추가. 처음 펼칠 때만 `GET /api/teams/{teamId}/participants`를 개별
    조회(팀 목록 로드 시 한꺼번에 불러오지 않음, 계획대로). 참여 시각은 `formatApproxJoinedAt()`로
    "N일 전 참여"/"오늘 참여"처럼 대략적으로만 표시(서버는 ISO-8601 원본 그대로 내려줌). 팀장은
    `.badge.badge-leader`("팀장") 배지로 표시. `components.css`에 `.badge-leader`,
    `.participants-panel`, `.participant-list`, `.participant-item*` 스타일 추가(기존 `.team-item`이
    flex-wrap이라 `.participants-panel { flex-basis: 100% }`로 다음 줄에 펼쳐지게 함).
  - **테스트**(`TeamControllerTest`, 5케이스 신규):
    1) `participants_success_maskedAndSortedLeaderFirst` — 리더의 `joinedAt`을 일부러 다른
       참여자들보다 나중으로(`ReflectionTestUtils.setField`, `ChatLogRecorderTest`의 기존 패턴 재사용)
       설정해도 "리더 먼저" 규칙이 타임스탬프와 무관하게 적용되는지, 그리고 나머지 두 참여자가
       `joinedAt` 오름차순으로 나오는지 확인. 마스킹 형태(`김**`/`이**`/`박**`)도 같이 확인.
    2) `participants_singleCharacterName_maskedFully` — 1글자 이름("김")이 `*` 하나로 마스킹되는지.
    3) `participants_doesNotExposeRealNameOrMemberId` — 응답 본문 문자열에 실명 원문("정하윤")·
       이메일·"memberId"/"email" 키가 전혀 포함되지 않는지 `content().string(not(containsString(...)))`로
       직접 assert.
    4) `participants_publicAccess` — 인증 없이(`.with(asUser(...))` 없이) 호출해도 200 + `success:true`.
    5) `participants_teamNotFound` — 존재하지 않는 teamId → 404 `TEAM_NOT_FOUND`.
  - N+1 검증은 코드 리뷰로만 확인(쿼리 카운트 자동 검증 테스트의 기존 선례가 이 저장소 테스트
    스위트에 없어 새로 도입하지 않음, 계획의 "코드 리뷰 또는 쿼리 카운트 검증" 중 전자를 선택) —
    `findAllByTeamIdWithMemberOrderByJoinedAtAsc`가 `member`/`team`/`team.leader` 3개 연관관계를
    모두 fetch join하므로 참여자 수와 무관하게 쿼리 1번으로 끝난다.
- 결과:
  - `./gradlew compileJava`, `./gradlew compileTestJava` 모두 성공.
  - `./gradlew test --tests "*TeamControllerTest*"`: 20/20 통과(신규 5케이스 포함).
  - `./gradlew test`(전체 스위트, `--rerun-tasks` 없이도 실제 실행됨): **209개 전체 통과, 실패/에러 0개**
    — 이번 시도에서는 (007번 로그에 기록된 것과 달리) 기존 환경발 테스트 흔들림이 재현되지
    않았다.
- 증거(API 계약 기준, MockMvc):
  - `GET /api/teams/{teamId}/participants` (리더 "김철수" + 참여자 "이영희"/"박준형") →
    `200 {"data":[{"displayName":"김**","isLeader":true,"joinedAt":"..."},
    {"displayName":"이**","isLeader":false,"joinedAt":"..."},
    {"displayName":"박**","isLeader":false,"joinedAt":"..."}]}` — 리더가 `joinedAt`상 가장 늦어도 1번으로 온다.
  - 존재하지 않는 teamId(999999) → `404 {"code":"TEAM_NOT_FOUND"}`.
  - 비로그인 호출 → `200 {"success":true,...}`.
- 다음: Evaluate 단계에서 `./gradlew test` 재확인, 계획 대비 스코프 준수 재검토,
  통과 시 `docs/dev/team/crud/design.md` 갱신 + `docs/dev/ongoing/team-participants-list.md` →
  `docs/dev/team/crud/changes/003-participants-list.md`로 채번 이동은 Evaluate 몫(이번 시도에서는
  하지 않음).

## Evaluate — 2026-08-14  ✅ PASS

- 결과:
  - **계산적 평가**: `./gradlew test --rerun-tasks`로 전체 스위트 강제 재실행(캐시 `UP-TO-DATE` 아님, 실제
    실행) → `BUILD SUCCESSFUL`, JUnit XML 리포트(`build/test-results/test/*.xml`) 집계 기준 **209/209
    통과, failures=0, errors=0**. generator가 보고한 결과와 일치 — 이번 회차에는 007번 로그에 있던 환경발
    흔들림(4개 실패)이 재현되지 않았다. (단, 이 결과가 항상 재현된다는 보장은 아니다 — 이전에도 회차마다
    나타났다 안 나타났다 했으므로, 다음 회차에도 반드시 직접 재확인해야 한다는 점을 로그로 남겨둔다.)
  - **테스트 커버리지 대 평가 기준 대조** (`TeamControllerTest.java` 직접 읽고 확인):
    1) `participants_success_maskedAndSortedLeaderFirst` — 리더의 `joinedAt`을 가장 나중으로
       `ReflectionTestUtils.setField`로 세팅해도 리더가 1번, 이후 `joinedAt` 오름차순으로 나오는지와
       마스킹 형태(`김**`/`이**`/`박**`)를 함께 확인 → "정상 조회+마스킹+정렬" 기준 충족.
    2) `participants_singleCharacterName_maskedFully` — 1글자 이름("김")이 `*` 하나로 마스킹되는지 확인
       → "1글자 이름 마스킹" 기준 충족.
    3) `participants_doesNotExposeRealNameOrMemberId` — 응답 본문 문자열에 실명("정하윤")·이메일·
       `memberId`/`email` 키가 없음을 `content().string(not(containsString(...)))`로 직접 assert → "실명/
       memberId 미노출 직접 assert" 기준 충족.
    4) `participants_publicAccess` — 인증 없이 호출해도 200 + `success:true` → "비로그인 200" 기준 충족.
    5) `participants_teamNotFound` — 존재하지 않는 teamId(999999) → 404 `TEAM_NOT_FOUND` → "404
       TEAM_NOT_FOUND" 기준 충족.
    - 계획의 평가 기준 5개 전부가 실제 assert로 커버됨(단순 200만 확인하는 얕은 테스트 아님).
  - **추론적 평가**:
    - **스키마 변경 없음 확인**: `git diff --stat -- src/main/java/com/gong9ri/gong9ri/entity/` 결과 없음
      (엔티티 diff 자체가 없음), 저장소 전체에서 마이그레이션 파일 검색 결과도 이번 기능과 무관 — 계획대로
      스키마 변경 없음.
    - **스코프 제외 항목 준수**: 닉네임 필드/입력 UI, 참여자 상세 클릭, 페이지네이션 — 코드에 전혀 없음
      (DTO는 `displayName`/`isLeader`/`joinedAt` 3필드뿐, `product.js`에 클릭 상세 이동 없음, 목록에
      페이지네이션 파라미터 없음). `ErrorCode.TEAM_NOT_FOUND`도 기존 코드 재사용(신규 에러코드 추가 없음).
    - **`SecurityConfig` permitAll 스코프**: `git diff`로 직접 확인 — 추가된 줄은
      `.requestMatchers(HttpMethod.GET, "/api/teams/*/participants").permitAll()` 단 한 줄. `HttpMethod.GET`
      한정이라 `POST /api/teams/{teamId}/join`(인증 필요)에는 영향 없음. 경로도 `/api/teams/*/participants`로
      정확히 참여자 목록 엔드포인트만 매칭.
    - **계층 분리·N+1**(`docs/code-convention.md`): Controller(`TeamController.participants`)는 위임만,
      비즈니스 로직(존재 검증·정렬·마스킹)은 `TeamService.participants`/`TeamParticipantResponse`에 위치.
      `TeamParticipationRepositoryImpl.findAllByTeamIdWithMemberOrderByJoinedAtAsc`가 `member`/`team`/
      `team.leader` 3개 연관관계를 모두 fetch join(쿼리 1회) — N+1 방지 확인(코드 리뷰 기준, 계획에서 허용한
      두 방법 중 코드 리뷰 선택).
    - **엔티티 미노출**: 응답 DTO(`TeamParticipantResponse`)가 record로 엔티티를 직접 감싸지 않고 정적 팩토리
      `from()`으로 변환.
  - `docs/api/team.md` diff 확인 — 신규 엔드포인트 계약(요청/응답 예시, 마스킹·정렬 규칙, 에러 표) 반영됨.
- 결론: 계산적·추론적 평가 모두 통과. 계획과 실제 구현 사이 스코프 이탈 없음.
- 후속 조치(Evaluate 수행):
  - `docs/dev/team/crud/design.md`에 "참여자 목록 표시" 절 신규 추가 + API/관련 코드 위치 섹션 갱신.
  - `docs/dev/ongoing/team-participants-list.md` → `docs/dev/team/crud/changes/003-participants-list.md`로
    채번 이동(`mv`, 기존 `changes/`에 001-crud, 002-target-participants가 있어 다음 번호 003).
