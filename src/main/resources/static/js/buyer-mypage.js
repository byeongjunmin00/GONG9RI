/**
 * buyer-mypage.js — 구매자 마이페이지(buyer/mypage.html) 전용 스크립트
 *
 * 이 페이지는 static/ 루트가 아닌 서브디렉토리(/buyer/mypage.html)에 있으므로,
 * 페이지 이동이 필요해지면 반드시 절대경로를 쓴다(현재는 페이지 이동 액션 없음).
 *
 * - 상단 프로필 카드의 사용자 이름/이메일을 GET /api/auth/me에서 불러온다.
 * - 로드 시 4개 API를 각각 호출한다: GET /api/buyer/mypage/purchases, /teams, /refund-requests, /wishlist.
 *   로그인 상태를 사전에 확인하지 않는 기존 원칙과 동일하다.
 * - 둘 중 하나라도 401(UNAUTHORIZED)이면 페이지 상단 공통 배너(#page-alert)에 로그인 안내 +
 *   로그인 링크를 띄우고, 마이페이지 섹션 전체를 숨긴다(서버가 신뢰 SSOT).
 * - 403(FORBIDDEN, 판매자 계정)/기타 에러는 해당 섹션의 상태 영역에 서버 message를 표시하고
 *   다른 섹션은 각자 독립적으로 계속 로드된다(한 섹션의 실패가 페이지 전체를 깨지 않는다).
 * - 탭 네비게이션: [전체 현황], [구매 내역], [공구 참여], [찜한 상품], [환불 내역], [계정 설정] 탭 스위칭 및
 *   상단 대시보드 KPI 카드 클릭 시 해당 탭 이동, URL hash(#purchases 등) 연동을 지원한다.
 * - 구매 목록: status가 PAID/REFUNDED인지에 따라 배지/문구를 구분한다(REFUNDED는 "환불됨").
 * - 공구 참여 목록: status(RECRUITING/SUCCESS/FAILED)별로 표시를 분기한다.
 *   - RECRUITING: deadline과 현재 시각의 차이로 "남은 유지 기간"을 계산해 .badge-time 배지로 강조하고,
 *     currentCount/maxParticipants로 시각적 프로그레스 바(team-progress)를 표시한다.
 *   - SUCCESS: 구매 목록과 같은 카드 스타일(.mypage-list-item)로 "성사 완료"를 표시한다.
 *     productId 기준으로 purchases 목록에서 대응하는 PAID 결제를 찾아(best-effort) 금액/결제일시를
 *     함께 보여준다 — 매칭 실패해도 에러로 취급하지 않고 팀 정보만 표시한다.
 *   - FAILED: "미성사"로 표시한다. 마감 스케줄러로 실패한 경우와 달리, 마지막 참여자 취소로 실패한
 *     경우는 환불이 곧바로 처리되는 게 아니라 요청 상태(대기/승인/거절)로 남을 수 있어(team/leave,
 *     상품별 "참여 취소 시 자동환불" 설정에 따라 다름) 팀 카드에서 환불 완료 여부를 단정하지 않는다.
 *     실제 환불 상태는 아래 환불 요청 내역 섹션에서 확인한다.
 * - RECRUITING 팀 항목에는 "참여 취소" 버튼을 추가로 노출한다(POST /api/teams/{teamId}/leave). 성공하면
 *   공구 참여 목록과 환불 요청 내역을 함께 다시 불러온다(취소로 환불 요청이 자동 생성될 수 있어서).
 * - 혼자구매(teamId가 null)한 PAID 결제 항목에는 "환불 요청" 버튼 + 사유 입력 패널을 노출한다
 *   (POST /api/payments/{paymentId}/refund-requests). 팀이 딸린 결제는 이 버튼 자체가 없다 — 그 환불은
 *   참여 취소로만 가능하다(docs/api/refund.md).
 * - 환불 요청 내역 섹션은 GET /api/buyer/mypage/refund-requests로 상태(대기/승인/거절)와 거절 사유를
 *   읽기 전용으로 보여준다.
 * - 찜한 상품 섹션(product/wishlist)은 GET /api/buyer/mypage/wishlist로 목록을 불러오고, 각 항목에
 *   "찜 해제" 버튼(DELETE /api/products/{productId}/wishlist)을 둔다 — 메인 페이지 카드의 하트 토글과
 *   같은 API를 재사용한다.
 * - 서버 응답 문자열(상품명/에러 message 등)은 textContent로만 대입해 XSS를 방지한다.
 */
