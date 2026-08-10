# 환불 트리거 → 이벤트 기반 메시징 확장

대상: team/deadline-check(확장) + notification/refund-alert(신규)
담당: 전용운

## 배경 / 요구

현재 `team/deadline-check`는 스케줄러(`TeamDeadlineScheduler`)가 마감 지난 팀을 감지하면 `TeamDeadlineService.processDeadline()`을 직접 호출해서 상태전환(FAILED)+환불 처리를 한 흐름으로 처리한다(`docs/dev/team/deadline-check/design.md`, `docs/policy/refund-trigger.md`). 이걸 "감지"와 "상태전환+환불 처리"를 이벤트로 분리하고, 환불이 끝나면 구매자/판매자에게 알림을 남기는 컨슈머를 추가해 실제 비동기 이벤트 메시징 구조로 확장한다.

## 설계

- **기술**: 스프링 애플리케이션 이벤트(인프로세스 발행-구독). 새 인프라(메시지 브로커) 도입 없음.
- **이벤트 흐름**:
  1. `TeamDeadlineScheduler`는 마감 지난 팀 id를 스캔해 팀 id별로 "마감 감지" 이벤트만 발행한다 — 처리 로직을 직접 호출하지 않는다.
  2. 별도 구독자가 이 이벤트를 받아 기존 상태전환(FAILED)+환불 처리를 수행한다(기존 락·재검증 전략은 그대로 재사용, 바꾸지 않음).
  3. 환불 트랜잭션이 **커밋된 이후에만** "환불 완료" 이벤트가 나가야 한다 — 커밋 전/롤백 시 알림이 남으면 정합성이 깨진다(리스크로 명시, 해결 방식은 Generate가 정함).
  4. "환불 완료" 이벤트를 구독하는 알림 처리기가 그 팀에서 환불된 결제들의 구매자 전원 + 상품 판매자에게 알림 레코드를 각각 생성한다.
  5. 알림은 이메일/SMS 없이 **DB 저장 + 마이페이지 조회**까지만 구현한다(이번 스코프).
- **비동기성**: 이벤트 구독·처리가 스케줄러의 스캔 루프를 막지 않아야 한다 — 구체 수단(스레드풀 등)은 Generate가 정한다.

## API/DB 계약 (초안 — 상세는 아래 문서가 원천)

- DB: `docs/db/notification.md` (신규)
- API: `docs/api/mypage.md`에 `GET /api/buyer/mypage/notifications`, `GET /api/seller/mypage/notifications` 추가

## 태스크

- [ ] 마감 감지 이벤트 클래스 + 스케줄러에서 발행하도록 변경(직접 호출 제거)
- [ ] 마감 감지 이벤트 구독자 — 기존 상태전환+환불 로직 수행(락·재검증 로직 유지)
- [ ] 환불 완료 이벤트 클래스 + 커밋 후 발행
- [ ] 환불 완료 이벤트 구독자 — 구매자 전원+판매자 `Notification` 레코드 생성
- [ ] `Notification` entity/repository/dto
- [ ] `GET .../mypage/notifications` 엔드포인트(구매자/판매자)
- [ ] 기존 `TeamDeadlineServiceTest` 회귀 확인 + 신규 이벤트/알림 테스트 작성

## 평가(통과) 기준

- 기존 `TeamDeadlineServiceTest` 5케이스가 이벤트 경유로 바뀐 뒤에도 회귀 없이 통과.
- 신규: (a) 이벤트 발행 시 상태전환+환불 수행 검증, (b) 환불 트랜잭션 롤백 시 알림 미생성 검증, (c) 커밋 성공 시 구매자 전원+판매자 알림 생성 검증.
- `GET .../mypage/notifications` 스코핑 테스트(본인만 조회, 반대 역할 403).
- `./gradlew test` 전체 통과.

## 리스크 / 전제

- 인프로세스 이벤트라 서버 재시작 중 처리되지 못한 이벤트는 사라질 수 있다 — 알림 발송 여부에만 영향이 있고, 환불 자체는 이미 트랜잭션으로 보장돼 마이페이지에서 항상 사실 확인이 가능하다(데이터 정합성 문제는 아님).
- 실제 이메일/SMS 발송 채널은 이번 스코프에 없다.
- 로컬 개발 시 MySQL 가동 필요(기존과 동일, 추가 인프라 없음).
