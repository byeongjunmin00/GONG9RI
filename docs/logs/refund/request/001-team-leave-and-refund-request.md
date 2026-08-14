# 001-team-leave-and-refund-request — 공구팀 참여 취소 + 환불 요청/승인 (로그)

## Attempt 1 — 2026-08-14  ⏳ 진행 중
- 시도: 승인된 계획(`docs/dev/ongoing/team-leave-and-refund-request.md`)대로 Generate 서브에이전트가
  구현 진행 중. 계획 승인 후 사용자 요구사항 하나 추가(팀 성사 후 환불 불가 안내를 체크박스로
  명시적 확인받기)를 진행 중인 에이전트에 전달해 반영 지시함.
- 현재 상태(중간 확인): `TeamService.java`, `TeamParticipationRepositoryCustom/Impl.java` 수정,
  `dto/TeamParticipantResponse.java` 신규 — 참여 취소(team/leave) 쪽 작업이 먼저 진행되는 것으로
  보임. 아직 완료 보고 전이라 테스트 결과·전체 diff는 이번 Attempt 후속 기록에서 이어서 남긴다.
- 다음: 에이전트 완료 후 전체 diff·테스트 결과·Evaluate 결과를 이 로그에 이어서(append) 기록한다.

## Attempt 1 이어서 — 재인수 후 실제 구현 (2026-08-14)

**중요한 정정**: 위 "중간 확인" 기록은 오판이었다. 그 diff(`TeamService.participants()`,
`TeamParticipantResponse`, `GET /api/teams/{teamId}/participants`)는 이 기능(team/leave +
refund/request)이 아니라 **완전히 별개의, 이미 완료된 기능**(`docs/dev/ongoing/
team-participants-list.md`, "공구팀 참여자 목록 표시" — `docs/logs/team/crud/008-participants-list.md`
에 이미 PASS로 기록됨)이었다. 재인수 시점에 `git diff`/`git status`로 직접 확인해 이 사실을 밝혀냈다
— team-leave-and-refund-request 쪽은 이번 Attempt 시작 시점까지 **전혀 손대지 않은 상태**였다. 그
기존 diff는 그대로 두고(다른 기능의 완성된 작업, 되돌리지 않음) 이번 기능은 처음부터 구현했다.