(function () {
  var pageAlertEl = document.getElementById('page-alert');
  var pageAlertTextEl = document.getElementById('page-alert-text');
  var pageAlertLoginLinkEl = document.getElementById('page-alert-login-link');

  var mypageSectionsEl = document.getElementById('mypage-sections');

  var summaryUserNameEl = document.getElementById('summary-user-name');
  var summaryUserEmailEl = document.getElementById('summary-user-email');
  var summaryPurchasesCountEl = document.getElementById('summary-purchases-count');
  var summaryTeamsCountEl = document.getElementById('summary-teams-count');
  var summaryWishlistCountEl = document.getElementById('summary-wishlist-count');
  var summaryRefundsCountEl = document.getElementById('summary-refunds-count');

  var tabBtns = document.querySelectorAll('.mypage-tab-btn');
  var tabPanels = document.querySelectorAll('.mypage-tab-panel');
  var summaryCards = document.querySelectorAll('.summary-card');

  var purchasesStatusEl = document.getElementById('purchases-status');
  var purchasesListEl = document.getElementById('purchases-list');

  var teamsStatusEl = document.getElementById('teams-status');
  var teamsListEl = document.getElementById('teams-list');

  var refundRequestsStatusEl = document.getElementById('refund-requests-status');
  var refundRequestsListEl = document.getElementById('refund-requests-list');

  var wishlistStatusEl = document.getElementById('wishlist-status');
  var wishlistListEl = document.getElementById('wishlist-list');

  if (
    !pageAlertEl || !pageAlertTextEl || !pageAlertLoginLinkEl || !mypageSectionsEl ||
    !purchasesStatusEl || !purchasesListEl ||
    !teamsStatusEl || !teamsListEl ||
    !refundRequestsStatusEl || !refundRequestsListEl ||
    !wishlistStatusEl || !wishlistListEl
  ) {
    return;
  }

  // loadPurchases()가 완료되면 채워진다. 공구 참여 목록의 SUCCESS 항목 매칭에 사용한다.
  var latestPurchases = [];

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

  // ---------- 탭 네비게이션 & 대시보드 로직 ----------

  function switchTab(targetTab) {
    if (!targetTab) targetTab = 'all';

    tabBtns.forEach(function (btn) {
      var isTarget = btn.getAttribute('data-tab') === targetTab;
      if (isTarget) {
        btn.classList.add('active');
        btn.setAttribute('aria-selected', 'true');
      } else {
        btn.classList.remove('active');
        btn.setAttribute('aria-selected', 'false');
      }
    });

    tabPanels.forEach(function (panel) {
      var panelTab = panel.getAttribute('data-tab-panel');
      if (targetTab === 'all') {
        panel.hidden = false;
      } else {
        panel.hidden = (panelTab !== targetTab);
      }
    });

    if (history.replaceState) {
      history.replaceState(null, '', '#' + targetTab);
    }
  }

  function setupTabs() {
    tabBtns.forEach(function (btn) {
      btn.addEventListener('click', function () {
        var tab = btn.getAttribute('data-tab');
        switchTab(tab);
      });
    });

    summaryCards.forEach(function (card) {
      card.addEventListener('click', function () {
        var tab = card.getAttribute('data-tab');
        switchTab(tab);
      });
    });

    var hash = window.location.hash.replace('#', '');
    if (hash && ['all', 'purchases', 'teams', 'wishlist', 'refunds', 'account'].indexOf(hash) !== -1) {
      switchTab(hash);
    } else {
      switchTab('all');
    }
  }

  function loadProfileInfo() {
    return window.Api.get('/auth/me')
      .then(function (user) {
        if (user && summaryUserNameEl && summaryUserEmailEl) {
          summaryUserNameEl.textContent = user.name || '구매자';
          summaryUserEmailEl.textContent = user.email || '';
          // 프로필 사진(member/profile-image 노출). 컨테이너가 이미 원형·크기를 갖고 있어 fill을 쓴다.
          window.Avatar.fill(document.querySelector('.mypage-profile__avatar'),
              user.name, user.profileImageUrl);
        }
      })
      .catch(function (err) {
        if (handleUnauthorized(err)) return;
      });
  }

  // ---------- UI Helper: 썸네일 & 메인 레이아웃 헬퍼 ----------

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
   * 썸네일 영역과 메인 정보 영역(infoEl)을 감싸는 중복 구조를 리팩터링한 헬퍼 함수
   */
  function createListItemMainWrapper(imageUrl, altText, infoEl) {
    var mainEl = document.createElement('div');
    mainEl.className = 'mypage-list-item__main';
    var thumbEl = createThumbnailElement(imageUrl, altText);
    mainEl.appendChild(thumbEl);
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

  // ---------- 구매 목록 ----------

  function purchaseStatusToBadgeClass(status) {
    if (status === 'REFUNDED') {
      return 'badge-failed';
    }
    return 'badge-success';
  }

  function purchaseStatusToLabel(status) {
    if (status === 'REFUNDED') {
      return '환불됨';
    }
    return '결제 완료';
  }

  /**
   * 혼자구매(teamId가 null)한 PAID 결제에만 "환불 요청" 버튼 + 사유 입력 패널을 붙인다 — 팀이 딸린
   * 결제는 참여 취소로만 환불 가능하므로 이 버튼 자체를 노출하지 않는다(docs/api/refund.md).
   */
  function appendRefundRequestAction(purchase, actionsEl) {
    if (purchase.teamId !== null || purchase.status !== 'PAID') {
      return;
    }

    var requestBtn = document.createElement('button');
    requestBtn.type = 'button';
    requestBtn.className = 'btn btn-ghost btn-sm';
    requestBtn.textContent = '환불 요청';
    actionsEl.appendChild(requestBtn);

    var panel = document.createElement('div');
    panel.className = 'refund-reject-panel';
    panel.hidden = true;

    var reasonInput = document.createElement('input');
    reasonInput.type = 'text';
    reasonInput.className = 'form-input';
    reasonInput.placeholder = '환불을 요청하는 사유를 입력하세요';
    panel.appendChild(reasonInput);

    var submitBtn = document.createElement('button');
    submitBtn.type = 'button';
    submitBtn.className = 'btn btn-secondary btn-sm';
    submitBtn.textContent = '요청 보내기';
    submitBtn.addEventListener('click', function () {
      var reason = reasonInput.value.trim();
      if (!reason) {
        reasonInput.focus();
        return;
      }
      submitBtn.disabled = true;
      window.Api.post('/payments/' + purchase.paymentId + '/refund-requests', { reason: reason })
        .then(function () {
          panel.hidden = true;
          requestBtn.disabled = true;
          requestBtn.textContent = '요청됨';
          // 버튼 텍스트만 바뀌는 걸로는 잘 안 보인다는 피드백(2026-08-19) — 구매 목록 상단에
          // 눈에 띄는 성공 안내를 띄운다(에러 안내와 같은 자리, showStatus 재사용).
          showStatus(purchasesStatusEl, '환불 요청을 보냈습니다. 판매자 승인을 기다려주세요.', 'success');
          loadRefundRequests();
        })
        .catch(function (err) {
          console.error('[buyer-mypage.js] failed to create refund request:', err);
          submitBtn.disabled = false;
          if (handleUnauthorized(err)) {
            return;
          }
          var message = (err && err.message) || '환불 요청에 실패했습니다.';
          showStatus(purchasesStatusEl, message, 'error');
        });
    });
    panel.appendChild(submitBtn);

    requestBtn.addEventListener('click', function () {
      panel.hidden = !panel.hidden;
    });

    actionsEl.appendChild(panel);
  }

  function createPurchaseItem(purchase) {
    var li = document.createElement('li');
    li.className = 'mypage-list-item';

    var infoEl = document.createElement('div');
    infoEl.className = 'mypage-list-item__info';

    var titleEl = document.createElement('span');
    titleEl.className = 'mypage-list-item__title';
    titleEl.textContent = purchase.productName || '';
    infoEl.appendChild(titleEl);

    var metaEl = document.createElement('span');
    metaEl.className = 'mypage-list-item__meta';
    var amountText = formatPrice(purchase.amount);
    var paidAtText = purchase.paidAt ? '결제일시 ' + purchase.paidAt : '';
    // 공구팀 번호(admin-identifier-codes) — 팀이 딸린 결제에만 있다(혼자구매는 teamId처럼 null).
    metaEl.textContent = [amountText, paidAtText, purchase.teamNo].filter(Boolean).join(' · ');
    infoEl.appendChild(metaEl);

    // 판매자가 조작하는 배송 단계(007) — 읽기 전용. status가 PAID일 때만 의미가 있다(REFUNDED
    // 건은 서버도 배송 단계를 항상 기본값으로만 두므로 굳이 보여주지 않는다).
    if (purchase.status === 'PAID' && purchase.shipmentStatusLabel) {
      var shipmentEl = document.createElement('span');
      shipmentEl.className = 'mypage-list-item__meta';
      var trackingText = purchase.trackingNumber
        ? (purchase.trackingCarrier ? purchase.trackingCarrier + ' ' : '') + purchase.trackingNumber
        : '';
      shipmentEl.textContent = ['🚚 ' + purchase.shipmentStatusLabel, trackingText].filter(Boolean).join(' · ');
      infoEl.appendChild(shipmentEl);
    }

    var mainEl = createListItemMainWrapper(purchase.imageUrl, purchase.productName, infoEl);
    li.appendChild(mainEl);

    var actionsEl = document.createElement('div');
    actionsEl.className = 'mypage-list-item__actions';

    var badgeEl = document.createElement('span');
    badgeEl.className = 'badge ' + purchaseStatusToBadgeClass(purchase.status);
    badgeEl.textContent = purchaseStatusToLabel(purchase.status);
    actionsEl.appendChild(badgeEl);

    appendRefundRequestAction(purchase, actionsEl);

    li.appendChild(actionsEl);

    return li;
  }

  function renderPurchases(purchases) {
    clearChildren(purchasesListEl);
    var fragment = document.createDocumentFragment();
    purchases.forEach(function (purchase) {
      fragment.appendChild(createPurchaseItem(purchase));
    });
    purchasesListEl.appendChild(fragment);
  }

  function loadPurchases() {
    showStatus(purchasesStatusEl, '구매 목록을 불러오는 중입니다...', 'loading');
    clearChildren(purchasesListEl);

    return window.Api.get('/buyer/mypage/purchases')
      .then(function (purchases) {
        var list = Array.isArray(purchases) ? purchases : [];
        latestPurchases = list;

        if (summaryPurchasesCountEl) {
          summaryPurchasesCountEl.textContent = list.length;
        }

        if (list.length === 0) {
          showStatus(purchasesStatusEl, '구매한 상품이 없습니다.', 'empty');
          return;
        }
        hideStatus(purchasesStatusEl);
        renderPurchases(list);
      })
      .catch(function (err) {
        console.error('[buyer-mypage.js] failed to load purchases:', err);
        if (summaryPurchasesCountEl) summaryPurchasesCountEl.textContent = '0';
        if (handleUnauthorized(err)) {
          return;
        }
        var message = (err && err.message) || '구매 목록을 불러오지 못했습니다.';
        showStatus(purchasesStatusEl, message, 'error');
      });
  }

  // ---------- 공구 참여 목록 ----------

  /**
   * productId 기준으로 latestPurchases에서 대응하는 PAID 결제를 찾는다(best-effort).
   * 한 상품에 결제 이력이 여러 번 있으면 어느 결제가 해당 팀 결제인지 API로는 구분할 수 없어
   * 첫 번째로 발견되는 PAID 결제를 사용한다. 매칭 실패 시 null(에러 아님).
   */
  function findMatchingPurchase(team) {
    for (var i = 0; i < latestPurchases.length; i++) {
      var purchase = latestPurchases[i];
      if (purchase.productId === team.productId && purchase.status === 'PAID') {
        return purchase;
      }
    }
    return null;
  }

  function teamStatusToBadgeClass(status) {
    if (status === 'SUCCESS') {
      return 'badge-success';
    }
    if (status === 'FAILED') {
      return 'badge-failed';
    }
    return 'badge-recruiting';
  }

  function teamStatusToLabel(status) {
    if (status === 'SUCCESS') {
      return '성사 완료';
    }
    if (status === 'FAILED') {
      return '미성사';
    }
    return '모집중';
  }

  function teamCountText(team) {
    var current = typeof team.currentCount === 'number' ? team.currentCount : '?';
    var max = typeof team.maxParticipants === 'number' ? team.maxParticipants : '?';
    return current + ' / ' + max + '명';
  }

  function createTeamItem(team) {
    var li = document.createElement('li');
    li.className = 'mypage-list-item';

    var infoEl = document.createElement('div');
    infoEl.className = 'mypage-list-item__info';

    var titleEl = document.createElement('span');
    titleEl.className = 'mypage-list-item__title';
    titleEl.textContent = team.productName || '';
    infoEl.appendChild(titleEl);

    var metaEl = document.createElement('span');
    metaEl.className = 'mypage-list-item__meta';

    var metaParts;
    var remainingText = '';

    if (team.status === 'SUCCESS') {
      var matchedPurchase = findMatchingPurchase(team);
      if (matchedPurchase) {
        var amountText = formatPrice(matchedPurchase.amount);
        var paidAtText = matchedPurchase.paidAt ? '결제일시 ' + matchedPurchase.paidAt : '';
        metaParts = [amountText, paidAtText];
      } else {
        metaParts = [teamCountText(team)];
      }
    } else if (team.status === 'FAILED') {
      metaParts = [teamCountText(team)];
    } else {
      // RECRUITING: 남은 유지 기간 계산
      remainingText = formatRemaining(team.deadline);
      metaParts = [teamCountText(team)];
    }

    // 공구팀 번호(admin-identifier-codes) — 백필 전 기존 팀은 값이 없을 수 있다.
    metaParts.push(team.teamNo);
    metaEl.textContent = metaParts.filter(Boolean).join(' · ');
    infoEl.appendChild(metaEl);

    // RECRUITING 상태인 경우 인원 달성률 프로그레스 바 삽입
    if (team.status === 'RECRUITING') {
      var progressEl = createTeamProgressBarElement(team.currentCount, team.maxParticipants);
      infoEl.appendChild(progressEl);
    }

    var mainEl = createListItemMainWrapper(team.imageUrl, team.productName, infoEl);
    li.appendChild(mainEl);

    var actionsEl = document.createElement('div');
    actionsEl.className = 'mypage-list-item__actions';

    // RECRUITING 팀인 경우 잔여 시간 강조 배지(.badge-time) 노출
    if (team.status === 'RECRUITING' && remainingText) {
      var timeBadgeEl = document.createElement('span');
      timeBadgeEl.className = 'badge badge-time';
      timeBadgeEl.textContent = '⏱️ 마감까지 ' + remainingText;
      actionsEl.appendChild(timeBadgeEl);
    }

    var badgeEl = document.createElement('span');
    badgeEl.className = 'badge ' + teamStatusToBadgeClass(team.status);
    badgeEl.textContent = teamStatusToLabel(team.status);
    actionsEl.appendChild(badgeEl);

    // RECRUITING 팀만 참여 취소 가능(팀이 정원을 채워 SUCCESS로 전환된 뒤에는 취소 불가 —
    // docs/api/team.md의 POST /api/teams/{teamId}/leave 계약).
    if (team.status === 'RECRUITING') {
      var leaveBtn = document.createElement('button');
      leaveBtn.type = 'button';
      leaveBtn.className = 'btn btn-ghost btn-sm';
      leaveBtn.textContent = '참여 취소';
      leaveBtn.addEventListener('click', function () {
        var confirmed = window.confirm(
          '이 공구팀 참여를 취소하시겠습니까? 결제하신 금액이 있으면 환불 요청이 자동으로 생성됩니다.');
        if (!confirmed) {
          return;
        }
        leaveBtn.disabled = true;
        window.Api.post('/teams/' + team.teamId + '/leave')
          .then(function () {
            loadTeams();
            loadRefundRequests();
          })
          .catch(function (err) {
            console.error('[buyer-mypage.js] failed to leave team:', err);
            leaveBtn.disabled = false;
            if (handleUnauthorized(err)) {
              return;
            }
            var message = (err && err.message) || '참여 취소에 실패했습니다.';
            showStatus(teamsStatusEl, message, 'error');
          });
      });
      actionsEl.appendChild(leaveBtn);
    }

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
    showStatus(teamsStatusEl, '공구 참여 목록을 불러오는 중입니다...', 'loading');
    clearChildren(teamsListEl);

    return window.Api.get('/buyer/mypage/teams')
      .then(function (teams) {
        var list = Array.isArray(teams) ? teams : [];
        if (summaryTeamsCountEl) {
          summaryTeamsCountEl.textContent = list.length;
        }

        if (list.length === 0) {
          showStatus(teamsStatusEl, '참여한 공구팀이 없습니다.', 'empty');
          return;
        }
        hideStatus(teamsStatusEl);
        renderTeams(list);
      })
      .catch(function (err) {
        console.error('[buyer-mypage.js] failed to load teams:', err);
        if (summaryTeamsCountEl) summaryTeamsCountEl.textContent = '0';
        if (handleUnauthorized(err)) {
          return;
        }
        var message = (err && err.message) || '공구 참여 목록을 불러오지 못했습니다.';
        showStatus(teamsStatusEl, message, 'error');
      });
  }

  // ---------- 환불 요청 내역(읽기 전용) ----------

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
      return '승인됨(환불 처리중/완료)';
    }
    if (status === 'REJECTED') {
      return '거절됨';
    }
    return '대기중';
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
    var amountText = formatPrice(request.amount);
    var requestedAtText = request.requestedAt ? '요청일 ' + request.requestedAt : '';
    var reasonText = request.reason ? '사유: ' + request.reason : '사유: 참여 취소';
    var rejectionText = request.status === 'REJECTED' && request.rejectionReason
      ? '거절 사유: ' + request.rejectionReason
      : '';
    metaEl.textContent = [amountText, requestedAtText, reasonText, rejectionText].filter(Boolean).join(' · ');
    infoEl.appendChild(metaEl);

    var mainEl = createListItemMainWrapper(request.imageUrl, request.productName, infoEl);
    li.appendChild(mainEl);

    var actionsEl = document.createElement('div');
    actionsEl.className = 'mypage-list-item__actions';

    var badgeEl = document.createElement('span');
    badgeEl.className = 'badge ' + refundRequestStatusToBadgeClass(request.status);
    badgeEl.textContent = refundRequestStatusToLabel(request.status);
    actionsEl.appendChild(badgeEl);

    li.appendChild(actionsEl);

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
    showStatus(refundRequestsStatusEl, '환불 요청 내역을 불러오는 중입니다...', 'loading');
    clearChildren(refundRequestsListEl);

    return window.Api.get('/buyer/mypage/refund-requests')
      .then(function (requests) {
        var list = Array.isArray(requests) ? requests : [];
        if (summaryRefundsCountEl) {
          summaryRefundsCountEl.textContent = list.length;
        }

        if (list.length === 0) {
          showStatus(refundRequestsStatusEl, '환불 요청 내역이 없습니다.', 'empty');
          return;
        }
        hideStatus(refundRequestsStatusEl);
        renderRefundRequests(list);
      })
      .catch(function (err) {
        console.error('[buyer-mypage.js] failed to load refund requests:', err);
        if (summaryRefundsCountEl) summaryRefundsCountEl.textContent = '0';
        if (handleUnauthorized(err)) {
          return;
        }
        var message = (err && err.message) || '환불 요청 내역을 불러오지 못했습니다.';
        showStatus(refundRequestsStatusEl, message, 'error');
      });
  }

  // ---------- 찜한 상품 ----------

  function createWishlistItem(item) {
    var li = document.createElement('li');
    li.className = 'mypage-list-item';

    var infoEl = document.createElement('div');
    infoEl.className = 'mypage-list-item__info';

    var titleEl = document.createElement('a');
    titleEl.className = 'mypage-list-item__title';
    titleEl.href = '/product.html?id=' + item.productId;
    titleEl.textContent = item.productName || '';
    infoEl.appendChild(titleEl);

    var metaEl = document.createElement('span');
    metaEl.className = 'mypage-list-item__meta';
    var priceText = formatPrice(item.bestPrice != null ? item.bestPrice : item.basePrice);
    if (item.sellerName) {
      metaEl.appendChild(window.Avatar.withName(item.sellerName, item.sellerProfileImageUrl, 'xs'));
      var priceEl = document.createElement('span');
      priceEl.textContent = ' · ' + priceText;
      metaEl.appendChild(priceEl);
    } else {
      metaEl.textContent = priceText;
    }
    infoEl.appendChild(metaEl);

    var mainEl = createListItemMainWrapper(item.imageUrl, item.productName, infoEl);
    li.appendChild(mainEl);

    var actionsEl = document.createElement('div');
    actionsEl.className = 'mypage-list-item__actions';

    var removeBtn = document.createElement('button');
    removeBtn.type = 'button';
    removeBtn.className = 'btn btn-ghost btn-sm';
    removeBtn.textContent = '찜 해제';
    removeBtn.addEventListener('click', function () {
      removeBtn.disabled = true;
      window.Api.del('/products/' + item.productId + '/wishlist')
        .then(function () {
          loadWishlist();
          // 헤더 찜 아이콘 뱃지도 같이 갱신(js/header-wishlist-badge.js) — js/main.js의 하트 토글과
          // 동일한 이벤트를 재사용한다.
          document.dispatchEvent(new CustomEvent('gong9ri:wishlist-changed'));
        })
        .catch(function (err) {
          console.error('[buyer-mypage.js] failed to remove wishlist item:', err);
          removeBtn.disabled = false;
        });
    });
    actionsEl.appendChild(removeBtn);

    li.appendChild(actionsEl);

    return li;
  }

  function renderWishlist(items) {
    clearChildren(wishlistListEl);
    var fragment = document.createDocumentFragment();
    items.forEach(function (item) {
      fragment.appendChild(createWishlistItem(item));
    });
    wishlistListEl.appendChild(fragment);
  }

  function loadWishlist() {
    showStatus(wishlistStatusEl, '찜한 상품을 불러오는 중입니다...', 'loading');
    clearChildren(wishlistListEl);

    return window.Api.get('/buyer/mypage/wishlist')
      .then(function (items) {
        var list = Array.isArray(items) ? items : [];
        if (summaryWishlistCountEl) {
          summaryWishlistCountEl.textContent = list.length;
        }

        if (list.length === 0) {
          showStatus(wishlistStatusEl, '찜한 상품이 없습니다.', 'empty');
          return;
        }
        hideStatus(wishlistStatusEl);
        renderWishlist(list);
      })
      .catch(function (err) {
        console.error('[buyer-mypage.js] failed to load wishlist:', err);
        if (summaryWishlistCountEl) summaryWishlistCountEl.textContent = '0';
        if (handleUnauthorized(err)) {
          return;
        }
        var message = (err && err.message) || '찜한 상품을 불러오지 못했습니다.';
        showStatus(wishlistStatusEl, message, 'error');
      });
  }

  function init() {
    setupTabs();
    loadProfileInfo();

    // purchases를 먼저 로드해 latestPurchases를 채운 뒤 teams를 로드해야
    // SUCCESS 항목의 결제 매칭(findMatchingPurchase)이 최신 데이터를 사용할 수 있다.
    loadPurchases().then(function () {
      loadTeams();
    });
    loadRefundRequests();
    loadWishlist();
  }

  init();
})();
