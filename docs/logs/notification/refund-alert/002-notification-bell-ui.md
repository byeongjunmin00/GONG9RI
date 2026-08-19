# 002-notification-bell-ui — 알림 벨 UI + 읽음 처리 (로그)

## Attempt 1 — 2026-08-19  ✅ PASS (도중에 실수 1건 발견·수정)

- 시도: 알림 읽음 처리(개별/전체) 백엔드 추가 + 헤더 알림 벨 프론트 신규.
- 겪은 실수: 새 `NotificationService`를 만들려고 `Write`로 파일을 새로 썼는데, **같은 이름의 서비스가 이미 존재하고 있었다는 걸 놓쳐서 그 내용을 통째로 덮어씀**(환불 알림 생성 로직 `createTeamRefundedNotifications` 전체가 사라짐) — `./gradlew compileJava`가 `TeamRefundedEventListener`에서 그 메서드를 못 찾는다고 바로 잡아줘서 즉시 발견. `git show HEAD:...`로 원본을 복구하고 내가 추가하려던 메서드만 병합해서 해결. 새 서비스 클래스를 만들기 전에 동일 이름이 이미 있는지 먼저 확인했어야 했다는 교훈.
- 겪은 버그: "전체 읽음" 통합 테스트가 `AssertionError: JSON path "$.data[0].isRead" expected:<true> but was:<false>`로 실패 — 벌크 `@Modifying` UPDATE 쿼리가 영속성 컨텍스트를 안 거치고 DB를 직접 바꾸다 보니, 같은 트랜잭션 안에서 그 전에 로드된 `Notification` 엔티티를 다시 조회하면 캐시된 옛 값(`isRead=false`)이 그대로 보이는 전형적인 JPA 함정. `@Modifying(clearAutomatically = true)`로 해결.
- 검증:
  - `./gradlew test` 전체 재실행 — **BUILD SUCCESSFUL**(중간에 로컬 Redis 잔여 상태로 인한 `LoginRateLimitFilterTest`의 무관한 1회성 실패가 있었으나 재실행으로 통과 확인, 이미 알려진 패턴).
  - 로컬 `bootRun`에서 실제 회원가입 API로 테스트 계정 2개 생성(이메일 인증만 SQL로 우회) → 로그인 → 세션 쿠키로 전체 흐름 curl 검증: 빈 목록 → 알림 2건 삽입(SQL) → 개별 읽음(성공, 재조회로 1건만 읽음 확인) → 전체 읽음(성공, 재조회로 둘 다 읽음 확인) → 다른 계정으로 타인 알림 읽음 시도 시 403 확인. 검증 후 테스트 계정·알림·쿠키 파일 전부 직접 정리.
  - 정적 자산(`js/header-notifications.js`, 관련 CSS, 헤더 partial 마크업) 실제 서빙 확인.
  - 브라우저로 실제 벨 클릭·드롭다운 열림·시각적 확인은 못함(이 세션에 브라우저 자동화 도구가 없음) — 사용자에게 실제 화면에서 확인해달라고 안내 필요.