- 시도: 계획 문서 전체 태스크를 아래처럼 구현했다.

  **A. 공구팀 참여 취소(team/leave)**
  - `entity/GroupBuyTeam.java`: `decreaseParticipant()`(join의 `increaseParticipant()`와 대칭, 0이
    되면 `FAILED`로 전환) + `changeLeader(Member)` 추가.
  - `service/TeamService.leave(principal, teamId)`: `findByIdForUpdate`로 join()과 동일한 비관적
    락 재사용 → 참여자 확인(`FORBIDDEN`) → `RECRUITING` 확인(`TEAM_NOT_RECRUITING`) → 참여 기록
    삭제(`deleteByTeamIdAndMemberId`, 신규) → 정원 감소 → 리더였으면 다음 최초 참가자
    (`findFirstByTeamIdOrderByJoinedAtAsc`, 신규)에게 승계 → 그 팀에 대한 `PAID` 결제가 있으면
    `RefundRequestService.createFromTeamLeave()` 호출 → 기존 `TeamCapacityChangedEvent` 재사용해
    실시간 브로드캐스트(join과 동일 경로, 신규 이벤트 불필요).
  - `controller/TeamController.leave()` — `POST /api/teams/{teamId}/leave`.
  - `TeamParticipation` 테이블의 기존 "하드 삭제 없음" 정책을 참여 취소에 한해 갱신
    (`docs/db/team_participation.md`) — 계획 문서가 "참여 기록 제거"를 명시했고, 자리 즉시 반환이
    핵심이라 실제 삭제가 맞다고 판단. 결제/환불 이력은 `payment`/`refund_request`가 별도로 보존한다.

  **B. 환불 요청/승인(refund/request, 신규 개념)**
  - `entity/{RefundRequest,RefundRequestStatus,RefundRejectionReason}.java` 신규 — 거절 사유는
    자유 텍스트가 아니라 4개 템플릿(`ALREADY_SHIPPED`/`ALREADY_USED`/`POLICY_VIOLATION`/`OTHER`) enum.
  - `repository/RefundRequestRepository(+Custom/Impl).java` 신규(QueryDSL) — 실측 이슈: 3단계 경로
    (`refundRequest.payment.product.seller`)를 그대로 체이닝하면 `NullPointerException`(QueryDSL
    기본 `PathInits.DIRECT2`가 root로부터 2단계까지만 자동 초기화 — 관련 Q클래스 확인 후 원인 특정).
    별도 alias(`new QProduct(...)`)로 우회, seller 조회가 필요 없는 곳(단건 소유권 확인)은 fetch join
    자체를 빼고 지연로딩 1회로 대체(단건이라 N+1 아님).
  - `service/RefundRequestService.java` 신규 — `createDirect`(솔로 구매 직접 요청, 팀 결제면
    `TEAM_PAYMENT_REFUND_NOT_ALLOWED` 거절), `createFromTeamLeave`(팀 leave가 같은 트랜잭션에서
    호출, 자동환불 설정이면 즉시 `approve()`+이벤트 발행), `approve`/`reject`(판매자, 소유권 확인).
  - **기존 취소 실행 경로 재사용**(지시받은 핵심 제약): `service/PaymentCancellationExecutor.java`
    신규 — 원래 `TeamPaymentsRefundRequestedEventListener` 안에 있던 "취소 대상 조회 → PortOne 호출
    → 결과 반영" 로직을 추출해 공유 컴포넌트로 분리(PortOne 취소 API를 호출하는 코드를 새로 작성하지
    않기 위해 — 지시사항). 기존 리스너는 이 실행기에 위임하도록 리팩터링(동작 동일, 사유 문구만
    파라미터화). 새 `event/{RefundRequestApprovedEvent,RefundRequestApprovedEventListener}.java`가
    같은 실행기를 재사용 — `@Async` + `AFTER_COMMIT`으로 판매자 승인/자동환불 트랜잭션(특히
    `TeamService.leave`의 비관적 락)이 커밋된 뒤에만 PortOne을 호출한다.
  - `controller/RefundRequestController.java` 신규 — `POST /api/payments/{id}/refund-requests`(직접
    요청), `POST /api/refund-requests/{id}/approve`, `.../reject`.
  - `BuyerMypageService/Controller`, `SellerMypageService/Controller`에 `GET .../refund-requests`
    추가(기존 알림 조회와 동일한 패턴 — 조회만, 액션은 위 컨트롤러가 전담).

  **C. 상품별 자동환불 설정**
  - `entity/Product.java`: `autoRefundOnCancel`(primitive boolean, `@ColumnDefault("false")` —
    `Member.emailVerified`와 동일한 "기존 row 있는 테이블에 NOT NULL 컬럼 추가" 안전 패턴) 필드 +
    7-arg 생성자 오버로드(6-arg는 그대로 유지해 20개 테스트 파일의 기존 호출을 안 건드림) +
    `update()` 시그니처 변경(호출부가 `ProductService` 하나뿐이라 안전).
  - `ProductRegisterRequest`/`ProductResponse`에 필드 추가, `ProductService.register/update`에서
    전달.

  **D. 구매자 환불 불가 명시적 확인(체크박스)**
  - `product.html`에 `#refund-notice-checkbox` 추가(가격 구간 표 아래, product-actions 위).
  - `product.js`: `updateCreateTeamButtonState()`/`updateJoinButtonsState()` 추가 — 체크 전까지
    "신규 팀 신설하기"와 각 팀의 "참가하기"(`.team-item-join-btn` 클래스로 조회) 버튼이 비활성.
    "혼자 구매하기"는 이 게이트와 무관(솔로 구매는 환불 불가 규칙 대상이 아님).

  **테스트**(총 257개 전체 스위트, 신규/변경 약 48개):
  - `TeamControllerTest`: leave 성공(RECRUITING, 정원 즉시 반환), 자리 재참가 가능, 리더 승계,
    마지막 참여자 취소 시 FAILED, TEAM_NOT_RECRUITING(409), 비참여자 FORBIDDEN(403), TEAM_NOT_FOUND
    (404), UNAUTHORIZED(401), PAID 결제 있으면 PENDING 환불요청 자동생성, 결제 없으면 생성 안 됨,
    자동환불 설정 켜져 있으면 즉시 APPROVED — 총 11케이스 신규.
  - `RefundRequestControllerTest`(신규 20케이스): 직접 요청 성공/팀결제거절/본인아님/PAID아님/
    중복대기중/사유누락/결제없음/판매자거절/비로그인, 승인 성공/본인아님/없음/이미처리/구매자거절/
    비로그인, 거절 성공(사유 템플릿 설명 반영+결제 PAID 유지)/사유누락/본인아님/이미처리.
  - `BuyerMypageControllerTest`/`SellerMypageControllerTest`: refund-requests 목록 성공/스코핑/
    반대역할403/비로그인401 각 4케이스 추가.
  - `PaymentCancellationExecutorTest`(신규): 대상 있음/대상없음(스킵)/PortOne 실패 격리 — 원래
    `TeamPaymentsRefundRequestedEventListenerTest`에 있던 검증을 추출된 실행기로 이전.
  - `TeamPaymentsRefundRequestedEventListenerTest`: 실행기 위임 라우팅만 검증하도록 축소(리팩터링
    반영).
  - `event/RefundRequestApprovedEventFlowTest`(신규, `TeamDeadlineEventFlowTest`와 동일 패턴 —
    클래스레벨 `@Transactional` 미사용, 실제 커밋 필요): (1) 판매자 승인 → AFTER_COMMIT → PortOne
    취소(목, SUCCEEDED) → 결제 REFUNDED 확인. (2) 상품 `autoRefundOnCancel=true`로 참여 취소 →
    승인 절차 없이 같은 경로로 REFUNDED까지 확인(환불 알림까지 대기 후 정리 — 팀 결제라
    `TeamRefundedEvent`가 함께 발행되므로 `notification.related_team_id` FK 때문에 알림도 함께
    정리해야 함을 실측으로 확인, MySQL 테스트 DB에 leftover 발견 후 직접 SQL로 정리).
  - `docs/db/product.md`/`docs/db/team_participation.md`/`docs/db/refund_request.md`(신규),
    `docs/api/{team,mypage,product,refund(신규)}.md` 갱신.

