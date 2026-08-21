/**
 * withdraw.js — 회원 탈퇴 (member/withdraw, 2026-08-21)
 *
 * 되돌릴 수 없는 동작이라 세 단계를 거치게 한다: 버튼 → 비밀번호 확인 → 최종 확인 창.
 * 계정 정보 폼(account-info.js)과 파일을 나눈 이유는, 저장 폼의 제출 흐름에 섞이면 "저장"을 누르려다
 * 탈퇴가 되는 실수를 만들기 쉬워서다.
 *
 * 카카오 로그인 계정은 비밀번호가 없다(가입 시 랜덤 값으로 채운다) — 그래서 비밀번호 입력을 감추고
 * 서버도 그때만 검사를 건너뛴다. 판정은 서버가 하고(소셜 여부는 서버가 안다), 프론트는 화면만 맞춘다.
 */
(function () {
  'use strict';

  var openBtn = document.getElementById('btn-withdraw-open');
  var confirmBox = document.getElementById('withdraw-confirm');
  var passwordField = document.getElementById('withdraw-password-field');
  var passwordInput = document.getElementById('withdraw-password');
  var confirmBtn = document.getElementById('btn-withdraw-confirm');
  var cancelBtn = document.getElementById('btn-withdraw-cancel');
  var alertEl = document.getElementById('withdraw-alert');

  if (!openBtn || !confirmBox || !confirmBtn || !cancelBtn) {
    return;
  }

  // 카카오 계정이면 비밀번호 칸을 감춘다. /auth/me는 kakaoId를 내려주지 않으므로 이메일 도메인으로
  // 판단하지 않고, 서버가 비밀번호를 요구하지 않는다는 점만 이용한다 — 즉 일단 비워서 보내보고
  // 서버가 거절하면 그때 칸을 보여준다(프론트가 계정 종류를 추측하지 않게 하기 위함).
  var passwordRequired = true;

  function showAlert(message, variant) {
    if (!alertEl) {
      window.alert(message);
      return;
    }
    alertEl.hidden = false;
    alertEl.className = 'form-alert form-alert--' + (variant || 'error');
    alertEl.textContent = message;
  }

  function hideAlert() {
    if (alertEl) {
      alertEl.hidden = true;
      alertEl.textContent = '';
    }
  }

  openBtn.addEventListener('click', function () {
    confirmBox.hidden = false;
    openBtn.hidden = true;
    hideAlert();
  });

  cancelBtn.addEventListener('click', function () {
    confirmBox.hidden = true;
    openBtn.hidden = false;
    if (passwordInput) {
      passwordInput.value = '';
    }
    hideAlert();
  });

  confirmBtn.addEventListener('click', function () {
    hideAlert();
    var password = passwordInput ? passwordInput.value : '';
    if (passwordRequired && !password) {
      showAlert('비밀번호를 입력해주세요.');
      return;
    }
    // 마지막 관문 — 여기까지 온 사람도 실수일 수 있다.
    if (!window.confirm('정말 탈퇴하시겠어요?\n다시 로그인할 수 없고 되돌릴 수 없습니다.')) {
      return;
    }

    confirmBtn.disabled = true;
    // Api.del의 두 번째 인자는 body가 아니라 옵션 객체다 — body는 옵션 안에 넣어야 실린다.
    window.Api.del('/member', { body: { password: password } })
      .then(function () {
        // 서버가 세션을 이미 끊었다. 메인으로 보내면서 안내 배너를 띄운다(로그인 성공/실패 안내와
        // 같은 "쿼리파라미터 + 배너" 패턴).
        window.location.href = '/?withdrawn=1';
      })
      .catch(function (err) {
        confirmBtn.disabled = false;
        if (err && err.code === 'LOGIN_FAILED') {
          if (passwordField) {
            passwordField.hidden = false;
          }
          passwordRequired = true;
          showAlert('비밀번호가 올바르지 않습니다.');
          return;
        }
        if (err && err.code === 'FORBIDDEN') {
          showAlert('관리자 계정은 탈퇴할 수 없습니다.');
          return;
        }
        showAlert((err && err.message) || '탈퇴 처리 중 오류가 발생했습니다.');
      });
  });
})();
