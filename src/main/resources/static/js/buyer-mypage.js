/**
 * buyer-mypage.js — 구매자 마이페이지(buyer/mypage.html) 전용 스크립트
 *
 * 이 페이지는 static/ 루트가 아닌 서브디렉토리(/buyer/mypage.html)에 있으므로,
 * 페이지 이동이 필요해지면 반드시 절대경로를 쓴다(현재는 페이지 이동 액션 없음).
 *
 * - 로드 시 두 API를 각각 호출한다: GET /api/buyer/mypage/purchases, /teams.
 *   로그인 상태를 사전에 확인하지 않는 기존 원칙과 동일하다.
 * - 둘 중 하나라도 401(UNAUTHORIZED)이면 페이지 상단 공통 배너(#page-alert)에 로그인 안내 +
 *   로그인 링크를 띄우고, 두 섹션 전체를 숨긴다(서버가 신뢰 SSOT).
 * - 403(FORBIDDEN, 판매자 계정)/기타 에러는 해당 섹션의 상태 영역에 서버 message를 표시하고
 *   다른 섹션은 각자 독립적으로 계속 로드된다(한 섹션의 실패가 페이지 전체를 깨지 않는다).
 * - 구매 목록: status가 PAID/REFUNDED인지에 따라 배지/문구를 구분한다(REFUNDED는 "환불됨").
 * - 공구 참여 목록: status(RECRUITING/SUCCESS/FAILED)별로 표시를 분기한다.
 *   - RECRUITING: deadline과 현재 시각의 차이로 "남은 유지 기간"을 계산해 표시하고,
 *     currentCount/maxParticipants로 현재 인원 상태를 표시한다.
 *   - SUCCESS: 구매 목록과 같은 카드 스타일(.mypage-list-item)로 "성사 완료"를 표시한다.
 *     productId 기준으로 purchases 목록에서 대응하는 PAID 결제를 찾아(best-effort) 금액/결제일시를
 *     함께 보여준다 — 매칭 실패해도 에러로 취급하지 않고 팀 정보만 표시한다.
 *   - FAILED: "미성사(환불 처리됨)"으로 표시한다(정책상 마감 지난 팀은 이미 환불 처리가 끝난 상태).
 * - 서버 응답 문자열(상품명/에러 message 등)은 textContent로만 대입해 XSS를 방지한다.
 */
(function () {
  var pageAlertEl = document.getElementById('page-alert');
  var pageAlertTextEl = document.getElementById('page-alert-text');
  var pageAlertLoginLinkEl = document.getElementById('page-alert-login-link');

  var mypageSectionsEl = document.getElementById('mypage-sections');

  var purchasesStatusEl = document.getElementById('purchases-status');
  var purchasesListEl = document.getElementById('purchases-list');

  var teamsStatusEl = document.getElementById('teams-status');
  var teamsListEl = document.getElementById('teams-list');

  if (
    !pageAlertEl || !pageAlertTextEl || !pageAlertLoginLinkEl || !mypageSectionsEl ||
    !purchasesStatusEl || !purchasesListEl ||
    !teamsStatusEl || !teamsListEl
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
    mypageSectionsEl.hidden = true;
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
    metaEl.textContent = [amountText, paidAtText].filter(Boolean).join(' · ');
    infoEl.appendChild(metaEl);

    li.appendChild(infoEl);

    var badgeEl = document.createElement('span');
    badgeEl.className = 'badge ' + purchaseStatusToBadgeClass(purchase.status);
    badgeEl.textContent = purchaseStatusToLabel(purchase.status);
    li.appendChild(badgeEl);

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

        if (list.length === 0) {
          showStatus(purchasesStatusEl, '구매한 상품이 없습니다.', 'empty');
          return;
        }
        hideStatus(purchasesStatusEl);
        renderPurchases(list);
      })
      .catch(function (err) {
        console.error('[buyer-mypage.js] failed to load purchases:', err);
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
      return '미성사(환불 처리됨)';
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
      // RECRUITING: 남은 유지 기간 + 현재 인원 상태
      var remainingText = formatRemaining(team.deadline);
      metaParts = [teamCountText(team), remainingText ? '마감까지 ' + remainingText : ''];
    }

    metaEl.textContent = metaParts.filter(Boolean).join(' · ');
    infoEl.appendChild(metaEl);

    li.appendChild(infoEl);

    var badgeEl = document.createElement('span');
    badgeEl.className = 'badge ' + teamStatusToBadgeClass(team.status);
    badgeEl.textContent = teamStatusToLabel(team.status);
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
    showStatus(teamsStatusEl, '공구 참여 목록을 불러오는 중입니다...', 'loading');
    clearChildren(teamsListEl);

    return window.Api.get('/buyer/mypage/teams')
      .then(function (teams) {
        var list = Array.isArray(teams) ? teams : [];
        if (list.length === 0) {
          showStatus(teamsStatusEl, '참여한 공구팀이 없습니다.', 'empty');
          return;
        }
        hideStatus(teamsStatusEl);
        renderTeams(list);
      })
      .catch(function (err) {
        console.error('[buyer-mypage.js] failed to load teams:', err);
        if (handleUnauthorized(err)) {
          return;
        }
        var message = (err && err.message) || '공구 참여 목록을 불러오지 못했습니다.';
        showStatus(teamsStatusEl, message, 'error');
      });
  }

  function init() {
    // purchases를 먼저 로드해 latestPurchases를 채운 뒤 teams를 로드해야
    // SUCCESS 항목의 결제 매칭(findMatchingPurchase)이 최신 데이터를 사용할 수 있다.
    loadPurchases().then(function () {
      loadTeams();
    });
  }

  init();
})();