- 결과:
  - `./gradlew compileJava`/`compileTestJava` 모두 성공.
  - `./gradlew test`(전체 스위트): **257개 전체 통과, 실패 0 / 에러 0** (`BUILD SUCCESSFUL`).
  - 로컬 MySQL(Windows 서비스)·Redis(Docker) 둘 다 가동 중이었음 — 실제 DB로 전체 스위트 실행,
    별도 미가동 이슈 없음.
- 증거(API 계약 기준, MockMvc):
  - `POST /api/teams/{teamId}/leave`(리더 취소, 3인 팀에서 2번째 참가자였던 사람) →
    `200 {"data":{"teamId":..,"currentCount":1,"maxParticipants":3,"status":"RECRUITING"}}`.
  - `POST /api/payments/{id}/refund-requests`(솔로 구매, 사유 "단순 변심") →
    `201 {"data":{"status":"PENDING","reason":"단순 변심","teamId":null,...}}`.
  - 팀 결제로 같은 API 호출 → `409 {"code":"TEAM_PAYMENT_REFUND_NOT_ALLOWED"}`.
  - `POST /api/refund-requests/{id}/approve`(판매자) → `200 {"data":{"status":"APPROVED",...}}`,
    이후 커밋+비동기 PortOne(목) 경유해 `payment.status`가 `REFUNDED`로 전환 확인(flow 테스트).
  - `POST /api/refund-requests/{id}/reject`(사유 `ALREADY_SHIPPED`) →
    `200 {"data":{"status":"REJECTED","rejectionReason":"상품이 이미 발송되어 환불이 어렵습니다."}}`,
    결제는 `PAID` 유지 확인.
- 리스크/전제 재확인: 팀 결제의 환불은 참여 취소(RECRUITING일 때만 가능) 경로 하나로만 열려 있고,
  직접 환불 요청 API는 팀 결제를 코드 레벨에서 무조건 거절한다(테스트로 확인) — "정원 채워 SUCCESS
  전환 이후 어떤 경로로도 환불 불가" 제약을 코드가 실제로 지키는지는 이 조합(leave의 RECRUITING
  가드 + createDirect의 팀결제 거절)으로 보장된다.
