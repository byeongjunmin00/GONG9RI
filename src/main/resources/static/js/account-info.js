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

  var profileImageInput = document.getElementById('profile-image-input');
  var btnChangeProfileImage = document.getElementById('btn-change-profile-image');
  var btnDeleteProfileImage = document.getElementById('btn-delete-profile-image');
  var profileImagePreview = document.getElementById('profile-image-preview');
  var profileImageDefaultIcon = document.getElementById('profile-image-default-icon');

  function updateProfileAvatar(url) {
    if (url) {
      if (profileImagePreview) {
        profileImagePreview.src = url;
        profileImagePreview.hidden = false;
      }
      if (profileImageDefaultIcon) {
        profileImageDefaultIcon.hidden = true;
      }
      if (btnDeleteProfileImage) {
        btnDeleteProfileImage.hidden = false;
      }
      var profileAvatarEl = document.querySelector('.mypage-profile__avatar');
      if (profileAvatarEl) {
        while (profileAvatarEl.firstChild) {
          profileAvatarEl.removeChild(profileAvatarEl.firstChild);
        }
        var img = document.createElement('img');
        img.src = url;
        img.alt = '프로필 사진';
        img.style.width = '100%';
        img.style.height = '100%';
        img.style.objectFit = 'cover';
        img.style.borderRadius = '50%';
        profileAvatarEl.appendChild(img);
      }
    } else {
      if (profileImagePreview) {
        profileImagePreview.src = '';
        profileImagePreview.hidden = true;
      }
      if (profileImageDefaultIcon) {
        profileImageDefaultIcon.hidden = false;
      }
      if (btnDeleteProfileImage) {
        btnDeleteProfileImage.hidden = true;
      }
    }
  }

  if (btnChangeProfileImage && profileImageInput) {
    btnChangeProfileImage.addEventListener('click', function () {
      profileImageInput.click();
    });

    profileImageInput.addEventListener('change', function () {
      var file = profileImageInput.files && profileImageInput.files[0];
      if (!file) return;

      if (file.size > 5 * 1024 * 1024) {
        showAlert('이미지 파일 크기는 5MB 이하만 가능합니다.');
        profileImageInput.value = '';
        return;
      }

      var formData = new FormData();
      formData.append('file', file);

      btnChangeProfileImage.disabled = true;

      window.fetch('/api/member/profile-image', {
        method: 'POST',
        body: formData
      })
      .then(function (res) { return res.json(); })
      .then(function (res) {
        btnChangeProfileImage.disabled = false;
        profileImageInput.value = '';
        if (res && res.success && res.data) {
          showAlert('프로필 사진이 변경되었습니다.', 'success');
          updateProfileAvatar(res.data.profileImageUrl);
        } else {
          showAlert((res && res.error && res.error.message) || '프로필 사진 변경 실패', 'error');
        }
      })
      .catch(function (err) {
        btnChangeProfileImage.disabled = false;
        profileImageInput.value = '';
        console.error('[account-info.js] profile image upload error:', err);
        showAlert('프로필 사진 변경 중 오류가 발생했습니다.');
      });
    });
  }

  if (btnDeleteProfileImage) {
    btnDeleteProfileImage.addEventListener('click', function () {
      if (!window.confirm('프로필 사진을 삭제하시겠습니까?')) return;

      btnDeleteProfileImage.disabled = true;
      window.Api.del('/member/profile-image')
        .then(function (res) {
          btnDeleteProfileImage.disabled = false;
          showAlert('프로필 사진이 삭제되었습니다.', 'success');
          updateProfileAvatar(null);
        })
        .catch(function (err) {
          btnDeleteProfileImage.disabled = false;
          console.error('[account-info.js] delete profile image error:', err);
          showAlert('프로필 사진 삭제 실패');
        });
    });
  }

  function loadCurrentInfo() {
    window.Api.get('/auth/me')
      .then(function (member) {
        nameInput.value = member.name || '';
        emailInput.value = member.email || '';
        if (member.profileImageUrl) {
          updateProfileAvatar(member.profileImageUrl);
        }
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
          showAlert('이메일이 변경되어 재인증이 필요합니다. 새 주소로 발송된 인증 메일을 확인하신 후 다시 로그인해주세요.', 'success');
          setTimeout(function () {
            window.location.href = '/login.html';
          }, 1500);
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
