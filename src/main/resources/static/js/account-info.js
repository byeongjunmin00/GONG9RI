/**
 * account-info.js — 마이페이지(구매자/판매자 공통) "계정 정보" 섹션 전용 스크립트
 *
 * - 로드 시 GET /api/auth/me로 현재 이름/이메일을 불러와 입력창을 채운다. 401이면 아무것도
 *   안 하고 조용히 빠진다 — 같은 페이지의 다른 스크립트(buyer-mypage.js/seller-mypage.js)가
 *   이미 로그인 필요 배너를 띄우므로 중복 안내하지 않는다.
 * - PATCH /api/auth/me로 저장. DUPLICATE_EMAIL(409)은 이메일 필드 밑에, 그 외는 공통 알림에 표시한다.
 * - 이메일을 실제로 바꾼 경우(emailVerified가 응답에서 false로 내려옴) 재인증 메일을 다시 확인하라는
 *   안내를 별도로 보여준다 — 서버가 조용히 emailVerified를 초기화하기 때문에 사용자가 모르고
 *   넘어가면 다음 로그인이 막히는 걸 이해 못 할 수 있다.
 */
(function () {
  var form = document.getElementById('account-info-form');
  var alertEl = document.getElementById('account-info-alert');
  var emailErrorEl = document.getElementById('account-email-error');
  var submitBtn = document.getElementById('account-info-submit');
  var nameInput = document.getElementById('account-name');
  var emailInput = document.getElementById('account-email');

  if (!form || !alertEl || !emailErrorEl || !submitBtn || !nameInput || !emailInput) {
    return;
  }

  function showAlert(text, variant) {
    alertEl.hidden = false;
    alertEl.textContent = text;
    alertEl.className = 'form-alert form-alert--' + (variant || 'error');
  }

  function hideAlert() {
    alertEl.hidden = true;
    alertEl.textContent = '';
  }

  function showEmailError(text) {
    emailErrorEl.hidden = false;
    emailErrorEl.textContent = text;
  }

  function hideEmailError() {
    emailErrorEl.hidden = true;
    emailErrorEl.textContent = '';
  }

  function loadCurrentInfo() {
    window.Api.get('/auth/me')
      .then(function (member) {
        nameInput.value = member.name || '';
        emailInput.value = member.email || '';
      })
      .catch(function (err) {
        // 401은 다른 스크립트가 이미 안내하므로 조용히 무시. 그 외 에러만 폼을 못 쓰게 막는다.
        if (err && err.status !== 401) {
          console.error('[account-info.js] failed to load current info:', err);
        }
      });
  }

  form.addEventListener('submit', function (event) {
    event.preventDefault();
    hideAlert();
    hideEmailError();

    var name = nameInput.value.trim();
    var email = emailInput.value.trim();

    if (!name || !email) {
      showAlert('이름과 이메일을 모두 입력해주세요.');
      return;
    }

    submitBtn.disabled = true;

    window.Api.patch('/auth/me', { name: name, email: email })
      .then(function (member) {
        if (member && member.emailVerified === false) {
          showAlert('저장했습니다. 이메일을 변경해서 재인증이 필요해요 — 새 주소로 발송된 인증 메일을 확인해주세요.', 'success');
        } else {
          showAlert('저장했습니다.', 'success');
        }
      })
      .catch(function (err) {
        console.error('[account-info.js] update failed:', err);
        var message = (err && err.message) || '정보 수정에 실패했습니다. 잠시 후 다시 시도해주세요.';

        if (err && err.code === 'DUPLICATE_EMAIL') {
          showEmailError(message);
          return;
        }

        showAlert(message);
      })
      .then(function () {
        submitBtn.disabled = false;
      });
  });

  loadCurrentInfo();
})();
