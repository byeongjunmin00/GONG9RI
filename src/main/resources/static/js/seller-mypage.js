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
 * - 상단 프로필 카드에 판매자 이름/이메일과 수익 KPI(총 매출·결제 완료·환불 건수·대기 환불)를 노출한다.
 *   수익 KPI는 /revenue 응답으로, 대기 환불 건수는 /refund-requests 응답으로 채운다.
 * - 탭 네비게이션: [전체 현황], [등록 상품], [공구 현황], [환불 관리], [계정 설정] 탭 스위칭 및
 *   URL hash(#products 등) 연동을 지원한다.
 * - 상품 목록의 "수정"은 seller/products/edit.html?id={productId}로 이동(API 호출 없음).
 * - 상품 목록의 "삭제"는 confirm 확인 후 DELETE /api/products/{productId} 호출 →
 *   성공(204) 시 목록에서 해당 항목만 제거, 실패는 목록을 유지하고 상태 영역에 안내한다.
 * - 공구 참여 현황의 상태 뱃지/라벨은 js/product.js의 매핑(RECRUITING/SUCCESS/FAILED)과 동일하게 맞춘다.
 *   RECRUITING 팀에는 인원 달성률 프로그레스 바(.team-progress)와 잔여 시간 배지(.badge-time)를 노출한다.
 * - 환불 요청 관리: GET /api/seller/mypage/refund-requests로 목록을 불러와 PENDING 항목에만
 *   승인/거절 액션을 노출한다. 승인은 즉시 POST .../approve, 거절은 사유 템플릿(select)을 고른 뒤
 *   POST .../reject로 확정한다(자유 텍스트 아님, docs/api/refund.md). 성공하면 그 항목만 다시 그려
 *   전체 목록을 재조회하지 않는다.
 * - 환불 요청 카드는 요청자명을 타이틀 라인으로 분리하고 금액·날짜·사유를 메타 라인으로 구분한다.
 * - 서버 응답 문자열(에러 message 등)은 textContent로만 대입해 XSS를 방지한다.
 */
(function () {
  var pageAlertEl = document.getElementById('page-alert');
  var pageAlertTextEl = document.getElementById('page-alert-text');
  var pageAlertLoginLinkEl = document.getElementById('page-alert-login-link');

  var mypageSectionsEl = document.getElementById('mypage-sections');

  // 상단 프로필 카드 요소
  var summaryUserNameEl = document.getElementById('summary-user-name');
  var summaryUserEmailEl = document.getElementById('summary-user-email');
  var summaryRevenueTotalEl = document.getElementById('summary-revenue-total');
  var summaryRevenuePaidCountEl = document.getElementById('summary-revenue-paid-count');
  var summaryRevenueRefundedCountEl = document.getElementById('summary-revenue-refunded-count');
  var summaryPendingRefundsCountEl = document.getElementById('summary-pending-refunds-count');

  // 탭 요소
  var tabBtns = document.querySelectorAll('.mypage-tab-btn');
  var tabPanels = document.querySelectorAll('.mypage-tab-panel');
  var summaryCards = document.querySelectorAll('.summary-card[data-tab]');

  var productsStatusEl = document.getElementById('products-status');
  var productsListEl = document.getElementById('products-list');

  var ordersStatusEl = document.getElementById('orders-status');
  var ordersListEl = document.getElementById('orders-list');

  var teamsStatusEl = document.getElementById('teams-status');
  var teamsListEl = document.getElementById('teams-list');

  var refundRequestsStatusEl = document.getElementById('refund-requests-status');
  var refundRequestsListEl = document.getElementById('refund-requests-list');

  if (
    !pageAlertEl || !pageAlertTextEl || !pageAlertLoginLinkEl || !mypageSectionsEl ||
    !productsStatusEl || !productsListEl ||
    !ordersStatusEl || !ordersListEl ||
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

  /**
   * deadline(ISO 문자열) 기준 남은 유지 기간을 사람이 읽을 수 있는 문구로 변환한다.
   * @param {string} deadline
   * @returns {string} 예: "2일 3시간 남음" / "40분 남음" / "마감 임박"
   */
  function formatRemaining(deadline) {
    if (!deadline) {
      return '';
    }
    var deadlineTime = new Date(deadline).getTime();
    if (isNaN(deadlineTime)) {
      return '';
    }
    var diffMs = deadlineTime - Date.now();
    if (diffMs <= 0) {
      return '마감 임박';
    }
    var diffMinutes = Math.floor(diffMs / 60000);
    var days = Math.floor(diffMinutes / (60 * 24));
    var hours = Math.floor((diffMinutes % (60 * 24)) / 60);
    var minutes = diffMinutes % 60;
    if (days > 0) {
      return days + '일 ' + hours + '시간 남음';
    }
    if (hours > 0) {
      return hours + '시간 ' + minutes + '분 남음';
    }
    return minutes + '분 남음';
  }

  // ---------- 탭 네비게이션 ----------

  function switchTab(targetTab) {
    if (!targetTab) targetTab = 'all';

    tabBtns.forEach(function (btn) {
      var isTarget = btn.getAttribute('data-tab') === targetTab;
      btn.classList.toggle('active', isTarget);
      btn.setAttribute('aria-selected', isTarget ? 'true' : 'false');
    });

    tabPanels.forEach(function (panel) {
      var panelTab = panel.getAttribute('data-tab-panel');
      panel.hidden = targetTab !== 'all' && panelTab !== targetTab;
    });

    if (history.replaceState) {
      history.replaceState(null, '', '#' + targetTab);
    }
  }

  function setupTabs() {
    tabBtns.forEach(function (btn) {
      btn.addEventListener('click', function () {
        switchTab(btn.getAttribute('data-tab'));
      });
    });

    summaryCards.forEach(function (card) {
      card.addEventListener('click', function () {
        switchTab(card.getAttribute('data-tab'));
      });
    });

    var hash = window.location.hash.replace('#', '');
    var validTabs = ['all', 'products', 'teams', 'refunds', 'account'];
    switchTab(validTabs.indexOf(hash) !== -1 ? hash : 'all');
  }

  function loadProfileInfo() {
    return window.Api.get('/auth/me')
      .then(function (user) {
        if (!user) return;
        if (summaryUserNameEl) summaryUserNameEl.textContent = user.name || '판매자';
        if (summaryUserEmailEl) summaryUserEmailEl.textContent = user.email || '';
        // 프로필 사진(member/profile-image 노출). 컨테이너가 이미 원형·크기를 갖고 있어 fill을 쓴다.
        window.Avatar.fill(document.querySelector('.mypage-profile__avatar'),
            user.name, user.profileImageUrl);
      })
      .catch(function (err) {
        if (handleUnauthorized(err)) return;
        // 비로그인이 아니면 기본 텍스트 유지
      });
  }

  // ---------- UI 헬퍼: 썸네일 + 메인 레이아웃 래퍼 ----------

  function createThumbnailElement(imageUrl, altText) {
    var thumbEl = document.createElement('div');
    thumbEl.className = 'mypage-list-item__thumb';

    if (imageUrl) {
      var img = document.createElement('img');
      img.src = imageUrl;
      img.alt = altText || '상품 이미지';
      img.onerror = function () {
        thumbEl.innerHTML = '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><circle cx="8.5" cy="8.5" r="1.5"></circle><polyline points="21 15 16 10 5 21"></polyline></svg>';
      };
      thumbEl.appendChild(img);
    } else {
      thumbEl.innerHTML = '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><circle cx="8.5" cy="8.5" r="1.5"></circle><polyline points="21 15 16 10 5 21"></polyline></svg>';
    }

    return thumbEl;
  }

  /**
   * 썸네일 + 정보(infoEl)를 묶는 .mypage-list-item__main 래퍼를 생성한다.
   * 구매자 마이페이지와 동일한 헬퍼 패턴.
   */
  function createListItemMainWrapper(imageUrl, altText, infoEl) {
    var mainEl = document.createElement('div');
    mainEl.className = 'mypage-list-item__main';
    mainEl.appendChild(createThumbnailElement(imageUrl, altText));
    mainEl.appendChild(infoEl);
    return mainEl;
  }

  function createTeamProgressBarElement(currentCount, maxParticipants) {
    var progressEl = document.createElement('div');
    progressEl.className = 'team-progress';

    var current = typeof currentCount === 'number' ? currentCount : 0;
    var max = typeof maxParticipants === 'number' && maxParticipants > 0 ? maxParticipants : 1;
    var percent = Math.min(100, Math.round((current / max) * 100));

    var trackEl = document.createElement('div');
    trackEl.className = 'team-progress__track';

    var fillEl = document.createElement('div');
    fillEl.className = 'team-progress__fill';
    fillEl.style.width = percent + '%';
    trackEl.appendChild(fillEl);

    var textEl = document.createElement('div');
    textEl.className = 'team-progress__text';
    textEl.innerHTML = '<span>달성 인원</span><span><strong>' + current + '</strong> / ' + max + '명 (' + percent + '%)</span>';

    progressEl.appendChild(trackEl);
    progressEl.appendChild(textEl);

    return progressEl;
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

    var mainEl = createListItemMainWrapper(product.imageUrl, product.name, infoEl);
    li.appendChild(mainEl);

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

  // ---------- 수익 현황 (상단 프로필 KPI 카드로 표시) ----------

  function renderRevenue(revenue) {
    if (summaryRevenueTotalEl) {
      summaryRevenueTotalEl.textContent = formatPrice(revenue.totalRevenue) || '0원';
    }
    if (summaryRevenuePaidCountEl) {
      summaryRevenuePaidCountEl.textContent =
        typeof revenue.paidCount === 'number' ? revenue.paidCount + '건' : '0건';
    }
    if (summaryRevenueRefundedCountEl) {
      summaryRevenueRefundedCountEl.textContent =
        typeof revenue.refundedCount === 'number' ? revenue.refundedCount + '건' : '0건';
    }
  }

  function loadRevenue() {
    return window.Api.get('/seller/mypage/revenue')
      .then(function (revenue) {
        renderRevenue(revenue || {});
      })
      .catch(function (err) {
        console.error('[seller-mypage.js] failed to load revenue:', err);
        if (handleUnauthorized(err)) {
          return;
        }
        // KPI 카드에 에러 표시 — 섹션 전체가 아니라 수치만 못 보여주는 상황이므로
        // 별도 에러 배너를 띄우지 않고 기본값("-")을 유지한다.
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

    // 공구팀 번호(admin-identifier-codes) — 백필 전 기존 팀은 값이 없을 수 있다.
    if (team.status === 'RECRUITING') {
      // RECRUITING: 마감 일시 대신 잔여 시간 표시 (마감 일시는 배지로 따로 강조)
      metaEl.textContent = [current + ' / ' + max + '명', team.teamNo].filter(Boolean).join(' · ');
    } else {
      metaEl.textContent = [current + ' / ' + max + '명', deadlineText, team.teamNo].filter(Boolean).join(' · ');
    }
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

    // RECRUITING 상태인 경우 인원 달성률 프로그레스 바 삽입
    if (team.status === 'RECRUITING') {
      infoEl.appendChild(createTeamProgressBarElement(team.currentCount, team.maxParticipants));
    }

    var mainEl = createListItemMainWrapper(team.imageUrl, team.productName, infoEl);
    li.appendChild(mainEl);

    var actionsEl = document.createElement('div');
    actionsEl.className = 'mypage-list-item__actions';

    // RECRUITING 팀에 잔여 시간 배지 노출
    if (team.status === 'RECRUITING') {
      var remainingText = formatRemaining(team.deadline);
      if (remainingText) {
        var timeBadgeEl = document.createElement('span');
        timeBadgeEl.className = 'badge badge-time';
        timeBadgeEl.textContent = '⏱️ ' + remainingText;
        actionsEl.appendChild(timeBadgeEl);
      }
    }

    var badgeEl = document.createElement('span');
    badgeEl.className = 'badge ' + statusToBadgeClass(team.status);
    badgeEl.textContent = statusToLabel(team.status);
    actionsEl.appendChild(badgeEl);

    li.appendChild(actionsEl);

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
   * 처리 완료 시 대기 환불 카운터도 1 차감한다.
   */
  function applyRefundRequestUpdate(request, li, badgeEl, metaEl, actionsEl) {
    badgeEl.className = 'badge ' + refundRequestStatusToBadgeClass(request.status);
    badgeEl.textContent = refundRequestStatusToLabel(request.status);
    // 요청자명은 타이틀에 있으므로 metaEl에는 금액·날짜·사유·거절사유만 갱신한다
    var amountText = formatPrice(request.amount);
    var requestedAtText = request.requestedAt ? '요청일 ' + formatDateTime(request.requestedAt) : '';
    var reasonText = request.reason ? '사유: ' + request.reason : '사유: 참여 취소';
    var rejectionText = request.status === 'REJECTED' && request.rejectionReason
      ? '거절 사유: ' + request.rejectionReason
      : '';
    metaEl.textContent = [amountText, requestedAtText, reasonText, rejectionText].filter(Boolean).join(' · ');
    clearChildren(actionsEl);
    actionsEl.appendChild(badgeEl);

    // 대기 환불 카운터 1 차감 (PENDING → APPROVED/REJECTED)
    if (summaryPendingRefundsCountEl) {
      var current = parseInt(summaryPendingRefundsCountEl.textContent, 10);
      if (!isNaN(current) && current > 0) {
        summaryPendingRefundsCountEl.textContent = (current - 1) + '건';
      }
    }
  }

  function createRefundRequestItem(request) {
    var li = document.createElement('li');
    li.className = 'mypage-list-item';

    var infoEl = document.createElement('div');
    infoEl.className = 'mypage-list-item__info';

    // 요청자명을 타이틀 라인으로 분리하여 가독성을 높인다.
    // 상품명은 메타 라인에서 확인 가능하므로 타이틀은 "누가 요청했는지"를 우선 노출한다.
    var titleEl = document.createElement('span');
    titleEl.className = 'mypage-list-item__title';
    if (request.requesterName) {
      titleEl.appendChild(window.Avatar.withName(
          request.requesterName, request.requesterProfileImageUrl, 'sm'));
      var suffixEl = document.createElement('span');
      suffixEl.textContent = '님의 환불 요청';
      titleEl.appendChild(suffixEl);
    } else {
      titleEl.textContent = request.productName || '환불 요청';
    }
    infoEl.appendChild(titleEl);

    // 상품명 + 금액·날짜·사유를 메타 라인으로
    var metaEl = document.createElement('span');
    metaEl.className = 'mypage-list-item__meta';
    var amountText = formatPrice(request.amount);
    var requestedAtText = request.requestedAt ? '요청일 ' + formatDateTime(request.requestedAt) : '';
    var reasonText = request.reason ? '사유: ' + request.reason : '사유: 참여 취소';
    var rejectionText = request.status === 'REJECTED' && request.rejectionReason
      ? '거절 사유: ' + request.rejectionReason
      : '';
    metaEl.textContent = [request.productName, amountText, requestedAtText, reasonText, rejectionText]
      .filter(Boolean).join(' · ');
    infoEl.appendChild(metaEl);

    var mainEl = createListItemMainWrapper(request.imageUrl, request.productName, infoEl);
    li.appendChild(mainEl);

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

    // PENDING 항목: 배지 강조 + 승인/거절 버튼 노출
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

    // PENDING 건수를 세어 상단 KPI 카드에 반영한다
    var pendingCount = 0;
    requests.forEach(function (request) {
      if (request.status === 'PENDING') pendingCount++;
      fragment.appendChild(createRefundRequestItem(request));
    });

    refundRequestsListEl.appendChild(fragment);

    if (summaryPendingRefundsCountEl) {
      summaryPendingRefundsCountEl.textContent = pendingCount + '건';
    }
  }

  function loadRefundRequests() {
    showStatus(refundRequestsStatusEl, '환불 요청 목록을 불러오는 중입니다...', 'loading');
    clearChildren(refundRequestsListEl);

    return window.Api.get('/seller/mypage/refund-requests')
      .then(function (requests) {
        var list = Array.isArray(requests) ? requests : [];
        if (list.length === 0) {
          showStatus(refundRequestsStatusEl, '아직 접수된 환불 요청이 없습니다.', 'empty');
          if (summaryPendingRefundsCountEl) summaryPendingRefundsCountEl.textContent = '0건';
          return;
        }
        hideStatus(refundRequestsStatusEl);
        renderRefundRequests(list);
      })
      .catch(function (err) {
        console.error('[seller-mypage.js] failed to load refund requests:', err);
        if (summaryPendingRefundsCountEl) summaryPendingRefundsCountEl.textContent = '0건';
        if (handleUnauthorized(err)) {
          return;
        }
        var message = (err && err.message) || '환불 요청 목록을 불러오지 못했습니다.';
        showStatus(refundRequestsStatusEl, message, 'error');
      });
  }

  function createOrderItem(order) {
    var li = document.createElement('li');
    li.className = 'mypage-list-item';
    li.setAttribute('data-payment-id', String(order.paymentId));

    var mainEl = document.createElement('div');
    mainEl.className = 'mypage-list-item__main';

    var thumbEl = document.createElement('div');
    thumbEl.className = 'mypage-list-item__thumb';
    thumbEl.style.display = 'flex';
    thumbEl.style.alignItems = 'center';
    thumbEl.style.justifyContent = 'center';
    thumbEl.style.fontSize = '18px';
    thumbEl.textContent = '📦';
    mainEl.appendChild(thumbEl);

    var infoEl = document.createElement('div');
    infoEl.className = 'mypage-list-item__info';

    var buyerEl = document.createElement('span');
    buyerEl.className = 'mypage-list-item__title';
    // 이모지 대신 실제 구매자 사진을 쓴다(member/profile-image 노출).
    buyerEl.appendChild(window.Avatar.create(order.buyerName, order.buyerProfileImageUrl, 'sm'));
    var buyerTextEl = document.createElement('span');
    buyerTextEl.textContent = '구매자: ' + order.buyerName + (order.buyerEmail ? ' (' + order.buyerEmail + ')' : '');
    buyerEl.appendChild(buyerTextEl);
    buyerEl.classList.add('avatar-name');
    infoEl.appendChild(buyerEl);

    var metaEl = document.createElement('span');
    metaEl.className = 'mypage-list-item__meta';
    // 공구팀 번호(admin-identifier-codes) — 팀이 딸린 주문에만 있다(혼자구매는 teamId처럼 null).
    metaEl.textContent = '📦 ' + order.productName + ' · ' + formatPrice(order.amount) + ' · 결제일 ' + formatDateTime(order.paidAt)
        + (order.teamNo ? ' · ' + order.teamNo : '');
    infoEl.appendChild(metaEl);

    var badgeGroup = document.createElement('div');
    badgeGroup.style.display = 'flex';
    badgeGroup.style.gap = 'var(--space-2)';
    badgeGroup.style.marginTop = 'var(--space-2)';

    // preparationStatus가 PREPARING(실제 배송 대상)인 주문은 고정 문구("🚚 배송 준비 중") 대신
    // 판매자가 직접 조작하는 진짜 배송 단계 배지 하나만 보여준다 — 둘 다 보여주면 판매자가 "배송중"으로
    // 바꿔놔도 옆에 "배송 준비 중"이 그대로 남아 두 배지가 서로 모순돼 보이는 문제가 있었다
    // (2026-08-21 사용자가 스크린샷으로 실제 화면에서 발견).
    var shipmentBadge = null;
    if (order.preparationStatus === 'PREPARING') {
      shipmentBadge = document.createElement('span');
      shipmentBadge.className = 'badge ' + shipmentStatusToBadgeClass(order.shipmentStatus);
      shipmentBadge.textContent = order.shipmentStatusLabel || order.shipmentStatus;
      badgeGroup.appendChild(shipmentBadge);
    } else {
      var prepBadge = document.createElement('span');
      if (order.preparationStatus === 'RECRUITING') {
        prepBadge.className = 'badge badge-time';
      } else if (order.preparationStatus === 'REFUNDED') {
        prepBadge.className = 'badge badge-failed';
      } else {
        prepBadge.className = 'badge badge-secondary';
      }
      prepBadge.textContent = order.preparationStatusLabel || order.preparationStatus;
      badgeGroup.appendChild(prepBadge);
    }

    infoEl.appendChild(badgeGroup);

    // 저장 성공 시 createShipmentPanel이 이 요소를 직접 갱신한다(전체 목록 재조회 없이).
    var trackingEl = document.createElement('span');
    trackingEl.className = 'mypage-list-item__meta';
    updateTrackingText(trackingEl, order.trackingCarrier, order.trackingNumber);
    infoEl.appendChild(trackingEl);

    mainEl.appendChild(infoEl);
    li.appendChild(mainEl);

    if (order.preparationStatus === 'PREPARING') {
      li.appendChild(createShipmentPanel(order, shipmentBadge, trackingEl));
    }

    return li;
  }

  function updateTrackingText(trackingEl, carrier, number) {
    if (!number) {
      trackingEl.hidden = true;
      trackingEl.textContent = '';
      return;
    }
    trackingEl.hidden = false;
    trackingEl.textContent = '🚚 ' + (carrier ? carrier + ' ' : '') + number;
  }

  var SHIPMENT_STATUS_OPTIONS = [
    { value: 'PRODUCT_PREPARING', label: '상품 준비중' },
    { value: 'SHIPPING_PREPARING', label: '배송 준비중' },
    { value: 'IN_TRANSIT', label: '배송중' },
    { value: 'DELIVERED', label: '배송완료' },
  ];

  function shipmentStatusToBadgeClass(status) {
    if (status === 'DELIVERED') {
      return 'badge-success';
    }
    if (status === 'IN_TRANSIT') {
      return 'badge-time';
    }
    return 'badge-secondary';
  }

  /**
   * 판매자가 배송 단계·택배사·송장번호를 입력해 PATCH .../orders/{paymentId}/shipment로 저장하는 패널.
   * 성공하면 배지/트래킹 표시를 그 항목 안에서 즉시 갱신한다(전체 목록 재조회 없음, 환불 패널과 동일 패턴).
   */
  function createShipmentPanel(order, shipmentBadge, trackingEl) {
    var panel = document.createElement('div');
    panel.className = 'mypage-list-item__actions';
    panel.style.marginTop = 'var(--space-2)';
    panel.style.flexWrap = 'wrap';

    var select = document.createElement('select');
    select.className = 'form-select';
    SHIPMENT_STATUS_OPTIONS.forEach(function (option) {
      var optionEl = document.createElement('option');
      optionEl.value = option.value;
      optionEl.textContent = option.label;
      if (option.value === order.shipmentStatus) {
        optionEl.selected = true;
      }
      select.appendChild(optionEl);
    });
    panel.appendChild(select);

    var carrierInput = document.createElement('input');
    carrierInput.type = 'text';
    carrierInput.className = 'form-input';
    carrierInput.placeholder = '택배사 (예: CJ대한통운)';
    carrierInput.value = order.trackingCarrier || '';
    carrierInput.style.maxWidth = '160px';
    panel.appendChild(carrierInput);

    var trackingInput = document.createElement('input');
    trackingInput.type = 'text';
    trackingInput.className = 'form-input';
    trackingInput.placeholder = '송장번호';
    trackingInput.value = order.trackingNumber || '';
    trackingInput.style.maxWidth = '160px';
    panel.appendChild(trackingInput);

    var saveBtn = document.createElement('button');
    saveBtn.type = 'button';
    saveBtn.className = 'btn btn-primary btn-sm';
    saveBtn.textContent = '저장';

    var errorEl = document.createElement('span');
    errorEl.className = 'mypage-list-item__meta';
    errorEl.style.color = 'var(--color-error)';
    errorEl.hidden = true;

    saveBtn.addEventListener('click', function () {
      saveBtn.disabled = true;
      errorEl.hidden = true;

      window.Api.patch('/seller/mypage/orders/' + order.paymentId + '/shipment', {
        shipmentStatus: select.value,
        trackingCarrier: carrierInput.value || null,
        trackingNumber: trackingInput.value || null,
      })
        .then(function (updated) {
          saveBtn.disabled = false;
          order.shipmentStatus = updated.shipmentStatus;
          order.shipmentStatusLabel = updated.shipmentStatusLabel;
          order.trackingCarrier = updated.trackingCarrier;
          order.trackingNumber = updated.trackingNumber;
          if (shipmentBadge) {
            shipmentBadge.className = 'badge ' + shipmentStatusToBadgeClass(updated.shipmentStatus);
            shipmentBadge.textContent = updated.shipmentStatusLabel || updated.shipmentStatus;
          }
          updateTrackingText(trackingEl, updated.trackingCarrier, updated.trackingNumber);
          carrierInput.value = updated.trackingCarrier || '';
          trackingInput.value = updated.trackingNumber || '';
        })
        .catch(function (err) {
          saveBtn.disabled = false;
          console.error('[seller-mypage.js] failed to update shipment:', err);
          if (handleUnauthorized(err)) {
            return;
          }
          errorEl.textContent = (err && err.message) || '배송 상태 저장에 실패했습니다.';
          errorEl.hidden = false;
        });
    });
    panel.appendChild(saveBtn);
    panel.appendChild(errorEl);

    return panel;
  }

  function renderOrders(orders) {
    clearChildren(ordersListEl);
    var fragment = document.createDocumentFragment();
    orders.forEach(function (order) {
      fragment.appendChild(createOrderItem(order));
    });
    ordersListEl.appendChild(fragment);
  }

  function loadOrders() {
    showStatus(ordersStatusEl, '주문 및 결제 목록을 불러오는 중입니다...', 'loading');
    clearChildren(ordersListEl);

    return window.Api.get('/seller/mypage/orders')
      .then(function (orders) {
        var list = Array.isArray(orders) ? orders : [];
        if (list.length === 0) {
          showStatus(ordersStatusEl, '아직 접수된 결제/주문 내역이 없습니다.', 'empty');
          return;
        }
        hideStatus(ordersStatusEl);
        renderOrders(list);
      })
      .catch(function (err) {
        console.error('[seller-mypage.js] failed to load orders:', err);
        if (handleUnauthorized(err)) {
          return;
        }
        var message = (err && err.message) || '주문 내역을 불러오지 못했습니다.';
        showStatus(ordersStatusEl, message, 'error');
      });
  }

  function init() {
    setupTabs();
    loadProfileInfo();
    loadOrders();
    loadProducts();
    loadRevenue();
    loadTeams();
    loadRefundRequests();
  }

  init();
})();
