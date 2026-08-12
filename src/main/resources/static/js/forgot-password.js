/**
 * forgot-password.js — 비밀번호 찾기 페이지(forgot-password.html) 전용 스크립트
 *
 * POST /api/auth/password/reset-request 호출 → 결과와 무관하게(계정 존재 여부를 노출하지 않으므로
 * 서버가 항상 같은 성공 응답을 준다) 항상 같은 안내 문구를 보여준다. 429(TOO_MANY_REQUESTS)만
 * 예외적으로 다른 문구를 보여준다.
 */
(function () {
  var form = document.getElementById('forgot-password-form');
  var alertEl = document.getElementById('form-alert');
  var submitBtn = document.getElementById('forgot-password-submit');
  var emailInput = document.getElementById('email');

  if (!form || !alertEl || !submitBtn || !emailInput) {
    return;
  }

  function showAlert(text, variant) {
    alertEl.hidden = false;
    alertEl.textContent = text;
    alertEl.className = 'form-alert form-alert--' + variant;
    alertEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  form.addEventListener('submit', function (event) {
    event.preventDefault();

    var email = emailInput.value.trim();
    if (!email) {
      showAlert('이메일을 입력해주세요.', 'error');
      return;
    }

    submitBtn.disabled = true;

    window.Api.post('/auth/password/reset-request', { email: email })
      .then(function () {
        showAlert('입력하신 이메일이 가입돼 있다면 재설정 링크를 보냈습니다. 메일함을 확인해주세요.', 'success');
        form.reset();
      })
      .catch(function (err) {
        console.error('[forgot-password.js] request failed:', err);
        var message = (err && err.status === 429)
          ? (err.message || '요청이 너무 많습니다. 잠시 후 다시 시도해주세요.')
          : '요청 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.';
        showAlert(message, 'error');
      })
      .then(function () {
        submitBtn.disabled = false;
      });
  });
})();
