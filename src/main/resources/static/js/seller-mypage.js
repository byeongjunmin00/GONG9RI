/**
 * seller-mypage.js — 판매자 마이페이지(seller/mypage.html) 전용 스크립트
 *
 * 이 페이지는 static/ 루트가 아닌 서브디렉토리(/seller/mypage.html)에 있으므로,
 * 페이지 이동(수정 페이지 등)은 반드시 절대경로("/seller/products/edit.html?id=...")를 쓴다.
 *
 * - 로드 시 세 API를 각각 호출한다: GET /api/seller/mypage/products, /revenue, /teams.
 *   로그인 상태를 사전에 확인하지 않는 기존 원칙과 동일하다.
 * - 셋 중 하나라도 401(UNAUTHORIZED)이면 페이지 상단 공통 배너(#page-alert)에 로그인 안내 +
 *   로그인 링크를 띄우고, 세 섹션 전체를 숨긴다(서버가 신뢰 SSOT).
 * - 403(FORBIDDEN, 구매자 계정)/기타 에러는 해당 섹션의 상태 영역에 서버 message를 표시하고
 *   다른 섹션은 각자 독립적으로 계속 로드된다(한 섹션의 실패가 페이지 전체를 깨지 않는다).
 * - 상품 목록의 "수정"은 seller/products/edit.html?id={productId}로 이동(API 호출 없음).
 * - 상품 목록의 "삭제"는 confirm 확인 후 DELETE /api/products/{productId} 호출 →
 *   성공(204) 시 목록에서 해당 항목만 제거, 실패는 목록을 유지하고 상태 영역에 안내한다.
 * - 공구 참여 현황의 상태 뱃지/라벨은 js/product.js의 매핑(RECRUITING/SUCCESS/FAILED)과 동일하게 맞춘다.
 * - 환불 요청 관리: GET /api/seller/mypage/refund-requests로 목록을 불러와 PENDING 항목에만
 *   승인/거절 액션을 노출한다. 승인은 즉시 POST .../approve, 거절은 사유 템플릿(select)을 고른 뒤
 *   POST .../reject로 확정한다(자유 텍스트 아님, docs/api/refund.md). 성공하면 그 항목만 다시 그려
 *   전체 목록을 재조회하지 않는다.
 * - 서버 응답 문자열(에러 message 등)은 textContent로만 대입해 XSS를 방지한다.
 */
