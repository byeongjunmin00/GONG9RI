# 결제 생성/조회 (payment/crud) — Design

## 개요

구매자(BUYER)가 상품을 혼자구매하거나 공구팀 참여 건에 대해 결제를 기록한다. `teamId`가 없으면 혼자구매(정가 `basePrice`), 있으면 해당 팀의 `current_count` 구간에 맞는 `price_tier` 가격이 적용된다. **결제는 참가(정원 체크·`current_count` 증가·`TeamParticipation` 저장)를 다시 수행하지 않는다** — 그 로직은 이미 `team/crud`(`TeamService.create`/`join`)에서 완결된다. 결제는 그 결과 위에 순수 금액 기록만 남긴다.

## API / 인터페이스

- `POST /api/payments`, `GET /api/payments/{paymentId}` — 상세: `docs/api/payment.md`

## 데이터 모델

- `payment` — 상세: `docs/db/payment.md`
- `status`는 생성 시 `PAID` 고정. `REFUNDED` 전이는 이번 스코프 밖(`payment/refund` + `team/deadline-check` 스케줄러에서 처리 예정, `docs/policy/refund-trigger.md` 참고)

## 규칙 / 검증

- 결제 생성은 `Role.BUYER`만 가능(판매자 시도 시 `403 FORBIDDEN`) — `docs/api/payment.md` 계약
- 결제 상세 조회는 본인 결제만 가능(타인 결제 조회 시 `403 FORBIDDEN`)
- **금액 계산**:
  - `teamId`가 없으면 `product.basePrice`
  - `teamId`가 있으면 해당 팀의 `current_count` 시점 기준으로 `price_tier`를 `minCount` 오름차순 순회하며, `currentCount >= minCount`를 만족하는 마지막(가장 큰) 구간의 가격을 적용. 만족하는 구간이 없으면 `basePrice` 유지
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
- 테스트: `controller/PaymentControllerTest.java` (혼자구매/팀구매 tier 가격, 정원 방어 체크, 각종 에러 케이스 13개)
