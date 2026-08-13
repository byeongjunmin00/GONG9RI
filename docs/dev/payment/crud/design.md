# 결제 생성/조회 (payment/crud) — Design

## 개요

구매자(BUYER)가 상품을 혼자구매하거나 공구팀 참여 건에 대해 결제를 기록한다. `teamId`가 없으면 혼자구매(정가 `basePrice`), 있으면 해당 팀의 `max_participants`(목표 인원, 정원 — 팀 생성 시점에 고정된 스냅샷) 구간에 맞는 `price_tier` 가격이 적용된다. 이 기준값은 결제 시점의 실시간 참여 인원(`current_count`)이 아니라 팀 생애 동안 바뀌지 않는 정원이라, **같은 팀에 속한 모든 결제자는 먼저 결제하든 나중에 결제하든 항상 동일한 금액**을 낸다. **결제는 참가(정원 체크·`current_count` 증가·`TeamParticipation` 저장)를 다시 수행하지 않는다** — 그 로직은 이미 `team/crud`(`TeamService.create`/`join`)에서 완결된다. 결제는 그 결과 위에 순수 금액 기록만 남긴다.

## API / 인터페이스

- `POST /api/payments`, `GET /api/payments/{paymentId}` — 상세: `docs/api/payment.md`

## 데이터 모델

- `payment` — 상세: `docs/db/payment.md`
- **PortOne 연동 이후(`docs/dev/payment/portone/design.md`가 이 기능의 확정/웹훅/환불 흐름에 대한
  SSOT)**: `status`는 생성 시 `PENDING`(승인 대기)로 시작한다 — 이 문서(payment/crud)가 다루는 범위는
  "요청 접수 시점의 금액 계산·검증"까지이고, `PENDING → PAID/FAILED` 확정, `PAID → REFUND_PENDING/REFUNDED`
  환불 전이는 `payment/portone`이 담당한다.

## 규칙 / 검증

- 결제 생성은 `Role.BUYER`만 가능(판매자 시도 시 `403 FORBIDDEN`) — `docs/api/payment.md` 계약
- 결제 상세 조회는 본인 결제만 가능(타인 결제 조회 시 `403 FORBIDDEN`)
- **금액 계산**:
  - `teamId`가 없으면 `product.basePrice`
  - `teamId`가 있으면 해당 팀의 `max_participants`(목표 인원) 기준으로 `price_tier`를 `minCount` 오름차순 순회하며, `maxParticipants >= minCount`를 만족하는 마지막(가장 큰) 구간의 가격을 적용. 만족하는 구간이 없으면 `basePrice` 유지. `max_participants`는 팀 생성 시점에 `product.maxParticipants`에서 복제된 값으로 팀 생애 동안 불변이라, 결제 시점(`currentCount`)과 무관하게 한 팀 안에서는 항상 같은 가격이 나온다
- **`TEAM_FULL`(409) 판정**: 참가 로직 자체는 재수행하지 않지만, 아직 해당 팀 참가자가 아닌 멤버가 이미 정원이 찬 팀으로 결제를 시도하는 경우만 방어적으로 막는다(`TeamParticipationRepository.existsByTeamIdAndMemberId`로 확인). 이미 참가한 멤버는 팀이 정원 도달(`SUCCESS`) 상태여도 결제 가능
- 존재하지 않는 `productId`/`teamId`는 각각 `404 PRODUCT_NOT_FOUND`/`404 TEAM_NOT_FOUND`
- `SecurityConfig` 변경 없음 — `/api/payments/**`는 두 엔드포인트 모두 인증 필요라 기존 `anyRequest().authenticated()`로 충분

## 관련 코드 위치

- `entity/{Payment,PaymentStatus}.java`
- `dto/{PaymentCreateRequest,PaymentResponse}.java`
- `repository/PaymentRepository.java` — `findByIdWithDetails`가 상세 조회 시 member/product/team fetch join(N+1 방지)
- `service/PaymentService.java`
- `controller/PaymentController.java`
- `common/exception/ErrorCode.java` — `PAYMENT_NOT_FOUND` 추가
- 테스트: `controller/PaymentControllerTest.java` — 혼자구매/팀구매 tier 가격, 정원 방어 체크, 각종 에러 케이스(이 기능 범위). 같은 파일에 `confirm` 관련 시나리오도 있는데 그건 `payment/portone` 범위 — `docs/dev/payment/portone/design.md` 참고
