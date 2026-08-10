/**
 * checkout.js — 결제창 페이지(checkout.html) 전용 스크립트
 *
 * 라우팅: 정적 리소스 서빙 방식이라 쿼리스트링으로 상품/팀 ID를 받는다(design.md 참고).
 *   - `checkout.html?productId={productId}` — 혼자구매
 *   - `checkout.html?productId={productId}&teamId={teamId}` — 공구팀 결제
 * 파라미터명은 POST /api/payments의 요청 필드명(productId/teamId)과 동일하게 맞췄다
 * (product.html의 `id`와는 다른 이름이라 혼동 소지가 있음, design.md 리스크 참고).
 *
 * - `productId`가 없거나 양의 정수가 아니면, `teamId`가 있는데 양의 정수가 아니면
 *   API 호출 없이 "잘못된 접근" 상태를 보여준다.
 * - GET /api/products/{productId} → 상품 정보(판매자명/상품명/basePrice/priceTiers) 렌더링.
 *   PRODUCT_NOT_FOUND(404)면 "상품을 찾을 수 없습니다" 안내로 전환하고 결제 영역을 숨긴다.
 * - teamId가 없으면 basePrice를 최종 결제 금액으로 표시, 있으면 basePrice/priceTiers를
 *   참고 정보로만 표시한다 — 실제 확정 금액은 결제 응답(amount)이 유일한 소스다.
 * - teamId의 존재 여부/정원 상태는 이 페이지에서 사전 검증하지 않는다
 *   (POST /api/payments 응답의 TEAM_NOT_FOUND/TEAM_FULL로만 사후에 판정).
 * - "결제하기"(POST /api/payments) 성공 시 결제 완료 요약을 보여주고 결제 영역을 숨긴다.
 *   실패는 코드별(400/401/403/404/409)로 안내를 분기한다.
 * - "삭제(취소)"는 API 호출 없이 상품 상세 페이지(product.html?id=)로 돌아간다.
 * - 상품명/판매자명/서버 에러 message 등 신뢰할 수 없는 문자열은 textContent로만 대입해 XSS를 방지한다.
 */
