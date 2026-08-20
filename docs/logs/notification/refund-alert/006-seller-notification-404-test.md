# 006-seller-notification-404-test — 판매자 알림 404 테스트 보강 (로그)

## Attempt 1 — 2026-08-20  ✅ PASS
- 시도: `SellerMypageControllerTest`에 `markNotificationAsRead_notFound`(존재하지 않는 알림 →
  404 `NOTIFICATION_NOT_FOUND`) 케이스를 `BuyerMypageControllerTest`와 동일 패턴으로 추가.
- 결과: `./gradlew test --tests "*SellerMypageControllerTest*" --tests "*BuyerMypageControllerTest*"`
  통과, 회귀 없음.
