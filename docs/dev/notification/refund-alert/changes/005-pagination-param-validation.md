# 알림 페이지네이션 page/size 값 검증

대상: notification/refund-alert
담당: 전용운

## 배경 / 요구

코드리뷰(2026-08-20, 병합된 15개 커밋 리뷰)에서 발견: `BuyerMypageService`/`SellerMypageService`의
`notifications(principal, page, size)`가 컨트롤러에서 받은 `page`/`size`를 검증 없이 그대로
`PageRequest.of(page, size)`에 넘긴다. `page<0` 또는 `size<1`이면 `PageRequest.of`가
`IllegalArgumentException`을 던지는데, `GlobalExceptionHandler`엔 이 예외를 잡는 핸들러가 없어서
catch-all(`Exception.class`)로 떨어져 클라이언트에 400이 아니라 500(`INTERNAL_SERVER_ERROR`)이
나간다.

## 설계

`page`/`size`가 유효 범위를 벗어나면 이 프로젝트가 이미 쓰는 패턴대로
`BusinessException(ErrorCode.VALIDATION_FAILED)`을 던진다(`MemberService`,
`PortOneWebhookService`와 동일 패턴) — `GlobalExceptionHandler`의 `BusinessException` 핸들러가
그대로 400으로 매핑해준다. 검증 위치·구체 조건(예: 몇 이상/이하)은 Generate에서 결정한다.

## 태스크

- [x] `BuyerMypageService.notifications()`/`SellerMypageService.notifications()`에 `page`/`size`
      유효성 검증 추가
- [x] `BuyerMypageControllerTest`/`SellerMypageControllerTest`에 잘못된 `page`/`size` 케이스 추가

## 평가(통과) 기준

- 신규 테스트 통과(`page=-1` 또는 `size=0` 요청 시 400 `VALIDATION_FAILED`)
- 기존 `BuyerMypageControllerTest`/`SellerMypageControllerTest` 전체 통과

## 실행 결과

계획대로 `BuyerMypageService`/`SellerMypageService`의 `notifications()`에 `validatePageRequest(page,
size)`를 추가했다 — `page<0` 또는 `size<1`이면 이 프로젝트가 이미 쓰는 패턴대로
`BusinessException(ErrorCode.VALIDATION_FAILED)`를 던져 `GlobalExceptionHandler`가 400으로
매핑한다(구체 검증 위치·조건은 이 Generate 단계에서 결정).

`BuyerMypageControllerTest`/`SellerMypageControllerTest`에 각각 `page=-1`/`size=0` 케이스를
추가했고, `./gradlew test` **전체 385케이스 통과**(신규 2케이스 포함, 실패/에러 0).

## 리스크 / 전제

- 상한(예: `size` 최댓값 제한)은 이번 스코프에 넣지 않았다 — 발견된 버그는 "음수/0 값이 500을
  낸다"였고, 상한 미설정 자체는 별도 이슈이므로 임의로 스코프를 넓히지 않았다.
