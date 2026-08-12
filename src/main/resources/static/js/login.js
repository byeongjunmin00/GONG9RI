/**
 * login.js — 로그인 페이지(login.html) 전용 스크립트
 *
 * POST /api/auth/login 호출 → 성공 시 메인 페이지(`/`)로 리다이렉트한다.
 * (세션 쿠키(JSESSIONID)는 응답 헤더로 브라우저가 자동 저장하므로 클라이언트가 별도로 다룰 게 없다.)
 * 실패(VALIDATION_FAILED/LOGIN_FAILED) 시에는 API가 어느 입력값이 원인인지 구분해 주지 않아
 * 필드별이 아니라 폼 공통 에러 영역(#form-alert)에 서버 message를 그대로 표시한다.
 *
 * EMAIL_NOT_VERIFIED(이메일 인증 안 함)일 때는 "인증 메일 다시 보내기" 버튼도 같이 보여준다
 * (POST /api/auth/verify-email/resend 호출 — 계정 존재 여부와 무관하게 항상 같은 응답을 주므로
 * 결과 메시지도 항상 동일하게 보여준다).
 *
 * 회원가입(signup.html)에서 가입을 완료하거나 비밀번호 재설정(reset-password.html)을 마치면
 * 각각 `?signup=success`/`?reset=success`를 붙여 이 페이지로 이동시키는데, 그 경우 공통 안내
 * 영역에 성공 안내를 보여준다.
 */
(function () {
  var form = document.getElementById('login-form');
  var alertEl = document.getElementById('form-alert');
  var alertTextEl = document.getElementById('form-alert-text');
  var resendBtn = document.getElementById('resend-verification-btn');
  var submitBtn = document.getElementById('login-submit');
  var usernameInput = document.getElementById('username');
  var passwordInput = document.getElementById('password');

  if (!form || !alertEl || !alertTextEl || !resendBtn || !submitBtn || !usernameInput || !passwordInput) {
    return;
  }

  function showAlert(text, variant, showResend) {
    alertEl.hidden = false;
    alertTextEl.textContent = text;
    alertEl.className = 'form-alert form-alert--' + variant;
    resendBtn.hidden = !showResend;
    alertEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  function hideAlert() {
    alertEl.hidden = true;
    alertTextEl.textContent = '';
    resendBtn.hidden = true;
  }

  // 회원가입 완료 / 비밀번호 재설정 완료 후 이 페이지로 넘어온 경우 안내 문구를 보여준다.
  var params = new URLSearchParams(window.location.search);
  if (params.get('signup') === 'success') {
    showAlert('회원가입이 완료됐습니다. 이메일로 온 인증 링크를 클릭한 뒤 로그인해주세요.', 'success', false);
  } else if (params.get('reset') === 'success') {
    showAlert('비밀번호가 변경됐습니다. 새 비밀번호로 로그인해주세요.', 'success', false);
  } else if (params.get('error') === 'kakao') {
    showAlert('카카오 로그인에 실패했습니다. 잠시 후 다시 시도해주세요.', 'error', false);
  }

  // 다른 페이지에서 "로그인이 필요합니다" 안내를 통해 넘어온 경우 로그인 후 그 페이지로 돌아간다.
  // ?redirect=/product.html%3Fid%3D1 형태 — 오픈 리다이렉트 방지를 위해 같은 출처의 상대 경로만 허용한다
  // ("/"로 시작하고 "//"·"/\"로 시작하지 않는 경우만, 그 외에는 메인으로 이동).
  function resolveRedirectTarget() {
    var redirect = params.get('redirect');
    if (redirect && /^\/(?!\/|\\)/.test(redirect)) {
      return redirect;
    }
    return '/';
  }

  resendBtn.addEventListener('click', function () {
    resendBtn.disabled = true;
    window.Api.post('/auth/verify-email/resend', { username: usernameInput.value.trim() })
      .then(function () {
        showAlert('인증 메일을 다시 보냈습니다. 메일함을 확인해주세요.', 'success', false);
      })
      .catch(function (err) {
        console.error('[login.js] resend failed:', err);
        var message = (err && err.message) || '요청 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.';
        showAlert(message, 'error', false);
      })
      .then(function () {
        resendBtn.disabled = false;
      });
  });

  form.addEventListener('submit', function (event) {
    event.preventDefault();
    hideAlert();

    var username = usernameInput.value.trim();
    var password = passwordInput.value;

    // UX 보조용 최소 필수값 체크. 실제 판정 기준(SSOT)은 서버 응답(VALIDATION_FAILED)이다.
    if (!username || !password) {
      showAlert('아이디와 비밀번호를 모두 입력해주세요.', 'error', false);
      return;
    }

    submitBtn.disabled = true;

    window.Api.post('/auth/login', { username: username, password: password })
      .then(function () {
        window.location.href = resolveRedirectTarget();
      })
      .catch(function (err) {
        submitBtn.disabled = false;
        console.error('[login.js] login failed:', err);
        var message = (err && err.message) || '로그인에 실패했습니다. 잠시 후 다시 시도해주세요.';
        showAlert(message, 'error', err && err.code === 'EMAIL_NOT_VERIFIED');
      });
  });
})();
