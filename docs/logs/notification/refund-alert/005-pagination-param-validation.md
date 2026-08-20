# 005-pagination-param-validation — 알림 페이지네이션 page/size 값 검증 (로그)

## Attempt 1 — 2026-08-20  ✅ PASS
- 시도: `BuyerMypageService`/`SellerMypageService`의 `notifications(principal, page, size)`에
  `validatePageRequest(page, size)`를 추가 — `page<0` 또는 `size<1`이면
  `BusinessException(ErrorCode.VALIDATION_FAILED)`를 던지도록 함(기존 `MemberService`,
  `PortOneWebhookService`와 동일 패턴, `GlobalExceptionHandler`가 400으로 매핑).
  `BuyerMypageControllerTest`/`SellerMypageControllerTest`에 `page=-1`/`size=0` 케이스 추가.
- 결과: `./gradlew test` 전체 통과(385케이스, 실패/에러 0).
- 증거(API 샘플):
  - `GET /api/buyer/mypage/notifications?page=-1` → `400 {"code":"VALIDATION_FAILED", ...}`
  - `GET /api/buyer/mypage/notifications?size=0` → `400 {"code":"VALIDATION_FAILED", ...}`
  - `GET /api/seller/mypage/notifications?page=-1`/`?size=0` → 동일하게 400 확인.
  - 회귀 확인: `BuyerMypageControllerTest`(23케이스), `SellerMypageControllerTest`(23케이스) 전부 통과.
