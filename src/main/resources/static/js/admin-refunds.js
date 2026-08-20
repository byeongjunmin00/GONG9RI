/**
 * admin-refunds.js — 환불 요청 현황(admin/refunds.html) 전용 스크립트
 *
 * GET /api/admin/refund-requests로 판매자 범위 없이 전체 환불 요청을 최신순으로 본다. 읽기 전용이라
 * 승인/거절 액션은 없다(그건 각 판매자 마이페이지 몫, seller-mypage.js 참고 — 상태 라벨/배지 매핑은
 * 거기 것과 동일하게 맞춘다).
 */
(function () {
  var pageAlertEl = document.getElementById('page-alert');
  var pageAlertTextEl = document.getElementById('page-alert-text');
  var statusEl = document.getElementById('refunds-status');
  var listEl = document.getElementById('refunds-list');
  var loadMoreBtn = document.getElementById('load-more-btn');
  var statusFilterEl = document.getElementById('status-filter');

  if (!pageAlertEl || !pageAlertTextEl || !statusEl || !listEl || !loadMoreBtn || !statusFilterEl || !window.AdminGuard) {
    return;
  }

  var PAGE_SIZE = 20;
  var state = { page: -1, loadedCount: 0, totalElements: 0, loading: false, status: '' };

  function showError(text) {
    pageAlertEl.hidden = false;
    pageAlertTextEl.textContent = text;
  }

  function formatPrice(value) {
    return typeof value === 'number' ? value.toLocaleString('ko-KR') + '원' : '';
  }

  function statusToBadgeClass(status) {
    if (status === 'APPROVED') {
      return 'badge-success';
    }
    if (status === 'REJECTED') {
      return 'badge-failed';
    }
    return 'badge-recruiting';
  }

  function statusToLabel(status) {
    if (status === 'APPROVED') {
      return '승인됨';
    }
    if (status === 'REJECTED') {
      return '거절됨';
    }
    return '대기중';
  }

  function createRefundItem(request) {
    var li = document.createElement('li');
    li.className = 'mypage-list-item';

    var infoEl = document.createElement('div');
    infoEl.className = 'mypage-list-item__info';

    var titleRowEl = document.createElement('span');
    titleRowEl.className = 'mypage-list-item__title';
    var badgeEl = document.createElement('span');
    badgeEl.className = 'badge ' + statusToBadgeClass(request.status);
    badgeEl.textContent = statusToLabel(request.status);
    titleRowEl.appendChild(badgeEl);
    titleRowEl.appendChild(document.createTextNode(' ' + (request.productName || '')));
    infoEl.appendChild(titleRowEl);

    var metaEl = document.createElement('span');
    metaEl.className = 'mypage-list-item__meta';
    var reasonText = request.reason ? '사유: ' + request.reason : '사유: 참여 취소';
    var requestedAtText = request.requestedAt ? new Date(request.requestedAt).toLocaleString('ko-KR') : '';
    var requesterText = request.requesterName ? '요청자 ' + request.requesterName : '';
    metaEl.textContent = [requesterText, formatPrice(request.amount), reasonText, '요청일 ' + requestedAtText]
      .filter(Boolean).join(' · ');
    infoEl.appendChild(metaEl);

    li.appendChild(infoEl);
    return li;
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

  function fetchRefunds(page) {
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

    var query = '/admin/refund-requests?page=' + page + '&size=' + PAGE_SIZE;
    if (state.status) {
      query += '&status=' + state.status;
    }

    window.Api.get(query)
      .then(function (data) {
        state.page = page;
        state.totalElements = data.totalElements;
        state.loadedCount += data.content.length;

        var fragment = document.createDocumentFragment();
        data.content.forEach(function (request) {
          fragment.appendChild(createRefundItem(request));
        });
        listEl.appendChild(fragment);

        statusEl.hidden = true;
        if (state.loadedCount === 0) {
          statusEl.hidden = false;
          statusEl.textContent = '환불 요청이 없습니다.';
        }
        updateLoadMoreButton();
      })
      .catch(function (err) {
        console.error('[admin-refunds.js] failed to load refund requests:', err);
        var message = (err && err.message) || '환불 요청 목록을 불러오지 못했습니다.';
        showError(message);
        statusEl.hidden = true;
      })
      .then(function () {
        state.loading = false;
      });
  }

  function resetAndReload() {
    state.page = -1;
    state.loadedCount = 0;
    state.totalElements = 0;
    listEl.innerHTML = '';
    fetchRefunds(0);
  }

  loadMoreBtn.addEventListener('click', function () {
    fetchRefunds(state.page + 1);
  });

  statusFilterEl.addEventListener('change', function () {
    state.status = statusFilterEl.value;
    resetAndReload();
  });

  window.AdminGuard.requireAdmin().then(function (member) {
    if (!member) {
      return;
    }
    fetchRefunds(0);
  });
})();
