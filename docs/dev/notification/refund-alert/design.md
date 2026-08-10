# 환불 완료 알림 (notification/refund-alert) — Design

## 개요

공구팀이 마감(미성사)돼 환불이 발생하면, 그 팀의 환불된 결제 구매자 전원 + 상품 판매자 각각에게 `Notification` 레코드를 생성하는 기능이다. 실제 이메일/SMS 발송 채널은 없다 — DB 저장 + 마이페이지 조회까지만 지원한다.

`team/deadline-check`가 발행하는 "환불 완료" 이벤트(`TeamRefundedEvent`)를 구독해서 동작한다(상세 이벤트 흐름: `docs/dev/team/deadline-check/design.md`). 이 기능 자체는 알림 생성·조회만 책임지고, 언제/어떤 조건에서 환불이 발생하는지는 `team/deadline-check` 쪽 책임이다.

## API / 인터페이스

- `GET /api/buyer/mypage/notifications` — 구매자 본인 알림 목록
- `GET /api/seller/mypage/notifications` — 판매자 본인 알림 목록
- 상세 요청/응답/에러 스펙: `docs/api/mypage.md`의 "## 알림" 섹션이 원천.
- 컨트롤러는 기존 `BuyerMypageController`/`SellerMypageController`에 자연스럽게 추가돼 있다(신규 컨트롤러 분리 없음 — 마이페이지 하위 기능이라 계층상 더 일관적).

## 데이터 모델

- 신규 테이블 `notification`. 상세 컬럼/인덱스/관계: `docs/db/notification.md`가 원천.
- 하드 삭제 없음(알림 이력 보존) — 삭제 메서드 자체를 두지 않는다.

## 이벤트 소비

- `event/TeamRefundedEventListener`가 `TeamRefundedEvent`를 `@TransactionalEventListener(phase = AFTER_COMMIT)`로 구독해 `NotificationService.createTeamRefundedNotifications(event)`를 호출한다(이벤트 발행·발행 시점 보장은 `team/deadline-check` 책임).
- `NotificationService.createTeamRefundedNotifications`는 그 팀의 환불된 결제 구매자 전원(중복 제거, `LinkedHashSet`) + 상품 판매자 각각에게 `Notification` 1건씩 생성한다. member/team은 FK로만 쓰이므로 `getReferenceById`로 불필요한 SELECT를 피한다.
- **`@Transactional(propagation = Propagation.REQUIRES_NEW)`**가 필수다 — 이 메서드는 원본 트랜잭션(`TeamDeadlineService.processDeadline`)의 커밋 직후 `AFTER_COMMIT` 콜백으로 호출되는데, 기본 propagation(REQUIRED)으로 두면 아직 스레드에 남아있는 원본 트랜잭션 리소스에 조인해버려 이 메서드의 INSERT가 실제로 커밋되지 않고 사라진다(스프링 공식 문서가 명시하는 캐비앗, 실제로 겪은 버그 — `docs/logs/notification/refund-alert/001-refund-alert.md` 참고). REQUIRES_NEW로 물리적으로 독립된 새 트랜잭션임을 보장해 해결.
- 메시지 문구(상수화): 구매자용 "참여하신 공구팀이 미성사되어 환불 처리되었습니다.", 판매자용 "등록하신 상품의 공구팀이 미성사되어 환불 처리되었습니다."

## 규칙 / 검증

- 조회는 본인 알림만 스코핑된다 — `BuyerMypageService`/`SellerMypageService`가 각각 `requireBuyer`/`requireSeller` 역할 체크(반대 역할 403 `FORBIDDEN`) 후 `principal.getMember().getId()` 기준으로만 `NotificationRepository.findAllByMemberIdOrderByCreatedAtDesc`를 호출한다. buyer/seller 둘 다 "내 memberId 기준" 조회라 별도 스코핑 리포지토리 메서드 없이 동일 쿼리를 재사용한다.
- 비로그인 401.
- 엔티티(`Notification`)를 컨트롤러 응답으로 직접 노출하지 않는다 — `dto/NotificationResponse`로 변환.
- 알림 타입은 `enum NotificationType`으로 관리(현재 `TEAM_REFUNDED` 하나) — 향후 알림 종류가 늘어나면 이 enum에 추가.

## 관련 코드 위치

- `entity/Notification.java`, `entity/NotificationType.java`
- `repository/NotificationRepository.java` — `findAllByMemberIdOrderByCreatedAtDesc(memberId)`
- `dto/NotificationResponse.java`
- `service/NotificationService.java` — `createTeamRefundedNotifications(TeamRefundedEvent)`
- `service/BuyerMypageService.java`, `service/SellerMypageService.java` — `notifications(principal)`
- `controller/BuyerMypageController.java`, `controller/SellerMypageController.java` — `GET .../notifications`
- 소비하는 이벤트: `event/TeamRefundedEvent.java`, `event/TeamRefundedEventListener.java` (발행 쪽은 `docs/dev/team/deadline-check/design.md` 참고)
- 테스트:
  - `controller/BuyerMypageControllerTest.java`, `controller/SellerMypageControllerTest.java` — 조회 성공, 본인만 조회(스코핑), 반대 역할 403, 비로그인 401 (각 4케이스)
  - `NotificationService` 자체의 별도 단위 테스트는 없음 — 이벤트 발행 없이는 단독 호출되지 않는 메서드라(오케스트레이션이 전부 이벤트 경유) `event/TeamDeadlineEventFlowTest.java`, `service/TeamDeadlineServiceTest.java`에서 end-to-end로 검증한다.
