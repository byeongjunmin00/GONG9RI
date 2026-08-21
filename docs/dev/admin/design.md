# 관리자(Admin) — Design

## 개요

가입자/상품/결제·환불 현황을 지금까지는 Railway DB 콘솔로 직접 조회해야 했다. "관리자 로그인 화면
만드는 게 낫지 않냐"는 사용자 요청으로 착수. 스코프는 "회원 조회만"/"+상품·결제 현황"/"+회원 정지·
삭제까지" 세 단계 중 가장 넓은 걸 선택함("걍 싹 다 하자 어드민이니께").

## 설계 원칙

- **새 인증 체계를 안 만든다.** `Member`에 `Role.ADMIN`을 추가해 기존 세션 로그인
  (`POST /api/auth/login`)·`MemberUserDetails`·`SecurityConfig`를 그대로 재사용한다. 헤더 nav의
  `data-role` 매칭(`header-auth.js`)도 코드 변경 없이 `data-role="ADMIN"` 링크만 추가하면 동작한다.
- **공개 회원가입으로는 관리자가 될 수 없다.** `MemberSignupRequest.role`이 `Role` enum을 그대로
  받기 때문에, `MemberService.signup()`에서 `role == ADMIN`이면 무조건 `VALIDATION_FAILED`로 거절한다.
  최초 관리자 계정은 배포 후 DB에 직접 심는다(API/시드 마이그레이션으로 안 만듦).
- **회원 "삭제"는 하드 삭제를 함부로 허용하지 않는다.** `Member`를 참조하는 테이블이 많다
  (Product/Payment/Review/GroupBuyTeam/TeamParticipation/Wishlist/Inquiry/RefundRequest/ChatSession).
  그래서 관리 수단을 두 단계로 나눴다:
  - **정지(`Member.suspended`)** — 기본 수단. 로그인 시 `emailVerified`와 같은 자리에서 체크해
    거절(`ACCOUNT_SUSPENDED`, 403). 언제든 되돌릴 수 있다.
  - **삭제** — 위 9개 테이블(Product/Payment/Review/GroupBuyTeam/TeamParticipation/Wishlist/
    Inquiry(작성자·답변자 둘 다)/RefundRequest/ChatSession) 전부에 이 회원을 참조하는 행이 하나도
    없을 때만 허용한다(`AdminService.hasActivity()`). 하나라도 있으면 `409 MEMBER_HAS_ACTIVITY`로
    거절하고 정지로 유도한다. 삭제가 허용된 경우에도, 다른 테이블이 참조하지 않는 leaf 데이터
    (Notification/AiSuggestionLog/SellerRevenueSummary)는 같은 트랜잭션에서 함께 지운다.
    ChatSession은 ChatMessage/ChatInteractionLog가 더 참조하는 3단 체인이라 여기서 cascade
    삭제하지 않고, 대신 "활동 있음"에 포함시켜 삭제 자체를 막는 쪽을 택했다.
  - 관리자가 자기 자신을 정지/삭제해 스스로 잠그는 걸 막는 가드(`requireNotSelf`)도 있다.
- **상품 삭제만 예외적으로 쓰기 액션이다**(2026-08-21 추가, 사용자 요청). 삭제 정책은 회원 삭제와 같은 결로 맞췄다 — 결제·공구팀·리뷰가 있으면 `PRODUCT_HAS_ACTIVITY`(409)로 거절하고, 찜·문의·가격구간·이미지 행은 상품과 함께 지운다.
  - 구현은 `ProductService.deleteByAdmin()`이 `delete()`와 **같은 내부 경로**를 쓴다. 관리자용으로 따로 구현하면 삭제 정책이나 캐시 무효화가 한쪽만 고쳐진다.
  - 이 가드는 관리자 기능을 만들며 새로 넣었지만 **판매자 삭제에도 원래 없던 구멍**이었다. 가드 없이 결제가 달린 상품을 지우면 `payment.product_id`의 FK(NO ACTION)가 DELETE를 거부한다 — 로컬 DB에서 직접 재현해 확인했다(`ERROR 1451 (23000) Cannot delete or update a parent row`).
