/**
 * reset-password.js — 비밀번호 재설정 페이지(reset-password.html) 전용 스크립트
 *
 * URL의 `?token=`(비밀번호 재설정 이메일의 링크가 여기로 붙여줌)을 읽어 폼 제출 시
 * POST /api/auth/password/reset에 같이 보낸다. 토큰이 없으면 폼을 아예 숨기고 안내만 보여준다.
 * 성공하면 로그인 페이지로 이동시킨다(비밀번호가 바뀐 걸 곧바로 로그인해서 확인시켜주는 게 자연스러움).
 */
(function () {
  var form = document.getElementById('reset-password-form');
  var alertEl = document.getElementById('form-alert');
  var submitBtn = document.getElementById('reset-password-submit');
  var newPasswordInput = document.getElementById('new-password');

  if (!form || !alertEl || !submitBtn || !newPasswordInput) {
    return;
  }

  function showAlert(text, variant) {
    alertEl.hidden = false;
    alertEl.textContent = text;
    alertEl.className = 'form-alert form-alert--' + variant;
  }

  var token = new URLSearchParams(window.location.search).get('token');
  if (!token) {
    form.hidden = true;
    showAlert('유효하지 않은 링크입니다. 비밀번호 찾기를 다시 요청해주세요.', 'error');
    return;
  }

  form.addEventListener('submit', function (event) {
    event.preventDefault();

    var newPassword = newPasswordInput.value;
    if (!newPassword) {
      showAlert('새 비밀번호를 입력해주세요.', 'error');
      return;
    }

    submitBtn.disabled = true;

    window.Api.post('/auth/password/reset', { token: token, newPassword: newPassword })
      .then(function () {
        window.location.href = '/login.html?reset=success';
      })
      .catch(function (err) {
        submitBtn.disabled = false;
        console.error('[reset-password.js] reset failed:', err);
        var message = (err && err.message) || '비밀번호 재설정에 실패했습니다. 링크가 만료됐을 수 있어요.';
        showAlert(message, 'error');
      });
  });
})();
