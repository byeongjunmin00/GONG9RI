# 관리자 화면 추가

대상: admin
담당: 민병준

## 배경 / 요구

가입자/상품/결제 현황을 Railway DB 콘솔로만 볼 수 있었던 상태에서, 사용자가 "관리자 로그인 화면
만드는 게 낫지 않냐"고 제안. 스코프를 세 단계(조회만/+상품·결제 현황/+정지·삭제까지)로 물었더니
"걍 싹 다 하자 어드민이니께"로 가장 넓은 스코프를 선택함.

## 설계

`docs/dev/admin/design.md` 참고 — 새 인증 체계 없이 `Role.ADMIN` 추가로 기존 세션 로그인 재사용,
정지를 기본 관리 수단으로 삼고 삭제는 활동 기록이 전혀 없는 계정만 허용.

## 태스크

- [x] `Role.ADMIN` + `Member.suspended` + 공개 가입 차단 + 로그인 시 정지 계정 거절
- [x] `ErrorCode.ACCOUNT_SUSPENDED`/`MEMBER_NOT_FOUND`/`MEMBER_HAS_ACTIVITY`
- [x] 활동 존재 체크용 repository 메서드 9개(Product/Payment/Review/GroupBuyTeam/
      TeamParticipation/Wishlist/Inquiry/RefundRequest/ChatSession)
- [x] `AdminService`(회원 목록/정지/정지해제/삭제, 대시보드 요약, 환불요청 전체 목록) +
      `AdminController`
- [x] 프론트 4개 화면(로그인/대시보드/회원 관리/상품 현황/환불 요청 현황) + 헤더 nav
- [x] 테스트: 비관리자 접근 거절, ADMIN 공개가입 차단, 정지 계정 로그인 거절, 회원 목록/정지/
      정지해제, 활동 있는 회원 삭제 거절(409)/활동 없는 회원 삭제 성공, 대시보드, 환불 목록
- [x] `docs/api/admin.md` 신규, `docs/api/auth.md`/`docs/db/member.md` 갱신

## 평가(통과) 기준

- `./gradlew test` 통과
- 로컬 실서버로 관리자 계정(DB 직접 시드) 로그인 → 대시보드 → 회원 정지/정지해제 실측 → 활동 있는
  계정 삭제 시 409 실측 → 활동 없는 테스트 계정 삭제 성공 실측 → 상품/환불 목록 노출 확인
- 일반 회원가입 API로 `role=ADMIN` 직접 호출 시 거절 확인