- **환불 현황은 읽기 전용.** 환불 강제 처리 같은 쓰기 액션은 이미 판매자 쪽
  흐름(환불 승인/거절은 `RefundRequestController`가 판매자 전용으로 처리)과 겹치고 위험도가 높아
  이번 스코프에서 뺐다. 상품 목록은 새 백엔드를 만들지 않고 기존 공개 `GET /api/products`를 그대로
  쓴다.

## 데이터 모델

`member.role` — enum에 `ADMIN` 추가(마이그레이션 불필요, `@Enumerated(EnumType.STRING)`이라 값만 늘어남).
`member.suspended` — `BOOLEAN NOT NULL DEFAULT false`(`emailVerified`와 동일한 `@ColumnDefault` 패턴).

## API

`docs/api/admin.md` 참고. 전부 `AdminService`의 `requireAdmin()` 서비스단 가드로 보호한다(이
프로젝트는 SecurityConfig에 role 기반 URL 매처를 두지 않고 전부 서비스단에서 `requireXxx()`로
검사하는 패턴 — `ProductService.requireSeller()` 등과 동일).

## 프론트

`static/admin/{login,dashboard,members,products,refunds,support}.html` + 대응 JS. 전부 서브디렉토리라
`SecurityConfig`의 `/**/*.html` permitAll에 이미 걸려 있어 별도 설정이 필요 없다. 각 페이지는
`js/admin-guard.js`의 `AdminGuard.requireAdmin()`으로 진입 시 role을 확인하고, ADMIN이 아니면
(비로그인 포함) `/admin/login.html`로 돌려보낸다 — 최종 판정은 항상 서버(403)고 이건 UX 보조다.

- **대시보드 UI/UX 및 서브 탭 네비게이션** (2026-08-21 `003-admin-dashboard-redesign` 및 `007-admin-dashboard-layout-cleanup` 개편):
  - 대시보드(`admin/dashboard.html`): 상단 관리자 프로필 배너 헤더(`.mypage-profile-card`), KPI 요약 카드 그리드(`.mypage-summary-grid` + `.summary-card`), 상단 중복 서브 탭 제거 후 하단 4개 주요 관리 기능 퀵 바로가기 카드를 **2x2 좌우 반응형 그리드**로 깔끔하게 재배치.
  - 서브 탭 네비게이션: 서브 관리자 페이지들(`members.html`, `products.html` 등)에 브랜드 공용 탭 컴포넌트(`.mypage-nav-tabs`, `.mypage-tab-btn`)를 지속 제공.
- **회원 관리 & 상품 현황 종합 정보 및 인사이트 개편** (2026-08-21 `004-admin-members-products-redesign`, `005-admin-n1-server-search-fix`, `006-admin-controller-parameter-fix` 개편):
  - 회원 관리(`admin/members.html`): 회원의 종합 활동 수치(`purchaseCount`, `teamCount`, `productCount`) 배치 JPQL 집계로 N+1 쿼리 해결 (쿼리 61개 -> 4개), 역할/상태별 필터 탭 및 서버 사이드 페이징 동적 DB 키워드 검색 적용.
  - 상품 현황(`admin/products.html`): 대표 썸네일 이미지(`imageUrl`), 리뷰 평점/개수 및 활성 공구팀 진행률 인라인 표시, 관리자 판단용 🚀 **추천/인기 푸시** 배지 vs ⚠️ **숨김/제재** 배지 노출, 상태별 필터 탭 및 서버 사이드 페이징 동적 DB 키워드 검색 적용.

## 관련 코드

`entity/Role.java`(`ADMIN`), `entity/Member.java`(`suspended`/`suspend()`/`unsuspend()`),
`service/MemberService.signup()`(ADMIN 가입 차단), `controller/AuthController.login()`(정지 계정
거절), `service/AdminService.java`, `controller/AdminController.java`,
`repository/MemberRepositoryCustom.java`/`MemberRepositoryImpl.java`,
`dto/AdminMemberResponse.java`/`AdminMemberPageResponse.java`/`AdminDashboardResponse.java`/
`AdminRefundPageResponse.java`, `static/admin/*.html`, `static/js/admin-*.js`,
`static/partials/header.html`(`data-role="ADMIN"` nav 링크).
- 경위: `docs/dev/admin/changes/008-admin-push-filter-and-test-fix.md`, 실행 로그: `docs/logs/frontend/admin/008-admin-push-filter-and-test-fix.md`

