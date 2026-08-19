/**
 * header-wishlist-badge.js — 헤더 찜 아이콘 옆 숫자 뱃지(구매자 전용)
 *
 * 찜 아이콘 자체(노출 여부)는 기존 header-auth.js의 data-role 매칭이 그대로 담당한다 — 이 스크립트는
 * 그 옆에 "몇 개 찜했는지" 숫자만 채운다. 목록을 드롭다운으로 새로 만들지는 않기로 했다(찜 목록은
 * 이미지·가격이 있는 카드형이라 마이페이지의 기존 목록이 더 적합, 알림 벨과의 차이점 —
 * partials/header.html 주석 참고). 로그인 여부·역할은 js/header-auth.js가 발행하는
 * 'gong9ri:auth-resolved' 이벤트로 받는다(추가 /auth/me 호출 없음, js/chat-widget.js와 동일 패턴).
 */
(function () {
  document.addEventListener('gong9ri:includes-ready', function () {
    var badgeEl = document.getElementById('header-wishlist-badge');

    if (!badgeEl || !window.Api) {
      return;
    }

    function updateBadge(count) {
      if (count > 0) {
        badgeEl.textContent = count > 9 ? '9+' : String(count);
        badgeEl.hidden = false;
      } else {
        badgeEl.hidden = true;
      }
    }

    document.addEventListener('gong9ri:auth-resolved', function (event) {
      var detail = event.detail || {};
      if (!detail.loggedIn || !detail.member || detail.member.role !== 'BUYER') {
        badgeEl.hidden = true;
        return;
      }

      window.Api.get('/buyer/mypage/wishlist')
        .then(function (data) {
          updateBadge((data || []).length);
        })
        .catch(function (err) {
          console.error('[header-wishlist-badge.js] 찜 개수 조회 실패:', err);
        });
    });
  });
})();