(function () {
  var pageAlertEl = document.getElementById('page-alert');
  var pageAlertTextEl = document.getElementById('page-alert-text');
  var pageAlertLoginLinkEl = document.getElementById('page-alert-login-link');

  var statusEl = document.getElementById('checkout-status');

  var detailEl = document.getElementById('checkout-detail');
  var sellerEl = document.getElementById('checkout-seller');
  var nameEl = document.getElementById('checkout-name');
  var amountLabelEl = document.getElementById('checkout-amount-label');
  var amountEl = document.getElementById('checkout-amount');
  var teamNoticeEl = document.getElementById('checkout-team-notice');
  var priceTiersTableEl = document.getElementById('checkout-price-tiers-table');
  var priceTiersBodyEl = document.getElementById('checkout-price-tiers-body');

  var payBtn = document.getElementById('pay-btn');
  var cancelBtn = document.getElementById('cancel-btn');

  var summaryEl = document.getElementById('checkout-summary');
  var summaryProductNameEl = document.getElementById('summary-product-name');
  var summaryAmountEl = document.getElementById('summary-amount');
  var summaryStatusEl = document.getElementById('summary-status');
  var summaryPaidAtEl = document.getElementById('summary-paid-at');

  if (
    !pageAlertEl || !pageAlertTextEl || !pageAlertLoginLinkEl || !statusEl ||
    !detailEl || !sellerEl || !nameEl || !amountLabelEl || !amountEl ||
    !teamNoticeEl || !priceTiersTableEl || !priceTiersBodyEl ||
    !payBtn || !cancelBtn ||
    !summaryEl || !summaryProductNameEl || !summaryAmountEl || !summaryStatusEl || !summaryPaidAtEl
  ) {
    return;
  }

  // init()에서 확정되면 이후 액션 핸들러들이 참조한다.
  var currentProductId = null;
  var currentTeamId = null; // null이면 혼자구매

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

  function showPageAlert(text, variant, showLoginLink) {
    pageAlertEl.hidden = false;
    pageAlertEl.className = 'form-alert form-alert--' + variant;
    pageAlertTextEl.textContent = text;
    pageAlertLoginLinkEl.hidden = !showLoginLink;
    pageAlertEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  function hidePageAlert() {
    pageAlertEl.hidden = true;
    pageAlertTextEl.textContent = '';
    pageAlertLoginLinkEl.hidden = true;
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

  /**
   * @param {string} raw
   * @returns {number|null} 유효한 양의 정수 ID, 형식이 잘못되면 null
   */
  function parsePositiveIntParam(raw) {
    if (!raw || !/^[1-9]\d*$/.test(raw)) {
      return null;
    }
    return Number(raw);
  }

  /**
   * @returns {{valid: boolean, productId: number|null, teamId: number|null}}
   *   productId는 필수 — 없거나 형식이 잘못되면 valid=false.
   *   teamId는 선택 — 파라미터가 없으면 teamId=null(혼자구매), 있는데 형식이 잘못되면 valid=false.
   */
  function parseParams() {
    var params = new URLSearchParams(window.location.search);

    var productId = parsePositiveIntParam(params.get('productId'));
    if (productId === null) {
      return { valid: false, productId: null, teamId: null };
    }

    var teamIdRaw = params.get('teamId');
    if (!teamIdRaw) {
      return { valid: true, productId: productId, teamId: null };
    }

    var teamId = parsePositiveIntParam(teamIdRaw);
    if (teamId === null) {
      return { valid: false, productId: null, teamId: null };
    }

    return { valid: true, productId: productId, teamId: teamId };
  }

  function renderPriceTiers(product) {
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

  function renderProduct(product, teamId) {
    sellerEl.textContent = product.sellerName || '';
    nameEl.textContent = product.name || '';
    amountEl.textContent = formatPrice(product.basePrice);

    if (teamId === null) {
      amountLabelEl.textContent = '결제 금액';
      teamNoticeEl.hidden = true;
      priceTiersTableEl.hidden = true;
      clearChildren(priceTiersBodyEl);
      return;
    }

    amountLabelEl.textContent = '정가 (참고)';
    teamNoticeEl.hidden = false;
    renderPriceTiers(product);
  }

  function loadProduct(productId, teamId) {
    showStatus('상품 정보를 불러오는 중입니다...', 'loading');
    detailEl.hidden = true;

    return window.Api.get('/products/' + productId)
      .then(function (product) {
        hideStatus();
        renderProduct(product, teamId);
        detailEl.hidden = false;
      })
      .catch(function (err) {
        console.error('[checkout.js] failed to load product:', err);
        if (err && err.code === 'PRODUCT_NOT_FOUND') {
          showStatus('상품을 찾을 수 없습니다.', 'error');
        } else {
          var message = (err && err.message) || '상품 정보를 불러오지 못했습니다.';
          showStatus(message, 'error');
        }
      });
  }

  function showSummary(payment) {
    detailEl.hidden = true;
    summaryProductNameEl.textContent = payment.productName || '';
    summaryAmountEl.textContent = formatPrice(payment.amount);
    summaryStatusEl.textContent = payment.status || '';
    summaryPaidAtEl.textContent = payment.paidAt || '';
    summaryEl.hidden = false;
  }

  /**
   * 결제 실패 공통 처리. design.md의 코드별 매핑을 그대로 따른다.
   */
  function handlePaymentError(err) {
    console.error('[checkout.js] payment failed:', err);

    var status = err && err.status;
    var code = err && err.code;
    var message = (err && err.message) || '결제 처리 중 오류가 발생했습니다.';

    if (status === 401 || code === 'UNAUTHORIZED') {
      showPageAlert('로그인이 필요합니다.', 'error', true);
      return;
    }

    showPageAlert(message, 'error');
  }

  function handlePay() {
    hidePageAlert();
    payBtn.disabled = true;

    var body = { productId: currentProductId };
    if (currentTeamId !== null) {
      body.teamId = currentTeamId;
    }

    window.Api.post('/payments', body)
      .then(function (payment) {
        showSummary(payment);
      })
      .catch(function (err) {
        payBtn.disabled = false;
        handlePaymentError(err);
      });
  }

  function handleCancel() {
    window.location.href = 'product.html?id=' + currentProductId;
  }

  function init() {
    var parsed = parseParams();

    if (!parsed.valid) {
      showStatus('잘못된 접근입니다. 올바른 결제 링크로 다시 시도해주세요.', 'error');
      return;
    }

    currentProductId = parsed.productId;
    currentTeamId = parsed.teamId;

    payBtn.addEventListener('click', handlePay);
    cancelBtn.addEventListener('click', handleCancel);

    loadProduct(currentProductId, currentTeamId);
  }

  init();
})();
