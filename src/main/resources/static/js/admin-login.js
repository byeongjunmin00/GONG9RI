/**
 * admin-login.js — 관리자 로그인 페이지(admin/login.html) 전용 스크립트
 *
 * POST /api/auth/login은 role과 무관하게 누구나 로그인시켜준다(기존 일반 로그인과 같은 엔드포인트
 * 재사용, docs/dev/admin/design.md) — 그래서 로그인 성공 후 응답의 role이 ADMIN인지 프론트에서도
 * 한 번 더 확인한다. ADMIN이 아니면 방금 만들어진 세션을 바로 로그아웃시키고 안내만 보여준다(관리자
 * 아닌 계정이 이 페이지로 로그인한 채 남아있으면 혼란스러움 — 최종 판정은 물론 서버가 AdminController
 * 쪽에서 다시 한다, 이건 UX 보조일 뿐).
 * 성공(ADMIN 확인) 시 항상 /admin/dashboard.html로 이동한다(일반 로그인과 달리 ?redirect= 없음).
 */
(function () {
  var form = document.getElementById('login-form');
  var alertEl = document.getElementById('form-alert');
  var alertTextEl = document.getElementById('form-alert-text');
  var submitBtn = document.getElementById('login-submit');
  var usernameInput = document.getElementById('username');
  var passwordInput = document.getElementById('password');

  if (!form || !alertEl || !alertTextEl || !submitBtn || !usernameInput || !passwordInput) {
    return;
  }

  function showAlert(text) {
    alertEl.hidden = false;
    alertTextEl.textContent = text;
    alertEl.className = 'form-alert form-alert--error';
  }

  function hideAlert() {
    alertEl.hidden = true;
    alertTextEl.textContent = '';
  }

  form.addEventListener('submit', function (event) {
    event.preventDefault();
    hideAlert();

    var username = usernameInput.value.trim();
    var password = passwordInput.value;

    if (!username || !password) {
      showAlert('아이디와 비밀번호를 모두 입력해주세요.');
      return;
    }

    submitBtn.disabled = true;

    window.Api.post('/auth/login', { username: username, password: password })
      .then(function (member) {
        if (member && member.role === 'ADMIN') {
          window.location.href = '/admin/dashboard.html';
          return;
        }
        return window.Api.post('/auth/logout').then(function () {
          showAlert('관리자 계정이 아닙니다.');
          submitBtn.disabled = false;
        });
      })
      .catch(function (err) {
        submitBtn.disabled = false;
        console.error('[admin-login.js] login failed:', err);
        var message = (err && err.message) || '로그인에 실패했습니다. 잠시 후 다시 시도해주세요.';
        showAlert(message);
      });
  });
})();