- 다음: Evaluate 단계에서 `./gradlew test` 재확인, 계획 대비 스코프 준수 검토, 통과 시
  `docs/dev/team/crud/design.md`(leave 반영)·`payment/portone`(취소 실행 재사용 반영) 등 design.md
  갱신 + `docs/dev/ongoing/team-leave-and-refund-request.md` → `changes/`로 채번 이동은 Evaluate 몫.

## Evaluate — 2026-08-14  ✅ PASS

- 결과:
  - `./gradlew test --rerun-tasks`(캐시 우회, 전체 스위트): **BUILD SUCCESSFUL**, 테스트 결과 XML
    합산 총 257개, 실패 0 / 에러 0 / 스킵 0 — generator 보고와 정확히 일치. 로컬 MySQL(3306,
    Windows 서비스, 포트 연결 확인)·Redis(Docker `gong9ri-main-redis-1`, healthy) 둘 다 실제 가동
    상태에서 실행, DB 미가동으로 인한 오탐 없음.
  - `git diff --stat`(unstaged) 전체 확인: 이번 세션 시작 시점에 harness가 보여준 스냅숏에는
    `TeamController`/`TeamParticipationRepositoryCustom·Impl`/`TeamParticipantResponse`(참여자 목록
    표시 기능)·`SecurityConfig`도 modified/untracked로 잡혀 있었으나, 실제로는 그 사이 이미
    `c8b089d feat(team/crud): 공구팀 참여자 목록 표시 기능 추가`로 커밋+완료(changes/003 채번,
    logs/team/crud/008 PASS 기록)까지 끝나 있었다(`git log`로 확인) — 현재 작업 트리 diff에는 섞여
    있지 않다. 같은 이유로 `a83f6ff feat(auth/mypage): 계정 정보수정` 등 다른 무관 커밋도 이미 분리돼
    있다. 결론: **현재 unstaged diff(38개 수정 + 19개 신규 파일)는 이번 기능(team/leave,
    refund/request) 범위에만 해당** — 분리해야 할 섞인 변경 없음.
  - 계획 문서(`docs/dev/ongoing/team-leave-and-refund-request.md`) 태스크 13개 전부 diff에서
    확인됨(A~D 전 항목 커버). `PurchaseResponse.teamId` 추가, `CacheConfigTest`/`ProductCachingTest`의
    기존 호출부 시그니처 갱신은 `Product.autoRefundOnCancel` 필드 추가에 따른 기계적 후속 수정으로,
    범위 이탈 아님.
