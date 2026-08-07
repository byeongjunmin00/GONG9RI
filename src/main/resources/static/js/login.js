/**
 * login.js — 로그인 페이지(login.html) 전용 스크립트
 *
 * POST /api/auth/login 호출 → 성공 시 메인 페이지(`/`)로 리다이렉트한다.
 * (세션 쿠키(JSESSIONID)는 응답 헤더로 브라우저가 자동 저장하므로 클라이언트가 별도로 다룰 게 없다.)
 * 실패(VALIDATION_FAILED/LOGIN_FAILED) 시에는 API가 어느 입력값이 원인인지 구분해 주지 않아
 * 필드별이 아니라 폼 공통 에러 영역(#form-alert)에 서버 message를 그대로 표시한다.
 *
 * 회원가입(signup.html)에서 가입을 완료하면 `?signup=success`를 붙여 이 페이지로 이동시키는데,
 * 그 경우 공통 안내 영역에 성공 안내를 보여준다.
 */
(function () {
  var form = document.getElementById('login-form');
  var alertEl = document.getElementById('form-alert');
  var submitBtn = document.getElementById('login-submit');
  var usernameInput = document.getElementById('username');
  var passwordInput = document.getElementById('password');

  if (!form || !alertEl || !submitBtn || !usernameInput || !passwordInput) {
    return;
  }

  function showAlert(text, variant) {
    alertEl.hidden = false;
    alertEl.textContent = text;
    alertEl.className = 'form-alert form-alert--' + variant;
  }

  function hideAlert() {
    alertEl.hidden = true;
    alertEl.textContent = '';
  }

  // 회원가입 완료 후 이 페이지로 넘어온 경우 안내 문구를 보여준다.
  var params = new URLSearchParams(window.location.search);
  if (params.get('signup') === 'success') {
    showAlert('회원가입이 완료됐습니다. 로그인해주세요.', 'success');
  }

  form.addEventListener('submit', function (event) {
    event.preventDefault();
    hideAlert();

    var username = usernameInput.value.trim();
    var password = passwordInput.value;

    // UX 보조용 최소 필수값 체크. 실제 판정 기준(SSOT)은 서버 응답(VALIDATION_FAILED)이다.
    if (!username || !password) {
      showAlert('아이디와 비밀번호를 모두 입력해주세요.', 'error');
      return;
    }

    submitBtn.disabled = true;

    window.Api.post('/auth/login', { username: username, password: password })
      .then(function () {
        window.location.href = '/';
      })
      .catch(function (err) {
        submitBtn.disabled = false;
        console.error('[login.js] login failed:', err);
        var message = (err && err.message) || '로그인에 실패했습니다. 잠시 후 다시 시도해주세요.';
        showAlert(message, 'error');
      });
  });
})();
