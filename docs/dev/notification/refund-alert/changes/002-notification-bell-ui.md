# 002 — 알림 벨 UI + 읽음 처리

대상: notification/refund-alert
담당: 민병준

## 배경 / 요구

`GET /api/{buyer,seller}/mypage/notifications` API는 처음부터 있었고 실제로 환불 이벤트마다 알림이 계속 쌓이고 있었는데, 이걸 보여줄 화면이 아예 없었다(코드 전수 검색으로 프론트 어디에도 "notification" 참조가 없음을 확인). 사용자가 "다음에 뭐 더 손볼 거 있나"라고 물어서 코드베이스를 점검하다 발견 — 완성된 백엔드 기능이 사용자한테 한 번도 노출된 적 없는 상태였음.

## 설계

- 계약 변경: `POST /api/{buyer,seller}/mypage/notifications/{notificationId}/read`, `POST /api/{buyer,seller}/mypage/notifications/read-all` 신규(`docs/api/mypage.md` 갱신).
- 영향 계층: `entity`(도메인 메서드 추가) → `repository`(벌크 UPDATE 추가) → `service`(`NotificationService`에 읽음 처리 2개 추가, `Buyer/SellerMypageService`가 역할 게이트 후 위임) → `controller`(기존 컨트롤러에 엔드포인트 추가) → 프론트(헤더 벨 UI 신규).
- 알림은 역할과 무관한 회원 단위 개념이라, 읽음 처리 로직은 역할 체크 없이 소유권만 확인하는 공용 서비스(`NotificationService`)에 두고, 역할 게이트는 호출부(`Buyer/SellerMypageService`)가 기존 패턴대로 먼저 확인.
- 프론트: 로그인 여부·역할은 `js/header-auth.js`가 발행하는 `gong9ri:auth-resolved` 이벤트로 받는다(추가 API 호출 없음, `js/chat-widget.js`와 동일 패턴). BUYER/SELLER만 벨 노출(ADMIN은 이 기능 대상 아님, 백엔드에도 관리자용 알림 엔드포인트 없음).

## 태스크

- [x] `Notification.markAsRead()` 도메인 메서드 추가
- [x] `NotificationRepository.markAllAsReadByMemberId` 벌크 UPDATE(`clearAutomatically=true`) 추가
- [x] `ErrorCode.NOTIFICATION_NOT_FOUND` 추가
- [x] `NotificationService.markAsRead`/`markAllAsRead` 추가(소유권 검증)
- [x] `Buyer/SellerMypageService`에 역할 게이트 위임 메서드 추가(`@Transactional` 명시 — 클래스 기본 readOnly 덮어쓰기)
- [x] `Buyer/SellerMypageController`에 읽음 처리 엔드포인트 2개씩 추가
- [x] 헤더 벨 UI(`partials/header.html`, `js/header-notifications.js`, CSS) 신규, 17개 페이지에 스크립트 태그 추가
- [x] 컨트롤러 테스트 각 4케이스(개별 읽음 성공/타인 알림 403/존재하지 않는 알림 404, 전체 읽음 성공)

## 평가(통과) 기준

- `./gradlew test` 전체 통과
- 로컬 실측: 실제 회원가입(이메일 인증만 DB로 우회) → 로그인 → 세션 쿠키로 알림 API 전체 흐름을 curl로 검증 — 목록 조회(빈 배열) → 알림 2건 심기(SQL) → 개별 읽음 처리 → 재조회(1건만 읽음) → 전체 읽음 처리 → 재조회(둘 다 읽음) → 다른 회원 계정으로 타인 알림 읽음 시도 시 403 확인. 테스트 계정/데이터는 검증 후 직접 정리.
- 정적 자산(JS/CSS) 실제 서빙 확인