- **가장 중요한 제약(팀 SUCCESS 전환 후 어떤 경로로도 환불 불가) 코드 추적 결과 — 위반 없음**:
  - (a) `TeamService.leave()`(`src/main/java/com/gong9ri/gong9ri/service/TeamService.java:168-207`):
    `team.getStatus() != TeamStatus.RECRUITING`이면 `TEAM_NOT_RECRUITING`(409)으로 무조건 거절.
    RECRUITING 확인 전에 참여자 여부부터 확인(`FORBIDDEN`)하지만 순서와 무관하게 RECRUITING 가드
    자체는 빠짐없이 실행됨. 테스트로도 확인(`leave_teamNotRecruiting_conflict`, SUCCESS 팀 대상).
  - (b) `RefundRequestService.createDirect()`(`RefundRequestService.java:48-72`): `payment.getTeam()
    != null`이면 `TEAM_PAYMENT_REFUND_NOT_ALLOWED`(409)로 무조건 거절 — 소유권 확인 이후, 상태·중복
    확인 이전에 위치해 팀 결제는 다른 조건과 무관하게 항상 막힘. 테스트로 확인
    (`RefundRequestControllerTest` "팀이 딸린 결제는 직접 환불 요청 시 409").
  - (c) 우회 경로 점검:
    - 판매자 승인/거절 API(`approve`/`reject`)는 `payment.team`을 직접 검사하지 않고 "이미 존재하는
      PENDING `RefundRequest`"만 처리한다 — 안전한 이유는 **팀 결제에 대한 PENDING `RefundRequest`가
      생성되는 유일한 경로가 `RefundRequestService.createFromTeamLeave()`뿐이고, 그 메서드는
      `grep`으로 확인한 결과 `TeamService.leave()` 한 곳에서만 호출**되기 때문(팀 결제는 (b)로 항상
      막히므로 `createDirect()`를 거쳐 PENDING이 생성될 수 없음). 즉 SUCCESS 팀에 대해 승인 API를
      호출해도 애초에 승인할 PENDING 요청 자체가 존재할 수 없다(RefundRequest 자체가 없으면
      `REFUND_REQUEST_NOT_FOUND`). 코드가 "이중 방어"(승인 시점에도 팀 상태 재확인)를 하고 있지는
      않지만, 생성 경로가 봉쇄돼 있어 실질적으로 도달 불가능한 상태 — 논리적으로 제약이 성립함을
      확인.
    - 참여 취소로 PENDING 환불요청이 생성된 뒤(RECRUITING 시점), 그 사람이 나간 자리를 다른 사람이
      채워 팀이 나중에 SUCCESS로 전환되고, 그 이후 판매자가 그 PENDING 요청을 승인하는 시나리오는
      실행 가능하다. 이는 위반이 아니라고 판단함 — 이미 `leave()`가 그 사람의 참여를 제거하고
      `currentCount`를 감소시켰으므로, 그 사람은 SUCCESS로 전환된 팀의 "현재 참여자"가 아니다(악용
      시나리오인 "계정 2개로 결제 후 하나만 환불해 인원수 대비 저가 구간을 유지"가 성립하지 않음 —
      나간 사람은 더 이상 인원수에 포함되지 않는다). 계획 문서의 "리스크/전제" 항목이 다루는 것은
      "아직 결제를 안 끝낸 마지막 참가자가 뒤늦게 결제 완료" 케이스이고, 이 케이스와는 별개다.
  - 결론: **세 가지 확인 항목 모두 통과, 제약 위반 경로를 발견하지 못했다.**
- **PortOne 취소 로직 재사용 확인**: `PaymentCancellationExecutor.cancelOne()`
  (`findCancelTarget` → `PortOneClient.cancelPayment` → `applyCancelResult`)가 유일한 PortOne 취소
  호출 지점이고, `TeamPaymentsRefundRequestedEventListener`(기존, 공구팀 미성사)와
  `RefundRequestApprovedEventListener`(신규, 판매자 승인/자동환불) 둘 다 이 실행기에 위임만 한다 —
  PortOne 호출 코드 중복 없음. `TeamPaymentsRefundRequestedEventListenerTest`가 축소돼 라우팅만
  검증하도록 리팩터링된 것도 diff로 확인.
- **코드 컨벤션**: 계층 분리(컨트롤러는 위임만, 서비스에 트랜잭션·비즈니스 로직), 생성자 주입,
  `@Transactional(readOnly = true)` 기본 + 쓰기 메서드만 `@Transactional`, 로깅(SLF4J, 도메인
  식별자 포함), 409/403/404/401 상태코드 사용 모두 `docs/code-convention.md` 부합. `Product`에
  `NOT NULL` 컬럼 추가 시 `@ColumnDefault`로 안전 마이그레이션한 패턴도 기존 `Member.emailVerified`
  사례와 일관.
- **프론트 가드(C) 확인**: `product.js`/`product.html`에 `#refund-notice-checkbox` 추가, 체크
  전까지 "신규 팀 신설하기"·각 팀 "참가하기" 버튼이 비활성 — diff로 확인(계획 승인 후 추가된
  요구사항, 로그 Attempt 1 기록과 일치).
- 문서 정합성: `docs/api/refund.md`, `docs/db/refund_request.md`, `docs/api/team.md`(leave 추가),
  `docs/db/team_participation.md`(참여 취소 하드 삭제 예외), `docs/db/product.md`
  (`auto_refund_on_cancel`) 모두 구현과 일치.
- 통과 처리: `docs/dev/team/crud/design.md`에 참여 취소(leave) 반영, `docs/dev/payment/portone/design.md`
  에 취소 실행 로직 추출/재사용 반영, 신규 `docs/dev/refund/request/design.md` 생성,
  `docs/dev/ongoing/team-leave-and-refund-request.md` → `docs/dev/refund/request/changes/001-team-leave-and-refund-request.md`로 채번 이동.
