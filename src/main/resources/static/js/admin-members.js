/**
 * admin-members.js — 회원 관리(admin/members.html) 전용 스크립트
 *
 * GET /api/admin/members로 회원을 최신 가입순으로 불러온다("더 보기" 페이지네이션, main.js의 상품
 * 목록과 동일 패턴). 행마다 정지/정지해제/삭제 버튼을 붙인다.
 * - 정지/정지해제: POST /api/admin/members/{id}/suspend|unsuspend → 성공하면 그 행만 다시 그린다
 *   (전체 재조회 안 함).
 * - 삭제: window.confirm 확인 후 DELETE /api/admin/members/{id} → 성공(204)하면 목록에서 제거,
 *   409(MEMBER_HAS_ACTIVITY, 활동 기록 있음)면 그 사유를 alert로 보여준다(정지로 유도).
 * - 관리자 본인 계정 행에는 액션 버튼을 아예 안 붙인다(AdminService.requireNotSelf와 짝 — 자기
 *   자신을 정지/삭제해 잠기는 걸 막는 서버 가드의 UX 보조).
 */
(function () {
  var pageAlertEl = document.getElementById('page-alert');
  var pageAlertTextEl = document.getElementById('page-alert-text');
  var statusEl = document.getElementById('members-status');
  var listEl = document.getElementById('members-list');
  var loadMoreBtn = document.getElementById('load-more-btn');

  if (!pageAlertEl || !pageAlertTextEl || !statusEl || !listEl || !loadMoreBtn || !window.AdminGuard) {
    return;
  }

  var PAGE_SIZE = 20;
  var state = { page: -1, loadedCount: 0, totalElements: 0, loading: false };
  var currentAdminId = null;

  var ROLE_LABELS = { BUYER: '구매자', SELLER: '판매자', ADMIN: '관리자' };

  function showError(text) {
    pageAlertEl.hidden = false;
    pageAlertTextEl.textContent = text;
  }

  function formatDate(iso) {
    var date = new Date(iso);
    return isNaN(date.getTime()) ? '' : date.toLocaleString('ko-KR');
  }

  function createMemberItem(member) {
    var li = document.createElement('li');
    li.className = 'mypage-list-item';
    li.setAttribute('data-member-id', String(member.memberId));

    var infoEl = document.createElement('div');
    infoEl.className = 'mypage-list-item__info';

    var titleEl = document.createElement('span');
    titleEl.className = 'mypage-list-item__title';
    titleEl.textContent = member.name + ' (' + member.username + ')';
    infoEl.appendChild(titleEl);

    var metaEl = document.createElement('span');
    metaEl.className = 'mypage-list-item__meta';
    var roleLabel = ROLE_LABELS[member.role] || member.role;
    var suspendedLabel = member.suspended ? ' · 정지됨' : '';
    metaEl.textContent = member.email + ' · ' + roleLabel + suspendedLabel + ' · 가입 ' + formatDate(member.createdAt);
    infoEl.appendChild(metaEl);

    li.appendChild(infoEl);

    var actionsEl = document.createElement('div');
    actionsEl.className = 'mypage-list-item__actions';

    if (member.memberId !== currentAdminId) {
      var suspendBtn = document.createElement('button');
      suspendBtn.type = 'button';
      suspendBtn.className = 'btn btn-secondary btn-sm';
      suspendBtn.textContent = member.suspended ? '정지 해제' : '정지';
      suspendBtn.addEventListener('click', function () {
        handleToggleSuspend(member, li, suspendBtn);
      });
      actionsEl.appendChild(suspendBtn);

      var deleteBtn = document.createElement('button');
      deleteBtn.type = 'button';
      deleteBtn.className = 'btn btn-ghost btn-sm';
      deleteBtn.textContent = '삭제';
      deleteBtn.addEventListener('click', function () {
        handleDelete(member, li, deleteBtn);
      });
      actionsEl.appendChild(deleteBtn);
    }

    li.appendChild(actionsEl);
    return li;
  }

  function handleToggleSuspend(member, li, btn) {
    btn.disabled = true;
    var path = '/admin/members/' + member.memberId + (member.suspended ? '/unsuspend' : '/suspend');
    window.Api.post(path, {})
      .then(function () {
        member.suspended = !member.suspended;
        li.replaceWith(createMemberItem(member));
      })
      .catch(function (err) {
        console.error('[admin-members.js] toggle suspend failed:', err);
        window.alert((err && err.message) || '처리에 실패했습니다.');
        btn.disabled = false;
      });
  }

  function handleDelete(member, li, btn) {
    var confirmed = window.confirm('정말 "' + member.name + '"(' + member.username + ') 회원을 삭제하시겠습니까? 되돌릴 수 없습니다.');
    if (!confirmed) {
      return;
    }
    btn.disabled = true;
    window.Api.del('/admin/members/' + member.memberId)
      .then(function () {
        li.remove();
        state.totalElements -= 1;
        state.loadedCount -= 1;
      })
      .catch(function (err) {
        console.error('[admin-members.js] delete failed:', err);
        window.alert((err && err.message) || '삭제에 실패했습니다.');
        btn.disabled = false;
      });
  }

  function updateLoadMoreButton() {
    if (state.loadedCount >= state.totalElements) {
      loadMoreBtn.hidden = true;
      return;
    }
    loadMoreBtn.hidden = false;
    loadMoreBtn.disabled = false;
    loadMoreBtn.textContent = '더 보기';
  }

  function fetchMembers(page) {
    if (state.loading) {
      return;
    }
    state.loading = true;
    if (page === 0) {
      statusEl.hidden = false;
      statusEl.textContent = '불러오는 중...';
    } else {
      loadMoreBtn.disabled = true;
      loadMoreBtn.textContent = '불러오는 중...';
    }

    window.Api.get('/admin/members?page=' + page + '&size=' + PAGE_SIZE)
      .then(function (data) {
        state.page = page;
        state.totalElements = data.totalElements;
        state.loadedCount += data.content.length;

        var fragment = document.createDocumentFragment();
        data.content.forEach(function (member) {
          fragment.appendChild(createMemberItem(member));
        });
        listEl.appendChild(fragment);

        statusEl.hidden = true;
        if (state.loadedCount === 0) {
          statusEl.hidden = false;
          statusEl.textContent = '회원이 없습니다.';
        }
        updateLoadMoreButton();
      })
      .catch(function (err) {
        console.error('[admin-members.js] failed to load members:', err);
        var message = (err && err.message) || '회원 목록을 불러오지 못했습니다.';
        showError(message);
        statusEl.hidden = true;
      })
      .then(function () {
        state.loading = false;
      });
  }

  loadMoreBtn.addEventListener('click', function () {
    fetchMembers(state.page + 1);
  });

  window.AdminGuard.requireAdmin().then(function (member) {
    if (!member) {
      return;
    }
    currentAdminId = member.memberId;
    fetchMembers(0);
  });
})();
