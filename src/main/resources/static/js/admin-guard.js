/**
 * admin-guard.js — 관리자 전용 페이지(admin/*.html) 공용 접근 가드
 *
 * GET /api/auth/me로 로그인 상태·role을 확인해, ADMIN이 아니면(비로그인 포함) /admin/login.html로
 * 돌려보낸다. 최종 판정은 항상 서버(AdminController가 각 요청마다 403)라 이건 UX 보조일 뿐이지만,
 * 네 개 admin 페이지가 전부 똑같은 체크를 반복하므로(header-auth.js의 role 표시와는 별개 목적 —
 * 저건 nav 링크만 숨기지 페이지 접근 자체는 안 막는다) 한 곳에 모아둔다.
 */
(function () {
  function requireAdmin() {
    if (!window.Api) {
      window.location.href = '/admin/login.html';
      return Promise.resolve(null);
    }
    return window.Api.get('/auth/me')
      .then(function (member) {
        if (!member || member.role !== 'ADMIN') {
          window.location.href = '/admin/login.html';
          return null;
        }
        return member;
      })
      .catch(function () {
        window.location.href = '/admin/login.html';
        return null;
      });
  }

  window.AdminGuard = { requireAdmin: requireAdmin };
})();
