# 환불 완료 알림 (notification/refund-alert) — Design

## 개요

공구팀이 마감(미성사)돼 환불이 발생하면, 그 팀의 환불된 결제 구매자 전원 + 상품 판매자 각각에게 `Notification` 레코드를 생성하는 기능이다. 실제 이메일/SMS 발송 채널은 없다 — DB 저장 + 마이페이지 조회 + (2026-08-19부터) 헤더 알림 벨 UI로 확인할 수 있다.

**2026-08-19 추가 — 알림 벨 UI + 읽음 처리**: 조회 API(`GET .../notifications`)는 처음부터 있었는데, 그걸 보여줄 화면 자체가 없었고(백엔드만 완성, 프론트 미연동 상태로 방치돼있었음) 읽음 처리 수단도 없었다(모든 알림이 영원히 `isRead=false`). 헤더에 벨 아이콘(`partials/header.html`의 `#header-notifications`, `js/header-notifications.js`)을 추가하고, 읽음 처리 API 2개(개별/전체)를 신규로 붙였다. 로그인한 구매자·판매자 모두에게 뜬다(역할과 무관한 회원 단위 기능이라 `js/header-auth.js`의 nav `data-role` 매칭 대신, `gong9ri:auth-resolved` 이벤트를 직접 구독해 로그인 여부만으로 노출을 결정 — `js/chat-widget.js`와 같은 패턴). 상세 변경: `changes/002-notification-bell-ui.md`.

"환불 완료" 이벤트(`TeamRefundedEvent`)를 구독해서 동작한다 — **PortOne 연동 이후**(`docs/dev/payment/portone/design.md`) 이 이벤트는 `team/deadline-check`가 아니라 `PaymentRefundService`(payment/portone)가 PortOne 결제취소 API 응답을 확인한 뒤 결제 건별로 발행한다. 이 기능 자체는 알림 생성·조회만 책임지고, 언제/어떤 조건에서 환불이 발생·확정되는지는 `team/deadline-check`·`payment/portone` 쪽 책임이다.

> **알려진 동작 변화**: 예전에는 팀 하나의 마감 처리마다 `TeamRefundedEvent`가 정확히 1번 발행돼(그 팀의 환불된 결제 구매자 전원을 한 번에 묶어서) 판매자도 알림을 1건만 받았다. 지금은 결제 건이 실제로 확정될 때마다(비동기 PortOne 취소 확인 시점이 결제 건마다 다를 수 있어) 개별 발행되므로, 같은 팀에 결제가 여러 건이면 판매자가 그 건수만큼 여러 번 알림을 받을 수 있다.

## API / 인터페이스

- `GET /api/buyer/mypage/notifications` — 구매자 본인 알림 목록
- `GET /api/seller/mypage/notifications` — 판매자 본인 알림 목록
- `POST /api/{buyer,seller}/mypage/notifications/{notificationId}/read` — 알림 1건 읽음 처리(2026-08-19 추가)
- `POST /api/{buyer,seller}/mypage/notifications/read-all` — 본인의 안 읽은 알림 전체 읽음 처리(2026-08-19 추가)
- 상세 요청/응답/에러 스펙: `docs/api/mypage.md`의 "## 알림" 섹션이 원천.
- 컨트롤러는 기존 `BuyerMypageController`/`SellerMypageController`에 자연스럽게 추가돼 있다(신규 컨트롤러 분리 없음 — 마이페이지 하위 기능이라 계층상 더 일관적).
- 읽음 처리 2개는 역할과 무관하게 "이 알림이 진짜 이 회원 것인지"만 확인하면 되는 로직이라, 두 컨트롤러가 공통으로 위임할 수 있게 `NotificationService`(이 기능이 이미 갖고 있던 이벤트 소비 서비스)에 `markAsRead(principal, notificationId)`/`markAllAsRead(principal)`를 추가했다 — 역할 게이트(`requireBuyer`/`requireSeller`)는 그대로 `Buyer,SellerMypageService`가 먼저 확인한 뒤 위임한다(소유권 검증과 역할 검증의 책임을 분리).

## 데이터 모델

- 신규 테이블 `notification`. 상세 컬럼/인덱스/관계: `docs/db/notification.md`가 원천.
- 하드 삭제 없음(알림 이력 보존) — 삭제 메서드 자체를 두지 않는다.

## 이벤트 소비

- `event/TeamRefundedEventListener`가 `TeamRefundedEvent`를 `@TransactionalEventListener(phase = AFTER_COMMIT)`로 구독해 `NotificationService.createTeamRefundedNotifications(event)`를 호출한다(이벤트 발행·발행 시점 보장은 `payment/portone`의 `PaymentRefundService` 책임 — 상세: `docs/dev/payment/portone/design.md`).
- `NotificationService.createTeamRefundedNotifications`는 그 팀의 환불된 결제 구매자 전원(중복 제거, `LinkedHashSet`) + 상품 판매자 각각에게 `Notification` 1건씩 생성한다. member/team은 FK로만 쓰이므로 `getReferenceById`로 불필요한 SELECT를 피한다.
- **`@Transactional(propagation = Propagation.REQUIRES_NEW)`**가 필수다 — 이 메서드는 원본 트랜잭션(`TeamDeadlineService.processDeadline`)의 커밋 직후 `AFTER_COMMIT` 콜백으로 호출되는데, 기본 propagation(REQUIRED)으로 두면 아직 스레드에 남아있는 원본 트랜잭션 리소스에 조인해버려 이 메서드의 INSERT가 실제로 커밋되지 않고 사라진다(스프링 공식 문서가 명시하는 캐비앗, 실제로 겪은 버그 — `docs/logs/notification/refund-alert/001-refund-alert.md` 참고). REQUIRES_NEW로 물리적으로 독립된 새 트랜잭션임을 보장해 해결.
- 메시지 문구(상수화): 구매자용 "참여하신 공구팀이 미성사되어 환불 처리되었습니다.", 판매자용 "등록하신 상품의 공구팀이 미성사되어 환불 처리되었습니다."

