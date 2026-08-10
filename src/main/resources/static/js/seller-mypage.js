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

  if (
    !pageAlertEl || !pageAlertTextEl || !pageAlertLoginLinkEl || !mypageSectionsEl ||
    !productsStatusEl || !productsListEl ||
    !revenueStatusEl || !revenueCardsEl || !revenueTotalEl || !revenuePaidCountEl || !revenueRefundedCountEl ||
    !teamsStatusEl || !teamsListEl
  ) {
    return;
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
    var deadlineText = team.deadline ? '마감 ' + team.deadline : '';
    metaEl.textContent = [current + ' / ' + max + '명', deadlineText].filter(Boolean).join(' · ');
    infoEl.appendChild(metaEl);

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

  function init() {
    loadProducts();
    loadRevenue();
    loadTeams();
  }

  init();
})();
