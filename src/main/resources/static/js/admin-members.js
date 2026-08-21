/**
 * admin-members.js — 회원 관리(admin/members.html) 전용 스크립트
 *
 * GET /api/admin/members?page=0&size=20&search=...&role=...&suspended=...
 * - 서버 사이드 동적 페이징 및 DB 키워드 검색/역할·상태 필터링 연동
 * - N+1 쿼리 방지 배치 집계 데이터(구매 건수, 공구 참여 수, 등록 상품 수) 렌더링
 * - 정지/정지해제/삭제 액션 후 해당 항목 인플레이스 갱신
 */
(function () {
  var pageAlertEl = document.getElementById('page-alert');
  var pageAlertTextEl = document.getElementById('page-alert-text');
  var statusEl = document.getElementById('members-status');
  var listEl = document.getElementById('members-list');
  var loadMoreBtn = document.getElementById('load-more-btn');
  var searchInputEl = document.getElementById('member-search-input');
  var filterBtns = document.querySelectorAll('.member-filter-btn');

  if (!pageAlertEl || !pageAlertTextEl || !statusEl || !listEl || !loadMoreBtn || !window.AdminGuard) {
    return;
  }

  var PAGE_SIZE = 20;
  var state = { page: -1, loadedCount: 0, totalElements: 0, loading: false };
  var currentAdminId = null;
  var activeFilter = 'ALL';
  var searchQuery = '';
  var searchDebounceTimer = null;

  var ROLE_LABELS = { BUYER: '구매자', SELLER: '판매자', ADMIN: '관리자' };

  function showError(text) {
    pageAlertEl.hidden = false;
    pageAlertTextEl.textContent = text;
  }

  function formatDate(iso) {
    if (!iso) return '';
    var date = new Date(iso);
    return isNaN(date.getTime()) ? '' : date.toLocaleDateString('ko-KR');
  }

  function createAvatarElement() {
    var thumbEl = document.createElement('div');
    thumbEl.className = 'mypage-list-item__thumb';
    thumbEl.innerHTML = '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path><circle cx="12" cy="7" r="4"></circle></svg>';
    return thumbEl;
  }

  function createMemberItem(member) {
    var li = document.createElement('li');
    li.className = 'mypage-list-item';
    li.setAttribute('data-member-id', String(member.memberId));

    var mainEl = document.createElement('div');
    mainEl.className = 'mypage-list-item__main';

    var thumbEl = createAvatarElement();
    mainEl.appendChild(thumbEl);

    var infoEl = document.createElement('div');
    infoEl.className = 'mypage-list-item__info';

    var titleEl = document.createElement('span');
    titleEl.className = 'mypage-list-item__title';
    titleEl.textContent = member.name + ' (' + member.username + ')';
    infoEl.appendChild(titleEl);

    var metaEl = document.createElement('span');
    metaEl.className = 'mypage-list-item__meta';
    metaEl.textContent = member.email + ' · 가입일 ' + formatDate(member.createdAt);
    infoEl.appendChild(metaEl);

    var metricsEl = document.createElement('div');
    metricsEl.style.display = 'flex';
    metricsEl.style.gap = 'var(--space-2)';
    metricsEl.style.flexWrap = 'wrap';
    metricsEl.style.marginTop = 'var(--space-2)';

    var roleBadge = document.createElement('span');
    roleBadge.className = 'badge ' + (member.role === 'SELLER' ? 'badge-primary' : member.role === 'ADMIN' ? 'badge-brand' : 'badge-secondary');
    roleBadge.textContent = ROLE_LABELS[member.role] || member.role;
    metricsEl.appendChild(roleBadge);

    var statusBadge = document.createElement('span');
    if (member.suspended) {
      statusBadge.className = 'badge badge-failed';
      statusBadge.textContent = '⚠️ 정지됨';
    } else {
      statusBadge.className = 'badge badge-success';
      statusBadge.textContent = '정상 계정';
    }
    metricsEl.appendChild(statusBadge);

    var purchaseBadge = document.createElement('span');
    purchaseBadge.className = 'badge';
    purchaseBadge.style.background = 'var(--color-surface-alt)';
    purchaseBadge.style.color = 'var(--color-text)';
    purchaseBadge.textContent = '🛍️ 구매 ' + (member.purchaseCount || 0) + '건';
    metricsEl.appendChild(purchaseBadge);

    var teamBadge = document.createElement('span');
    teamBadge.className = 'badge';
    teamBadge.style.background = 'var(--color-surface-alt)';
    teamBadge.style.color = 'var(--color-text)';
    teamBadge.textContent = '👥 공구 ' + (member.teamCount || 0) + '건';
    metricsEl.appendChild(teamBadge);

    if (member.role === 'SELLER') {
      var productBadge = document.createElement('span');
      productBadge.className = 'badge';
      productBadge.style.background = 'var(--color-surface-alt)';
      productBadge.style.color = 'var(--color-text)';
      productBadge.textContent = '📦 상품 ' + (member.productCount || 0) + '개';
      metricsEl.appendChild(productBadge);
    }

    infoEl.appendChild(metricsEl);
    mainEl.appendChild(infoEl);
    li.appendChild(mainEl);

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

  function clearChildren(parent) {
    while (parent.firstChild) {
      parent.removeChild(parent.firstChild);
    }
  }

  function handleToggleSuspend(member, li, btn) {
    btn.disabled = true;
    var path = '/admin/members/' + member.memberId + (member.suspended ? '/unsuspend' : '/suspend');
    window.Api.post(path, {})
      .then(function () {
        member.suspended = !member.suspended;
        var newLi = createMemberItem(member);
        li.replaceWith(newLi);
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
        updateLoadMoreButton();
      })
      .catch(function (err) {
        console.error('[admin-members.js] delete failed:', err);
        window.alert((err && err.message) || '삭제에 실패했습니다. 활동 이력이 있는 회원은 삭제할 수 없으므로 [정지]를 이용해주세요.');
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

  function fetchMembers(page, reset) {
    if (state.loading) {
      return;
    }
    state.loading = true;

    if (reset) {
      clearChildren(listEl);
      state.page = -1;
      state.loadedCount = 0;
      state.totalElements = 0;
    }

    if (page === 0) {
      statusEl.hidden = false;
      statusEl.textContent = '회원 목록을 불러오는 중입니다...';
    } else {
      loadMoreBtn.disabled = true;
      loadMoreBtn.textContent = '불러오는 중...';
    }

    // 서버 사이드 페이징/검색/필터 쿼리 스트링 구성
    var queryParams = ['page=' + page, 'size=' + PAGE_SIZE];
    if (searchQuery) {
      queryParams.push('search=' + encodeURIComponent(searchQuery));
    }
    if (activeFilter === 'BUYER' || activeFilter === 'SELLER') {
      queryParams.push('role=' + activeFilter);
    } else if (activeFilter === 'SUSPENDED') {
      queryParams.push('suspended=true');
    }

    var path = '/admin/members?' + queryParams.join('&');

    window.Api.get(path)
      .then(function (data) {
        state.page = page;
        state.totalElements = data.totalElements;
        state.loadedCount += (data.content || []).length;

        var fragment = document.createDocumentFragment();
        (data.content || []).forEach(function (member) {
          fragment.appendChild(createMemberItem(member));
        });
        listEl.appendChild(fragment);

        statusEl.hidden = true;
        if (state.loadedCount === 0) {
          statusEl.hidden = false;
          statusEl.textContent = '조건에 해당하는 회원이 없습니다.';
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

  // 필터 탭 클릭 이벤트 (서버 사이드 재조회)
  filterBtns.forEach(function (btn) {
    btn.addEventListener('click', function () {
      filterBtns.forEach(function (b) {
        b.classList.remove('active', 'btn-primary');
        b.classList.add('btn-secondary');
      });
      btn.classList.add('active', 'btn-primary');
      btn.classList.remove('btn-secondary');
      activeFilter = btn.getAttribute('data-filter') || 'ALL';
      fetchMembers(0, true);
    });
  });

  // 검색창 디바운스 입력 이벤트 (서버 사이드 재조회)
  if (searchInputEl) {
    searchInputEl.addEventListener('input', function () {
      if (searchDebounceTimer) {
        clearTimeout(searchDebounceTimer);
      }
      searchDebounceTimer = setTimeout(function () {
        searchQuery = searchInputEl.value.trim();
        fetchMembers(0, true);
      }, 300);
    });
  }

  loadMoreBtn.addEventListener('click', function () {
    fetchMembers(state.page + 1, false);
  });

  window.AdminGuard.requireAdmin().then(function (member) {
    if (!member) {
      return;
    }
    currentAdminId = member.memberId;
    fetchMembers(0, true);
  });
})();