(function () {
  var pageAlertEl = document.getElementById('page-alert');
  var pageAlertTextEl = document.getElementById('page-alert-text');
  var pageAlertLoginLinkEl = document.getElementById('page-alert-login-link');

  var mypageSectionsEl = document.getElementById('mypage-sections');

  var productsStatusEl = document.getElementById('products-status');
  var productsListEl = document.getElementById('products-list');

  var revenueStatusEl = document.getElementById('revenue-status');
  var revenueCardsEl = document.getElementById('revenue-cards');
  var revenueTotalEl = document.getElementById('revenue-total');
  var revenuePaidCountEl = document.getElementById('revenue-paid-count');
  var revenueRefundedCountEl = document.getElementById('revenue-refunded-count');

  var teamsStatusEl = document.getElementById('teams-status');
  var teamsListEl = document.getElementById('teams-list');

  var refundRequestsStatusEl = document.getElementById('refund-requests-status');
  var refundRequestsListEl = document.getElementById('refund-requests-list');

  if (
    !pageAlertEl || !pageAlertTextEl || !pageAlertLoginLinkEl || !mypageSectionsEl ||
    !productsStatusEl || !productsListEl ||
    !revenueStatusEl || !revenueCardsEl || !revenueTotalEl || !revenuePaidCountEl || !revenueRefundedCountEl ||
    !teamsStatusEl || !teamsListEl ||
    !refundRequestsStatusEl || !refundRequestsListEl
  ) {
    return;
  }

  // 서버가 주는 LocalDateTime은 "2026-08-27T19:52:25.05242"처럼 마이크로초까지 붙은 ISO 문자열이라
  // 그대로 화면에 쓰면 날것으로 보인다(2026-08-20 사용자 리포트). 다른 화면(admin-members.js,
  // header-notifications.js)과 같은 방식으로 한국 표기로 바꾼다.
  function formatDateTime(value) {
    if (!value) {
      return '';
    }
    var date = new Date(value);
    return isNaN(date.getTime()) ? '' : date.toLocaleString('ko-KR');
  }

  function formatPrice(value) {
    if (typeof value !== 'number') {
      return '';
    }
    return value.toLocaleString('ko-KR') + '원';
  }

  function clearChildren(el) {
    while (el.firstChild) {
      el.removeChild(el.firstChild);
    }
  }

  function showPageAlert(text, showLoginLink) {
    pageAlertEl.hidden = false;
    pageAlertEl.className = 'form-alert form-alert--error';
    pageAlertTextEl.textContent = text;
    pageAlertLoginLinkEl.hidden = !showLoginLink;
    if (showLoginLink) {
      pageAlertLoginLinkEl.href = '/login.html?redirect=' + encodeURIComponent(window.location.pathname + window.location.search);
    }
    mypageSectionsEl.hidden = true;
    pageAlertEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  function showStatus(el, text, variant) {
    el.hidden = false;
    el.textContent = text;
    el.className = 'product-status product-status--' + variant;
  }

  function hideStatus(el) {
    el.hidden = true;
    el.textContent = '';
  }

  function statusToBadgeClass(status) {
    if (status === 'SUCCESS') {
      return 'badge-success';
    }
    if (status === 'FAILED') {
      return 'badge-failed';
    }
    return 'badge-recruiting';
  }

  function statusToLabel(status) {
    if (status === 'SUCCESS') {
      return '모집완료';
    }
    if (status === 'FAILED') {
      return '모집실패';
    }
    return '모집중';
  }

  /**
   * 401(UNAUTHORIZED)이면 true를 반환하고 공통 로그인 배너를 띄운다.
   * 호출부는 true가 반환되면 그 섹션 자체의 에러 렌더링을 생략한다(배너가 대신한다).
   */
  function handleUnauthorized(err) {
    var status = err && err.status;
    var code = err && err.code;
    if (status === 401 || code === 'UNAUTHORIZED') {
      showPageAlert('로그인이 필요합니다.', true);
      return true;
    }
    return false;
  }

  // ---------- 등록 상품 목록 ----------

  function createProductItem(product) {
    var li = document.createElement('li');
    li.className = 'mypage-list-item';
    li.setAttribute('data-product-id', String(product.productId));

    var infoEl = document.createElement('div');
    infoEl.className = 'mypage-list-item__info';

    var nameEl = document.createElement('span');
    nameEl.className = 'mypage-list-item__title';
    nameEl.textContent = product.name || '';
    infoEl.appendChild(nameEl);

    var metaEl = document.createElement('span');
    metaEl.className = 'mypage-list-item__meta';
    var basePriceText = formatPrice(product.basePrice);
    var maxParticipantsText =
      typeof product.maxParticipants === 'number' ? product.maxParticipants + '명 정원' : '';
    metaEl.textContent = [basePriceText, maxParticipantsText].filter(Boolean).join(' · ');
    infoEl.appendChild(metaEl);

    li.appendChild(infoEl);

    var actionsEl = document.createElement('div');
    actionsEl.className = 'mypage-list-item__actions';

    var editLink = document.createElement('a');
    editLink.className = 'btn btn-secondary btn-sm';
    editLink.href = '/seller/products/edit.html?id=' + product.productId;
    editLink.textContent = '수정';
    actionsEl.appendChild(editLink);

    var deleteBtn = document.createElement('button');
    deleteBtn.type = 'button';
    deleteBtn.className = 'btn btn-ghost btn-sm';
    deleteBtn.textContent = '삭제';
    deleteBtn.addEventListener('click', function () {
      handleDeleteProduct(product.productId, li, deleteBtn);
    });
    actionsEl.appendChild(deleteBtn);

    li.appendChild(actionsEl);

    return li;
  }

  function renderProducts(products) {
    clearChildren(productsListEl);
    var fragment = document.createDocumentFragment();
    products.forEach(function (product) {
      fragment.appendChild(createProductItem(product));
    });
    productsListEl.appendChild(fragment);
  }

  function handleDeleteProduct(productId, itemEl, deleteBtn) {
    var confirmed = window.confirm('정말 이 상품을 삭제하시겠습니까? 삭제하면 되돌릴 수 없습니다.');
    if (!confirmed) {
      return;
    }

    hideStatus(productsStatusEl);
    deleteBtn.disabled = true;

    window.Api.del('/products/' + productId)
      .then(function () {
        productsListEl.removeChild(itemEl);
        if (productsListEl.children.length === 0) {
          showStatus(productsStatusEl, '등록된 상품이 없습니다.', 'empty');
        }
      })
      .catch(function (err) {
        console.error('[seller-mypage.js] failed to delete product:', err);
        deleteBtn.disabled = false;

        if (handleUnauthorized(err)) {
          return;
        }

        var message = (err && err.message) || '상품 삭제에 실패했습니다. 잠시 후 다시 시도해주세요.';
        showStatus(productsStatusEl, message, 'error');
      });
  }

  function loadProducts() {
    showStatus(productsStatusEl, '등록 상품 목록을 불러오는 중입니다...', 'loading');
    clearChildren(productsListEl);

    return window.Api.get('/seller/mypage/products')
      .then(function (products) {
        var list = Array.isArray(products) ? products : [];
        if (list.length === 0) {
          showStatus(productsStatusEl, '등록된 상품이 없습니다.', 'empty');
          return;
        }
        hideStatus(productsStatusEl);
        renderProducts(list);
      })
      .catch(function (err) {
        console.error('[seller-mypage.js] failed to load products:', err);
        if (handleUnauthorized(err)) {
          return;
        }
        var message = (err && err.message) || '등록 상품 목록을 불러오지 못했습니다.';
        showStatus(productsStatusEl, message, 'error');
      });
  }

  // ---------- 수익 현황 ----------

  function renderRevenue(revenue) {
    revenueTotalEl.textContent = formatPrice(revenue.totalRevenue);
    revenuePaidCountEl.textContent =
      typeof revenue.paidCount === 'number' ? revenue.paidCount + '건' : '';
    revenueRefundedCountEl.textContent =
      typeof revenue.refundedCount === 'number' ? revenue.refundedCount + '건' : '';
    revenueCardsEl.hidden = false;
  }

  function loadRevenue() {
    showStatus(revenueStatusEl, '수익 현황을 불러오는 중입니다...', 'loading');
    revenueCardsEl.hidden = true;

    return window.Api.get('/seller/mypage/revenue')
      .then(function (revenue) {
        hideStatus(revenueStatusEl);
        renderRevenue(revenue || {});
      })
      .catch(function (err) {
        console.error('[seller-mypage.js] failed to load revenue:', err);
        if (handleUnauthorized(err)) {
          return;
        }
        var message = (err && err.message) || '수익 현황을 불러오지 못했습니다.';
        showStatus(revenueStatusEl, message, 'error');
      });
  }

  // ---------- 공구 참여 현황 ----------

  function createTeamItem(team) {
    var li = document.createElement('li');
    li.className = 'mypage-list-item';

    var infoEl = document.createElement('div');
    infoEl.className = 'mypage-list-item__info';

    var titleRow = document.createElement('span');
    titleRow.className = 'mypage-list-item__title';
    titleRow.textContent = team.productName || '';
    infoEl.appendChild(titleRow);

    var metaEl = document.createElement('span');
    metaEl.className = 'mypage-list-item__meta';
    var current = typeof team.currentCount === 'number' ? team.currentCount : '?';
    var max = typeof team.maxParticipants === 'number' ? team.maxParticipants : '?';
    var deadlineText = team.deadline ? '마감 ' + formatDateTime(team.deadline) : '';
    metaEl.textContent = [current + ' / ' + max + '명', deadlineText].filter(Boolean).join(' · ');
    infoEl.appendChild(metaEl);

    // 누가 참여했는지 — 인원 수만 보여서 판매자가 알 수 없던 부분(2026-08-20 사용자 리포트).
    var participants = Array.isArray(team.participantNames) ? team.participantNames : [];
    var participantsEl = document.createElement('span');
    participantsEl.className = 'mypage-list-item__meta';
    if (participants.length) {
      participantsEl.textContent = '참여 ' + participants.join(', ')
        + (team.leaderName ? ' · 팀장 ' + team.leaderName : '');
    } else {
      participantsEl.textContent = team.leaderName ? '팀장 ' + team.leaderName : '';
    }
    if (participantsEl.textContent) {
      infoEl.appendChild(participantsEl);
    }

    li.appendChild(infoEl);

    var badgeEl = document.createElement('span');
    badgeEl.className = 'badge ' + statusToBadgeClass(team.status);
    badgeEl.textContent = statusToLabel(team.status);
    li.appendChild(badgeEl);

    return li;
  }

  function renderTeams(teams) {
    clearChildren(teamsListEl);
    var fragment = document.createDocumentFragment();
    teams.forEach(function (team) {
      fragment.appendChild(createTeamItem(team));
    });
    teamsListEl.appendChild(fragment);
  }

  function loadTeams() {
    showStatus(teamsStatusEl, '공구 참여 현황을 불러오는 중입니다...', 'loading');
    clearChildren(teamsListEl);

    return window.Api.get('/seller/mypage/teams')
      .then(function (teams) {
        var list = Array.isArray(teams) ? teams : [];
        if (list.length === 0) {
          showStatus(teamsStatusEl, '아직 개설된 공구팀이 없습니다.', 'empty');
          return;
        }
        hideStatus(teamsStatusEl);
        renderTeams(list);
      })
      .catch(function (err) {
        console.error('[seller-mypage.js] failed to load teams:', err);
        if (handleUnauthorized(err)) {
          return;
        }
        var message = (err && err.message) || '공구 참여 현황을 불러오지 못했습니다.';
        showStatus(teamsStatusEl, message, 'error');
      });
  }

  // ---------- 환불 요청 관리 ----------

  var REJECTION_REASON_OPTIONS = [
    { value: 'ALREADY_SHIPPED', label: '상품이 이미 발송됨' },
    { value: 'ALREADY_USED', label: '이미 사용/소비된 상품' },
    { value: 'POLICY_VIOLATION', label: '환불 정책 위반' },
    { value: 'OTHER', label: '기타(판매자 사정)' },
  ];

  function refundRequestStatusToBadgeClass(status) {
    if (status === 'APPROVED') {
      return 'badge-success';
    }
    if (status === 'REJECTED') {
      return 'badge-failed';
    }
    return 'badge-recruiting';
  }

  function refundRequestStatusToLabel(status) {
    if (status === 'APPROVED') {
      return '승인됨';
    }
    if (status === 'REJECTED') {
      return '거절됨';
    }
    return '대기중';
  }

  function refundRequestMetaText(request) {
    var amountText = formatPrice(request.amount);
    var requesterText = request.requesterName ? '요청자 ' + request.requesterName : '';
    var requestedAtText = request.requestedAt ? '요청일 ' + formatDateTime(request.requestedAt) : '';
    var reasonText = request.reason ? '사유: ' + request.reason : '사유: 참여 취소';
    var rejectionText = request.status === 'REJECTED' && request.rejectionReason
      ? '거절 사유: ' + request.rejectionReason
      : '';
    return [requesterText, amountText, requestedAtText, reasonText, rejectionText]
      .filter(Boolean).join(' · ');
  }

  function createRejectPanel(request, li, badgeEl, metaEl, actionsEl) {
    var panel = document.createElement('div');
    panel.className = 'refund-reject-panel';
    panel.hidden = true;

    var select = document.createElement('select');
    select.className = 'form-select';
    REJECTION_REASON_OPTIONS.forEach(function (option) {
      var optionEl = document.createElement('option');
      optionEl.value = option.value;
      optionEl.textContent = option.label;
      select.appendChild(optionEl);
    });
    panel.appendChild(select);

    var confirmBtn = document.createElement('button');
    confirmBtn.type = 'button';
    confirmBtn.className = 'btn btn-secondary btn-sm';
    confirmBtn.textContent = '거절 확정';
    confirmBtn.addEventListener('click', function () {
      confirmBtn.disabled = true;
      window.Api.post('/refund-requests/' + request.refundRequestId + '/reject', {
        rejectionReason: select.value,
      })
        .then(function (updated) {
          applyRefundRequestUpdate(updated, li, badgeEl, metaEl, actionsEl);
        })
        .catch(function (err) {
          console.error('[seller-mypage.js] failed to reject refund request:', err);
          confirmBtn.disabled = false;
          if (handleUnauthorized(err)) {
            return;
          }
          var message = (err && err.message) || '환불 요청 거절에 실패했습니다.';
          showStatus(refundRequestsStatusEl, message, 'error');
        });
    });
    panel.appendChild(confirmBtn);

    var cancelBtn = document.createElement('button');
    cancelBtn.type = 'button';
    cancelBtn.className = 'btn btn-ghost btn-sm';
    cancelBtn.textContent = '취소';
    cancelBtn.addEventListener('click', function () {
      panel.hidden = true;
    });
    panel.appendChild(cancelBtn);

    return panel;
  }

  /**
   * 승인/거절 성공 응답으로 그 항목만 다시 그린다(전체 목록 재조회 없음).
   */
  function applyRefundRequestUpdate(request, li, badgeEl, metaEl, actionsEl) {
    badgeEl.className = 'badge ' + refundRequestStatusToBadgeClass(request.status);
    badgeEl.textContent = refundRequestStatusToLabel(request.status);
    metaEl.textContent = refundRequestMetaText(request);
    clearChildren(actionsEl);
    actionsEl.appendChild(badgeEl);
  }

  function createRefundRequestItem(request) {
    var li = document.createElement('li');
    li.className = 'mypage-list-item';

    var infoEl = document.createElement('div');
    infoEl.className = 'mypage-list-item__info';

    var titleEl = document.createElement('span');
    titleEl.className = 'mypage-list-item__title';
    titleEl.textContent = request.productName || '';
    infoEl.appendChild(titleEl);

    var metaEl = document.createElement('span');
    metaEl.className = 'mypage-list-item__meta';
    metaEl.textContent = refundRequestMetaText(request);
    infoEl.appendChild(metaEl);

    li.appendChild(infoEl);

    var badgeEl = document.createElement('span');
    badgeEl.className = 'badge ' + refundRequestStatusToBadgeClass(request.status);
    badgeEl.textContent = refundRequestStatusToLabel(request.status);

    var actionsEl = document.createElement('div');
    actionsEl.className = 'mypage-list-item__actions';

    if (request.status !== 'PENDING') {
      actionsEl.appendChild(badgeEl);
      li.appendChild(actionsEl);
      return li;
    }

    actionsEl.appendChild(badgeEl);

    var approveBtn = document.createElement('button');
    approveBtn.type = 'button';
    approveBtn.className = 'btn btn-primary btn-sm';
    approveBtn.textContent = '승인';
    approveBtn.addEventListener('click', function () {
      approveBtn.disabled = true;
      window.Api.post('/refund-requests/' + request.refundRequestId + '/approve')
        .then(function (updated) {
          applyRefundRequestUpdate(updated, li, badgeEl, metaEl, actionsEl);
        })
        .catch(function (err) {
          console.error('[seller-mypage.js] failed to approve refund request:', err);
          approveBtn.disabled = false;
          if (handleUnauthorized(err)) {
            return;
          }
          var message = (err && err.message) || '환불 요청 승인에 실패했습니다.';
          showStatus(refundRequestsStatusEl, message, 'error');
        });
    });
    actionsEl.appendChild(approveBtn);

    var rejectBtn = document.createElement('button');
    rejectBtn.type = 'button';
    rejectBtn.className = 'btn btn-ghost btn-sm';
    rejectBtn.textContent = '거절';
    var rejectPanel = createRejectPanel(request, li, badgeEl, metaEl, actionsEl);
    rejectBtn.addEventListener('click', function () {
      rejectPanel.hidden = !rejectPanel.hidden;
    });
    actionsEl.appendChild(rejectBtn);

    li.appendChild(actionsEl);
    li.appendChild(rejectPanel);

    return li;
  }

  function renderRefundRequests(requests) {
    clearChildren(refundRequestsListEl);
    var fragment = document.createDocumentFragment();
    requests.forEach(function (request) {
      fragment.appendChild(createRefundRequestItem(request));
    });
    refundRequestsListEl.appendChild(fragment);
  }

  function loadRefundRequests() {
    showStatus(refundRequestsStatusEl, '환불 요청 목록을 불러오는 중입니다...', 'loading');
    clearChildren(refundRequestsListEl);

    return window.Api.get('/seller/mypage/refund-requests')
      .then(function (requests) {
        var list = Array.isArray(requests) ? requests : [];
        if (list.length === 0) {
          showStatus(refundRequestsStatusEl, '아직 접수된 환불 요청이 없습니다.', 'empty');
          return;
        }
        hideStatus(refundRequestsStatusEl);
        renderRefundRequests(list);
      })
      .catch(function (err) {
        console.error('[seller-mypage.js] failed to load refund requests:', err);
        if (handleUnauthorized(err)) {
          return;
        }
        var message = (err && err.message) || '환불 요청 목록을 불러오지 못했습니다.';
        showStatus(refundRequestsStatusEl, message, 'error');
      });
  }

  function init() {
    loadProducts();
    loadRevenue();
    loadTeams();
    loadRefundRequests();
  }

  init();
})();
