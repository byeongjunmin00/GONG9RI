/**
 * product.js — 상품 상세 페이지(product.html) 전용 스크립트
 *
 * 라우팅: 정적 리소스 서빙 방식이라 `/products/{id}` 경로 세그먼트를 매핑할 서버 라우팅이 없어
 * 쿼리스트링(`product.html?id={productId}`)으로 상품 ID를 받는다(design.md 참고).
 *
 * - URL의 `id` 쿼리 파라미터가 없거나 양의 정수가 아니면 API 호출 없이 "잘못된 접근" 상태를 보여준다.
 * - GET /api/products/{id} → 상품 상세(이름/설명/기본가/최대인원/priceTiers) 렌더링.
 *   PRODUCT_NOT_FOUND(404)면 "상품을 찾을 수 없습니다" 안내로 전환한다.
 * - GET /api/products/{id}/teams → 모집중 팀 목록 렌더링. 빈 배열이면 빈 상태 안내(에러 아님).
 * - "기존 팀 참가하기"(POST /api/teams/{teamId}/join), "신규 팀 신설하기"(POST /api/products/{id}/teams)는
 *   성공 시 안내 배너 + 팀 목록 재조회, 배너에 결제(checkout.html)로 이동하는 링크를 추가로 노출한다
 *   (자동 리다이렉트는 하지 않는다). 401/403/409/404는 에러 코드별로 처리한다.
 * - "혼자 구매하기"는 checkout.html?productId={id}로 이동한다(API 호출 없음).
 * - 상품명/설명/판매자명/서버 에러 message 등 신뢰할 수 없는 문자열은 textContent로만 대입해 XSS를 방지한다.
 */
