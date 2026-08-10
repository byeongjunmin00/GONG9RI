/**
 * signup.js — 회원가입 페이지(signup.html) 전용 스크립트
 *
 * POST /api/auth/signup 호출 → 성공 시 login.html로 리다이렉트(`?signup=success`)한다.
 * (가입 API는 세션을 만들지 않으므로 곧바로 로그인되지 않는다 — 로그인 페이지에서 안내를 보여준다.)
 * 실패:
 * - DUPLICATE_USERNAME(409): 원인 필드(아이디)가 명확하므로 #username-error에 표시한다.
 * - 그 외(VALIDATION_FAILED 등): 어느 필드가 원인인지 API가 구분해 주지 않으므로 #form-alert(공통)에 표시한다.
 */
(function () {
  var form = document.getElementById('signup-form');
  var alertEl = document.getElementById('form-alert');
  var usernameErrorEl = document.getElementById('username-error');
  var submitBtn = document.getElementById('signup-submit');

  var usernameInput = document.getElementById('username');
  var passwordInput = document.getElementById('password');
  var nameInput = document.getElementById('name');
  var emailInput = document.getElementById('email');

  if (
    !form ||
    !alertEl ||
    !usernameErrorEl ||
    !submitBtn ||
    !usernameInput ||
    !passwordInput ||
    !nameInput ||
    !emailInput
  ) {
    return;
  }

  function showAlert(text) {
    alertEl.hidden = false;
    alertEl.textContent = text;
    alertEl.className = 'form-alert form-alert--error';
    alertEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  function hideAlert() {
    alertEl.hidden = true;
    alertEl.textContent = '';
  }

  function showUsernameError(text) {
    usernameErrorEl.hidden = false;
    usernameErrorEl.textContent = text;
  }

  function hideUsernameError() {
    usernameErrorEl.hidden = true;
    usernameErrorEl.textContent = '';
  }

  function getSelectedRole() {
    var checked = form.querySelector('input[name="role"]:checked');
    return checked ? checked.value : '';
  }

  form.addEventListener('submit', function (event) {
    event.preventDefault();
    hideAlert();
    hideUsernameError();

    var username = usernameInput.value.trim();
    var password = passwordInput.value;
    var name = nameInput.value.trim();
    var email = emailInput.value.trim();
    var role = getSelectedRole();

    // UX 보조용 최소 필수값 체크. 실제 판정 기준(SSOT)은 서버 응답(VALIDATION_FAILED)이다.
    if (!username || !password || !name || !email || !role) {
      showAlert('모든 항목을 입력해주세요.');
      return;
    }

    submitBtn.disabled = true;

    window.Api.post('/auth/signup', {
      username: username,
      password: password,
      name: name,
      email: email,
      role: role,
    })
      .then(function () {
        window.location.href = '/login.html?signup=success';
      })
      .catch(function (err) {
        submitBtn.disabled = false;
        console.error('[signup.js] signup failed:', err);

        var message = (err && err.message) || '회원가입에 실패했습니다. 잠시 후 다시 시도해주세요.';

        if (err && err.code === 'DUPLICATE_USERNAME') {
          showUsernameError(message);
          return;
        }

        showAlert(message);
      });
  });
})();
