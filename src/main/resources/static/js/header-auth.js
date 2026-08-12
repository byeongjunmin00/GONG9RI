/**
 * header-auth.js — 헤더 로그인 상태 연동
 *
 * js/include.js가 모든 data-include 삽입을 끝낸 뒤 document에 발행하는
 * 'gong9ri:includes-ready' 이벤트를 구독해, 헤더 DOM이 준비된 뒤에만 동작한다.
 *
 * 흐름:
 * 1. GET /api/auth/me 호출
 *    - 성공(200): 로그인 상태 → #header-auth-guest 숨기고 #header-auth-user 노출
 *      (사용자 이름 표시), 로그인한 역할(BUYER/SELLER)에 해당하는 nav 링크만
 *      hidden 속성을 해제해 노출하고 강조 클래스도 추가한다(기본값은 hidden이라
 *      역할이 다른 링크는 그대로 숨겨진 채 유지된다).
 *    - 실패(401 등): 비로그인 상태 → 아무것도 하지 않아 nav 링크가 기본값(hidden)인 채로 유지된다.
 * 2. #header-auth-logout 클릭 시 POST /api/auth/logout 호출 후 성공하면 현재 페이지를 새로고침한다.
 * 3. /auth/me 호출이 끝나면(성공/실패 모두) document에 'gong9ri:auth-resolved' 커스텀 이벤트를
 *    { detail: { loggedIn, member } } 형태로 발행한다. 다른 스크립트(js/chat-widget.js 등)가
 *    로그인 상태·역할을 재사용하려고 다시 /auth/me를 호출하지 않도록 이 이벤트를 구독한다.
 *
 * 이 파일은 window.Api(js/api.js)에 의존한다 — HTML에서 api.js보다 뒤에 로드해야 한다.
 */
(function () {
  function applyLoggedInState(member) {
    var guest = document.getElementById('header-auth-guest');
    var user = document.getElementById('header-auth-user');
    var nameEl = document.getElementById('header-auth-user-name');

    if (nameEl) {
      nameEl.textContent = member.name + '님';
    }
    if (guest) {
      guest.hidden = true;
    }
    if (user) {
      user.hidden = false;
    }

    var roleLinks = document.querySelectorAll('.site-header__nav a[data-role]');
    Array.prototype.forEach.call(roleLinks, function (link) {
      if (link.getAttribute('data-role') === member.role) {
        link.classList.add('nav-link--role-active');
        link.hidden = false;
      }
    });
  }

  function bindLogout() {
    var logoutBtn = document.getElementById('header-auth-logout');
    if (!logoutBtn || !window.Api) {
      return;
    }
    logoutBtn.addEventListener('click', function () {
      window.Api.post('/auth/logout')
        .then(function () {
          window.location.reload();
        })
        .catch(function (err) {
          console.error('[header-auth.js] 로그아웃 실패:', err);
        });
    });
  }

  function init() {
    bindLogout();

    if (!window.Api) {
      return;
    }

    window.Api.get('/auth/me')
      .then(function (member) {
        applyLoggedInState(member);
        document.dispatchEvent(
          new CustomEvent('gong9ri:auth-resolved', { detail: { loggedIn: true, member: member } })
        );
      })
      .catch(function () {
        // 미인증(401 등) — 비로그인 상태로 간주하고 기존 마크업을 그대로 유지한다.
        document.dispatchEvent(
          new CustomEvent('gong9ri:auth-resolved', { detail: { loggedIn: false, member: null } })
        );
      });
  }

  document.addEventListener('gong9ri:includes-ready', init);
})();