(function () {
  var pageAlertEl = document.getElementById('page-alert');
  var pageAlertTextEl = document.getElementById('page-alert-text');
  var pageAlertLoginLinkEl = document.getElementById('page-alert-login-link');
  var pageAlertPayLinkEl = document.getElementById('page-alert-pay-link');
  var statusEl = document.getElementById('product-status');
  var detailEl = document.getElementById('product-detail');

  var sellerEl = document.getElementById('product-seller');
  var nameEl = document.getElementById('product-name');
  var descriptionEl = document.getElementById('product-description');
  var basePriceEl = document.getElementById('product-base-price');
  var maxParticipantsEl = document.getElementById('product-max-participants');
  var priceTiersTableEl = document.getElementById('price-tiers-table');
  var priceTiersBodyEl = document.getElementById('price-tiers-body');

  var buyAloneBtn = document.getElementById('buy-alone-btn');
  var createTeamBtn = document.getElementById('create-team-btn');

  var teamStatusEl = document.getElementById('team-status');
  var teamListEl = document.getElementById('team-list');

  if (
    !pageAlertEl || !pageAlertTextEl || !pageAlertLoginLinkEl || !pageAlertPayLinkEl || !statusEl || !detailEl ||
    !sellerEl || !nameEl || !descriptionEl || !basePriceEl || !maxParticipantsEl ||
    !priceTiersTableEl || !priceTiersBodyEl || !buyAloneBtn || !createTeamBtn ||
    !teamStatusEl || !teamListEl
  ) {
    return;
  }

  // init()에서 확정되면 이후 액션 핸들러들이 참조한다.
  var currentProductId = null;

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

  /**
   * @param {string} text
   * @param {string} variant  'success' | 'error'
   * @param {boolean} [showLoginLink]  401 처리 시 로그인 페이지 링크 노출
   * @param {string} [payLinkHref]  신설/참가 성공 시 결제로 이동하는 링크 href
   *   (checkout.html?productId=...&teamId=...). 없으면 링크를 숨긴다.
   */
  function showPageAlert(text, variant, showLoginLink, payLinkHref) {
    pageAlertEl.hidden = false;
    pageAlertEl.className = 'form-alert form-alert--' + variant;
    pageAlertTextEl.textContent = text;
    pageAlertLoginLinkEl.hidden = !showLoginLink;

    if (payLinkHref) {
      pageAlertPayLinkEl.href = payLinkHref;
      pageAlertPayLinkEl.hidden = false;
    } else {
      pageAlertPayLinkEl.hidden = true;
    }
  }

  function hidePageAlert() {
    pageAlertEl.hidden = true;
    pageAlertTextEl.textContent = '';
    pageAlertLoginLinkEl.hidden = true;
    pageAlertPayLinkEl.hidden = true;
  }

  function showStatus(text, variant) {
    statusEl.hidden = false;
    statusEl.textContent = text;
    statusEl.className = 'product-status product-status--' + variant;
  }

  function hideStatus() {
    statusEl.hidden = true;
    statusEl.textContent = '';
  }

  function showTeamStatus(text, variant) {
    teamStatusEl.hidden = false;
    teamStatusEl.textContent = text;
    teamStatusEl.className = 'product-status product-status--' + variant;
  }

  function hideTeamStatus() {
    teamStatusEl.hidden = true;
    teamStatusEl.textContent = '';
  }

  /**
   * @returns {number|null} 유효한 양의 정수 ID, 없거나 형식이 잘못되면 null
   */
  function parseProductId() {
    var params = new URLSearchParams(window.location.search);
    var raw = params.get('id');
    if (!raw || !/^[1-9]\d*$/.test(raw)) {
      return null;
    }
    return Number(raw);
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

  function renderProduct(product) {
    sellerEl.textContent = product.sellerName || '';
    nameEl.textContent = product.name || '';
    descriptionEl.textContent = product.description || '';
    basePriceEl.textContent = formatPrice(product.basePrice);
    maxParticipantsEl.textContent =
      typeof product.maxParticipants === 'number' ? String(product.maxParticipants) : '';

    var tiers = Array.isArray(product.priceTiers) ? product.priceTiers : [];
    clearChildren(priceTiersBodyEl);

    if (tiers.length === 0) {
      priceTiersTableEl.hidden = true;
      return;
    }

    var fragment = document.createDocumentFragment();
    tiers.forEach(function (tier) {
      var row = document.createElement('tr');

      var countCell = document.createElement('td');
      countCell.textContent = (typeof tier.minCount === 'number' ? tier.minCount : '') + '명 이상';
      row.appendChild(countCell);

      var priceCell = document.createElement('td');
      priceCell.textContent = formatPrice(tier.price);
      row.appendChild(priceCell);

      fragment.appendChild(row);
    });
    priceTiersBodyEl.appendChild(fragment);
    priceTiersTableEl.hidden = false;
  }

  function createTeamItem(team) {
    var li = document.createElement('li');
    li.className = 'team-item';

    var infoEl = document.createElement('div');
    infoEl.className = 'team-item-info';

    var badgeEl = document.createElement('span');
    badgeEl.className = 'badge ' + statusToBadgeClass(team.status);
    badgeEl.textContent = statusToLabel(team.status);
    infoEl.appendChild(badgeEl);

    var countEl = document.createElement('span');
    countEl.className = 'team-item-count';
    var current = typeof team.currentCount === 'number' ? team.currentCount : '?';
    var max = typeof team.maxParticipants === 'number' ? team.maxParticipants : '?';
    countEl.textContent = current + ' / ' + max + '명';
    infoEl.appendChild(countEl);

    li.appendChild(infoEl);

    var joinBtn = document.createElement('button');
    joinBtn.type = 'button';
    joinBtn.className = 'btn btn-secondary btn-sm';
    joinBtn.textContent = '참가하기';
    joinBtn.addEventListener('click', function () {
      handleJoin(team.teamId, joinBtn);
    });
    li.appendChild(joinBtn);

    return li;
  }

  function renderTeams(teams) {
    clearChildren(teamListEl);
    var fragment = document.createDocumentFragment();
    teams.forEach(function (team) {
      fragment.appendChild(createTeamItem(team));
    });
    teamListEl.appendChild(fragment);
  }

  function loadTeams(productId) {
    showTeamStatus('공구팀 목록을 불러오는 중입니다...', 'loading');
    clearChildren(teamListEl);

    return window.Api.get('/products/' + productId + '/teams')
      .then(function (teams) {
        var list = Array.isArray(teams) ? teams : [];
        if (list.length === 0) {
          showTeamStatus('아직 모집 중인 공구팀이 없습니다.', 'empty');
          return;
        }
        hideTeamStatus();
        renderTeams(list);
      })
      .catch(function (err) {
        console.error('[product.js] failed to load teams:', err);
        var message = (err && err.message) || '공구팀 목록을 불러오지 못했습니다.';
        showTeamStatus(message, 'error');
      });
  }

  /**
   * 참가/신설 공통 에러 처리. 계획 문서의 코드별 매핑을 그대로 따른다.
   */
  function handleActionError(err, productId) {
    console.error('[product.js] action failed:', err);

    var status = err && err.status;
    var code = err && err.code;
    var message = (err && err.message) || '요청 처리 중 오류가 발생했습니다.';

    if (status === 401 || code === 'UNAUTHORIZED') {
      showPageAlert('로그인이 필요합니다.', 'error', true);
      return;
    }

    if (status === 403 || code === 'FORBIDDEN') {
      showPageAlert(message, 'error');
      return;
    }

    if (status === 409 && (code === 'TEAM_FULL' || code === 'ALREADY_JOINED')) {
      showPageAlert(message, 'error');
      loadTeams(productId);
      return;
    }

    if (status === 404) {
      showPageAlert(message, 'error');
      loadTeams(productId);
      return;
    }

    showPageAlert(message, 'error');
  }

  function handleJoin(teamId, joinBtn) {
    hidePageAlert();
    joinBtn.disabled = true;

    window.Api.post('/teams/' + teamId + '/join')
      .then(function () {
        var payLink = 'checkout.html?productId=' + currentProductId + '&teamId=' + teamId;
        showPageAlert('공구팀에 참가했습니다.', 'success', false, payLink);
        loadTeams(currentProductId);
      })
      .catch(function (err) {
        joinBtn.disabled = false;
        handleActionError(err, currentProductId);
      });
  }

  function handleCreateTeam() {
    hidePageAlert();
    createTeamBtn.disabled = true;

    window.Api.post('/products/' + currentProductId + '/teams')
      .then(function (team) {
        var newTeamId = team && team.teamId;
        var payLink = 'checkout.html?productId=' + currentProductId + '&teamId=' + newTeamId;
        showPageAlert('신규 공구팀을 만들었습니다.', 'success', false, payLink);
        loadTeams(currentProductId);
      })
      .catch(function (err) {
        handleActionError(err, currentProductId);
      })
      .then(function () {
        createTeamBtn.disabled = false;
      });
  }

  function handleBuyAlone() {
    window.location.href = 'checkout.html?productId=' + currentProductId;
  }

  function loadProduct(productId) {
    showStatus('상품 정보를 불러오는 중입니다...', 'loading');
    detailEl.hidden = true;

    return window.Api.get('/products/' + productId)
      .then(function (product) {
        hideStatus();
        renderProduct(product);
        detailEl.hidden = false;
        return loadTeams(productId);
      })
      .catch(function (err) {
        console.error('[product.js] failed to load product:', err);
        if (err && err.code === 'PRODUCT_NOT_FOUND') {
          showStatus('상품을 찾을 수 없습니다.', 'error');
        } else {
          var message = (err && err.message) || '상품 정보를 불러오지 못했습니다.';
          showStatus(message, 'error');
        }
      });
  }

  function init() {
    var productId = parseProductId();

    if (productId === null) {
      showStatus('잘못된 접근입니다. 올바른 상품 링크로 다시 시도해주세요.', 'error');
      return;
    }

    currentProductId = productId;

    buyAloneBtn.addEventListener('click', handleBuyAlone);
    createTeamBtn.addEventListener('click', handleCreateTeam);

    loadProduct(productId);
  }

  init();
})();
