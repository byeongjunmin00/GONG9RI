# 코드리뷰(a6373d9) 후속 조치 — refund/request 쪽 3건

대상: refund/request (기존 기능 하드닝, 새 폴더 아님)
담당: 전용운

## 배경 / 요구

코드리뷰(`docs/logs/refund/request/002-code-review.md`)에서 남은 5건 중, 이 기능 소유 3건:
정책 문서 참조 누락(문서), `createFromTeamLeave` 중복 대기중 요청 방지 누락, `requireOwner` 중복
구현. 나머지 2건(마지막 참여자 탈퇴 금지, 마감 스윕 제외)은 각각 `team/crud`·`team/deadline-check`
소유라 그쪽 `changes/`에 별도 기록했다(`004-last-participant-cannot-leave.md`,
`003-exclude-pending-refund-requests.md`).

## 변경 내용

1. **정책 참조 + FAILED 케이스 분석 추가**(문서만) — `docs/policy/refund-trigger.md`를 참조하는
   "관련 정책·의존" 섹션 신규, 기존 SUCCESS 엣지케이스 분석과 대칭으로 FAILED(마감 스윕) 케이스
   분석 추가. 스테일해진 `findByIdWithPaymentAndProduct` 참조를 `findByIdForUpdate`로 수정(팀원의
   동시성 수정 `622e3d4`로 이미 교체됐던 걸 문서가 못 따라간 상태였음).
2. **`createFromTeamLeave` 중복 대기중 요청 방지** — `createDirect`와 동일한
   `existsByPayment_IdAndStatus(paymentId, PENDING)` 체크 추가. 단 예외 대신 로그+스킵(부수효과
   호출이라 예외 시 `leave()` 전체가 롤백돼버림).
3. **`requireOwner` 중복 제거** — `Payment.isOwnedBy(Long memberId)` 엔티티 메서드 신규,
   `RefundRequestService`/`PaymentService`의 `requireOwner`가 이를 호출하도록 통합.

## 태스크

- [x] `docs/dev/refund/request/design.md` — 정책 참조·FAILED 케이스·스테일 참조 수정
- [x] `RefundRequestService.createFromTeamLeave()` 중복 가드 추가
- [x] `TeamControllerTest.leave_rejoinThenLeaveAgain_doesNotDuplicatePendingRefundRequest` 신규
- [x] `Payment.isOwnedBy()` 추가, `RefundRequestService`/`PaymentService` 적용

## 평가(통과) 기준

- 재참가 후 재탈퇴해도 같은 결제에 대한 `RefundRequest`가 1건만 존재하고, 두 번째 `leave()` 호출 자체는 200 성공.
- 기존 owner-check 테스트(구매자 본인 확인, 판매자 본인 상품 확인) 회귀 없이 통과.
- `./gradlew test` 전체 통과(272/272, `docs/logs/refund/request/003-hardening.md` 참고).

완료: 2026-08-18. 상세 실행 증거는 `docs/logs/refund/request/003-hardening.md` 참고.
