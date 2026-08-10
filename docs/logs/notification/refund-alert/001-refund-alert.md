# 001-refund-alert — 환불 완료 알림 생성 + 마이페이지 알림 조회 (로그)

> 관련: `docs/logs/team/deadline-check/002-event-messaging.md`(같은 작업의 이벤트 발행 쪽 절반).
> 계획 원천: `docs/dev/ongoing/refund-event-messaging.md`. DB 계약: `docs/db/notification.md`. API 계약: `docs/api/mypage.md`의 "## 알림" 섹션.

## Attempt 1 — 2026-08-10
- 시도: `docs/db/notification.md` 계약대로 신규 도메인 구현.
  - `entity/NotificationType`(enum, `TEAM_REFUNDED` 하나) + `entity/Notification`(id, member(ManyToOne, FK not null), type, message, relatedTeam(ManyToOne, FK nullable), isRead(default false), createdAt(`@CreatedDate`)) — `idx_member(member_id)` 인덱스, 하드 삭제 없음(삭제 메서드 자체를 안 둠).
  - `repository/NotificationRepository`(`findAllByMemberIdOrderByCreatedAtDesc`) — 마이페이지 목록 조회용, `idx_member` 활용.
  - `dto/NotificationResponse`(notificationId, type, message, relatedTeamId, isRead, createdAt) — `docs/api/mypage.md` 필드명 그대로.
  - `service/NotificationService.createTeamRefundedNotifications(TeamRefundedEvent event)` — 그 팀 환불된 결제의 구매자 전원(중복 제거, `LinkedHashSet`) + 상품 판매자 각각에게 `Notification` 1건씩 생성. member/team은 FK로만 쓰이고 필드를 안 읽어서 `getReferenceById`로 불필요한 SELECT 회피. **`@Transactional(propagation = Propagation.REQUIRES_NEW)`** — 이 메서드가 `TeamRefundedEventListener`(`@TransactionalEventListener(AFTER_COMMIT)`)에서 호출되는데, 기본 REQUIRED로 두면 원본 트랜잭션 커밋 직후 아직 스레드에 남아있는 리소스에 조인해버려 이 메서드의 INSERT가 실제로 커밋되지 않는 버그를 겪음(상세 원인/발견 과정은 `docs/logs/team/deadline-check/002-event-messaging.md`의 "막힌 지점" 참고) — REQUIRES_NEW로 물리적으로 독립된 트랜잭션임을 보장해 해결.
  - 메시지 문구: 구매자용 "참여하신 공구팀이 미성사되어 환불 처리되었습니다.", 판매자용 "등록하신 상품의 공구팀이 미성사되어 환불 처리되었습니다."(`docs/api/mypage.md` 예시 그대로).
- API: `GET /api/buyer/mypage/notifications`, `GET /api/seller/mypage/notifications` 추가.
  - `BuyerMypageService`/`SellerMypageService`에 `notifications(principal)` 메서드 추가(각각 `requireBuyer`/`requireSeller` 역할 체크 재사용 — 반대 역할 403 `FORBIDDEN`, 기존 mypage 엔드포인트들과 동일 패턴). `NotificationRepository`를 직접 주입해 `member.id` 기준으로만 조회(스코핑) — 별도 스코핑 리포지토리 메서드가 필요 없었음(buyer/seller 둘 다 "내 memberId" 기준이라 동일 쿼리 재사용).
  - `BuyerMypageController`/`SellerMypageController`에 `@GetMapping("/notifications")` 추가(신규 컨트롤러 분리 안 함 — 기존 mypage 컨트롤러에 자연스럽게 얹는 게 계층상 더 일관적이라고 판단).
  - 컨트롤러/DTO 계층에서 엔티티 직접 노출 없음(`docs/code-convention.md` 준수).
- 신규 테스트:
  - `BuyerMypageControllerTest`/`SellerMypageControllerTest`에 알림 관련 4케이스씩 추가: 조회 성공(`type`/`isRead` 필드 확인), 스코핑(본인만 조회), 반대 역할 403, 비로그인 401 — 기존 다른 엔드포인트 테스트와 완전히 같은 패턴.
- `service/NotificationService`는 별도 단위 테스트 파일을 새로 만들지 않고, 이벤트 통합 테스트(`event/TeamDeadlineEventFlowTest`, `service/TeamDeadlineServiceTest`)에서 end-to-end로 검증 — `createTeamRefundedNotifications`는 이벤트 발행 없이는 절대 단독으로 호출되지 않는 메서드라(오케스트레이션이 전부 이벤트 경유), 이벤트 흐름과 분리해서 테스트할 이유가 약하다고 판단.
- 결과: `./gradlew clean build` **BUILD SUCCESSFUL**(로컬 MySQL 8.4 standalone 기동 후 검증, 상세는 `docs/logs/team/deadline-check/002-event-messaging.md` 참고). XML 리포트 합산 총 104케이스, 실패/에러 0건.

## Evaluate — 2026-08-10  ✅ PASS

- 결과: `./gradlew test --rerun` 재실행 → **BUILD SUCCESSFUL**, XML 리포트 합산 `tests=104 failures=0 errors=0`(generator 보고와 일치, 캐시 아님 — 상세 실행 근거는 `docs/logs/team/deadline-check/002-event-messaging.md`의 Evaluate 기록 참고).
- 원인(판정 근거): `entity/Notification`·`entity/NotificationType`·`repository/NotificationRepository`·`dto/NotificationResponse`가 `docs/db/notification.md` 컬럼·인덱스·삭제정책(하드 삭제 없음)과 정확히 일치. `GET /api/buyer,seller/mypage/notifications` 응답 필드(`notificationId`/`type`/`message`/`relatedTeamId`/`isRead`/`createdAt`)와 에러코드(403/401)가 `docs/api/mypage.md`의 "## 알림" 섹션과 정확히 일치. `BuyerMypageService`/`SellerMypageService.notifications()`가 기존 `requireBuyer`/`requireSeller` 패턴을 재사용해 본인 `memberId`로만 스코핑(반대 역할 403) — `BuyerMypageControllerTest`/`SellerMypageControllerTest`의 조회 성공/스코핑/403/401 4케이스씩으로 검증됨. 계층분리(controller→service→repository, 엔티티 직접 노출 없음)·생성자주입(`@RequiredArgsConstructor`)·트랜잭션 기본 `readOnly=true`(쓰기 메서드만 명시, `createTeamRefundedNotifications`는 `REQUIRES_NEW`로 정당한 이유가 코드 주석에 명시됨)·로깅(SLF4J, `println` 없음, INFO로 도메인 이벤트 기록) 모두 `docs/code-convention.md` 준수 확인. `NotificationResponse.from`이 `relatedTeam.getId()`만 읽는 지점은 JPA lazy 프록시의 식별자 접근이라 추가 SELECT를 유발하지 않음(N+1 아님, `docs/code-convention.md`의 N+1 표에 이 신규 엔드포인트가 명시적으로 추가되진 않았으나 실질적 N+1 이슈 없음).
- 증거(API 샘플): `GET /api/seller/mypage/notifications` → `200 [{"notificationId":2,"type":"TEAM_REFUNDED","message":"등록하신 상품의 공구팀이 미성사되어 환불 처리되었습니다.","relatedTeamId":5,"isRead":false,"createdAt":"2026-08-10T..."}]` (컨트롤러 테스트 `notifications_success`가 검증한 형태와 동일). 스코핑: `notifications_scoping_onlyOwnNotifications`가 본인 알림만 반환됨을 확인, `notifications_forbidden_seller`/`notifications_unauthorized`가 각각 403/401 확인.
