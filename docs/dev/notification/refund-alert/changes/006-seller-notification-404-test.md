# 판매자 알림 읽음 처리 404 테스트 케이스 누락 보강

대상: notification/refund-alert
담당: 전용운

## 배경 / 요구

코드리뷰(2026-08-20, 병합된 15개 커밋 리뷰)에서 발견: `design.md`는 "개별 읽음 성공/타인 알림
403/존재하지 않는 알림 404, 전체 읽음 성공(각 파일에 4케이스씩 추가)"라고 적혀있는데, 실제로는
`BuyerMypageControllerTest`만 4케이스(404 포함)였고 `SellerMypageControllerTest`는 3케이스뿐이었다
(404 케이스 없음). 지금 당장 버그는 아니다 — 판매자 경로도 결국 같은 `NotificationService.markAsRead`
를 타서 404가 정상적으로 나지만, 이 경로만 회귀해도 잡아낼 테스트가 없는 커버리지 공백이었다.

## 설계

`BuyerMypageControllerTest.markNotificationAsRead_notFound`와 완전히 같은 패턴으로
`SellerMypageControllerTest`에 동일 케이스를 추가한다.

## 태스크

- [x] `SellerMypageControllerTest`에 `markNotificationAsRead_notFound` 케이스 추가

## 평가(통과) 기준

- 신규 케이스 통과
- 기존 `BuyerMypageControllerTest`/`SellerMypageControllerTest` 전체 회귀 없음

## 실행 결과

계획대로 추가했다. `./gradlew test --tests "*SellerMypageControllerTest*" --tests
"*BuyerMypageControllerTest*"` 통과(둘 다 `BUILD SUCCESSFUL`, 회귀 없음).
