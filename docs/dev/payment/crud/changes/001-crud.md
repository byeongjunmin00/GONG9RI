# 결제 생성/조회

대상: payment/crud
담당: 민병준

## 배경 / 요구

auth → product → team까지 구현 완료. 다음 순서인 결제(payment) 기능 구현. `docs/api/payment.md`, `docs/db/payment.md`는 이미 확정돼 있어 계약 변경 없이 그대로 구현한다.

- `POST /api/payments` — 결제 생성 (혼자구매 또는 공구팀 참가자의 결제 기록)
- `GET /api/payments/{paymentId}` — 결제 상세 조회

**확정 사항**: `TeamService.create()`/`join()`이 이미 정원 체크·락·`current_count` 증가·`TeamParticipation` 저장까지 참가를 완결한다. payment는 참가 로직을 다시 처리하지 않고 **순수 결제 기록만** 남긴다.

## 설계

- 계층: `entity/Payment`, `repository/PaymentRepository`, `service/PaymentService`, `controller/PaymentController`, `dto/PaymentCreateRequest`·`PaymentResponse` 신규. `ErrorCode`에 `PAYMENT_NOT_FOUND` 추가.
- 금액 계산: `teamId` null → `product.basePrice`. `teamId` 있음 → 해당 팀 `current_count` 기준 `price_tier` 구간 가격(기존 `PriceTierRepository.findByProductIdOrderByMinCountAsc` 재사용).
- `TEAM_FULL`(409): 요청 멤버가 해당 팀 참가자가 아니고, 팀이 이미 정원 초과 상태면 방어적으로 막는다(참가 로직 자체를 다시 수행하지 않음 — 이미 참가한 멤버는 통과).
- 권한: 구매자만 결제 생성 가능(판매자 `FORBIDDEN`). 상세 조회는 본인 결제만 가능(타인 결제 `FORBIDDEN`).
- `SecurityConfig` 변경 없음 — `/api/payments/**`는 기존 `anyRequest().authenticated()`로 이미 커버됨(두 엔드포인트 모두 인증 필요, 공개 GET 없음).

## 태스크
- [x] `Payment` 엔티티 + `PaymentStatus` enum
- [x] `PaymentRepository`
- [x] `ErrorCode.PAYMENT_NOT_FOUND` 추가
- [x] `PaymentCreateRequest`/`PaymentResponse` DTO
- [x] `PaymentService` (create/detail)
- [x] `PaymentController`
- [x] 테스트 (`PaymentControllerTest`)
- [x] `./gradlew build` 통과 확인

## 평가(통과) 기준

- 결제 생성: 혼자구매(basePrice) / 팀구매(tier 가격) 정상 케이스
- 에러: `VALIDATION_FAILED`(400), `PRODUCT_NOT_FOUND`(404), `TEAM_NOT_FOUND`(404), `TEAM_FULL`(409), `FORBIDDEN`(403, 판매자), `UNAUTHORIZED`(401)
- 상세 조회: 정상 / `PAYMENT_NOT_FOUND`(404) / `FORBIDDEN`(403, 타인 결제) / `UNAUTHORIZED`(401)
- `./gradlew build` 통과