## 규칙 / 검증

- 조회는 본인 알림만 스코핑된다 — `BuyerMypageService`/`SellerMypageService`가 각각 `requireBuyer`/`requireSeller` 역할 체크(반대 역할 403 `FORBIDDEN`) 후 `principal.getMember().getId()` 기준으로만 `NotificationRepository.findAllByMemberIdOrderByCreatedAtDesc`를 호출한다. buyer/seller 둘 다 "내 memberId 기준" 조회라 별도 스코핑 리포지토리 메서드 없이 동일 쿼리를 재사용한다.
- 비로그인 401.
- 엔티티(`Notification`)를 컨트롤러 응답으로 직접 노출하지 않는다 — `dto/NotificationResponse`로 변환.
- 알림 타입은 `enum NotificationType`으로 관리(현재 `TEAM_REFUNDED` 하나) — 향후 알림 종류가 늘어나면 이 enum에 추가.
- **읽음 처리(2026-08-19)**: `Notification.markAsRead()`(도메인 메서드, `isRead=true`) 신규. 개별 읽음은 `notificationId`로 조회 후 소유권(`member.id` 일치) 검증, 아니면 `NOTIFICATION_NOT_FOUND`(404)/`FORBIDDEN`(403). 전체 읽음은 `@Modifying` 벌크 UPDATE(`WHERE member_id=:id AND is_read=false`)로 한 번에 처리 — 안 읽은 것만 골라서 건드리므로 이미 읽은 알림까지 매번 다시 쓰지 않는다.
  - `Buyer,SellerMypageService`는 클래스 기본이 `@Transactional(readOnly=true)`라, 새로 추가한 이 두 메서드는 명시적으로 `@Transactional`(쓰기)로 덮어써야 한다 — 안 그러면 같은 읽기전용 트랜잭션에 합류해서 실제 쓰기가 무시되거나 예외가 난다(실제로 겪음, 아래 로그 참고).
  - 벌크 UPDATE는 `@Modifying(clearAutomatically = true)`가 필수다 — 영속성 컨텍스트(1차 캐시)를 거치지 않고 DB를 직접 바꾸는 쿼리라, 안 비우면 같은 트랜잭션 안에서 그 전에 이미 로드된 `Notification` 엔티티를 다시 조회할 때 옛 `isRead` 값이 캐시된 채로 보인다(테스트로 직접 재현 후 발견).

## 관련 코드 위치

- `entity/Notification.java`(`markAsRead()` 추가), `entity/NotificationType.java`
- `repository/NotificationRepository.java` — `findAllByMemberIdOrderByCreatedAtDesc(memberId)`, `markAllAsReadByMemberId(memberId)`(신규)
- `dto/NotificationResponse.java`
- `service/NotificationService.java` — `createTeamRefundedNotifications(TeamRefundedEvent)`, `markAsRead(principal, notificationId)`/`markAllAsRead(principal)`(신규)
- `service/BuyerMypageService.java`, `service/SellerMypageService.java` — `notifications(principal)`, `markNotificationAsRead`/`markAllNotificationsAsRead`(신규, 각각 역할 게이트 후 `NotificationService`에 위임)
- `controller/BuyerMypageController.java`, `controller/SellerMypageController.java` — `GET .../notifications`, `POST .../notifications/{id}/read`·`POST .../notifications/read-all`(신규)
- `common/exception/ErrorCode.java` — `NOTIFICATION_NOT_FOUND`(신규)
- 소비하는 이벤트: `event/TeamRefundedEvent.java`, `event/TeamRefundedEventListener.java` (발행 쪽은 `docs/dev/payment/portone/design.md`의 `PaymentRefundService` 참고)
- **프론트(신규, 2026-08-19)**: `partials/header.html`(`#header-notifications` 벨+뱃지+드롭다운 패널), `js/header-notifications.js`, `css/layout.css`(`.site-header__notifications*`), `css/components.css`(`.header-notifications-panel*`). 헤더가 삽입되는 17개 페이지 전부에 `<script src="/js/header-notifications.js">`를 `header-search.js` 바로 뒤에 추가.
- 테스트:
  - `controller/BuyerMypageControllerTest.java`, `controller/SellerMypageControllerTest.java` — 조회 성공, 본인만 조회(스코핑), 반대 역할 403, 비로그인 401, **개별 읽음 성공/타인 알림 403/존재하지 않는 알림 404, 전체 읽음 성공(신규, 각 파일에 4케이스씩 추가)**
  - `NotificationService` 자체의 별도 단위 테스트는 없음 — 이벤트 발행 없이는 단독 호출되지 않는 메서드라(오케스트레이션이 전부 이벤트 경유) `event/TeamDeadlineEventFlowTest.java`에서 end-to-end로 검증한다. 읽음 처리 메서드는 컨트롤러 테스트로 커버.
