/**
 * seller-product-edit.js — 판매 물품 수정 페이지(seller/products/edit.html) 전용 스크립트
 *
 * 이 페이지는 static/ 루트가 아닌 서브디렉토리(/seller/products/edit.html)에 있으므로,
 * 페이지 이동은 반드시 절대경로("/seller/mypage.html")를 쓴다.
 *
 * - URL의 `id` 쿼리 파라미터가 없거나 양의 정수가 아니면 API 호출 없이 "잘못된 접근" 상태를 보여준다
 *   (product.html의 parseProductId와 동일한 방향).
 * - GET /api/products/{id}로 기존 값(name/description/basePrice/maxParticipants/priceTiers)을 불러와
 *   폼에 채운다. PRODUCT_NOT_FOUND(404)면 "상품을 찾을 수 없습니다" 안내로 전환하고 폼을 숨긴다.
 *   본인 상품인지 여부는 클라이언트에서 사전 판정하지 않는다(현재 로그인 사용자 조회 API가 없어
 *   비교 대상이 없다) — 최종 판정은 저장 시점의 서버 PUT 응답(403)이다.
 * - 가격 구간(priceTiers) 행 추가/삭제는 seller-product-new.js와 동일한 UX 가드레일을 이 파일
 *   안에 독립적으로 구현한다(완료된 등록 페이지 파일은 건드리지 않는다).
 * - "저장하기"(PUT /api/products/{id}) 성공(200) 시 seller/mypage.html(절대경로)로 이동한다.
 *   실패: 400(공통 에러 배너) / 401(로그인 필요 안내 + 로그인 링크) / 403(서버 message) / 404(서버 message).
 * - "취소하기"는 API 호출 없이 정적 링크(<a href="/seller/mypage.html">)로 처리한다.
 * - 서버 에러 message 등 신뢰할 수 없는 문자열은 textContent로만 대입해 XSS를 방지한다.
 */
(function () {
  var formAlertEl = document.getElementById('form-alert');
  var formAlertTextEl = document.getElementById('form-alert-text');
  var formAlertLoginLinkEl = document.getElementById('form-alert-login-link');

  var editStatusEl = document.getElementById('edit-status');

  var form = document.getElementById('product-form');
  var nameInput = document.getElementById('name');
  var categorySelect = document.getElementById('category');
  var descriptionInput = document.getElementById('description');
  var basePriceInput = document.getElementById('basePrice');
  var maxParticipantsInput = document.getElementById('maxParticipants');
  var imageUrlInput = document.getElementById('imageUrl');
  var openAtInput = document.getElementById('openAt');

  var priceTierRowsEl = document.getElementById('price-tier-rows');
  var addPriceTierBtn = document.getElementById('add-price-tier-btn');
  var priceTiersErrorEl = document.getElementById('price-tiers-error');
  var autoRefundOnCancelInput = document.getElementById('auto-refund-on-cancel');

  var submitBtn = document.getElementById('submit-btn');

  if (
    !formAlertEl || !formAlertTextEl || !formAlertLoginLinkEl || !editStatusEl ||
    !form || !nameInput || !categorySelect || !descriptionInput || !basePriceInput || !maxParticipantsInput || !imageUrlInput ||
    !openAtInput ||
    !priceTierRowsEl || !addPriceTierBtn || !priceTiersErrorEl || !autoRefundOnCancelInput || !submitBtn
  ) {
    return;
  }

  /**
   * <input type="datetime-local">의 값(초 없는 "YYYY-MM-DDTHH:mm")을 서버 LocalDateTime
   * 역직렬화가 받는 초 단위 ISO 문자열로 보정한다. 비어있으면 null(오픈예정 없음, 즉시 공개).
   */
  function toOpenAtPayload(value) {
    if (!value) {
      return null;
    }
    return value.length === 16 ? value + ':00' : value;
  }

  // init()에서 확정되면 이후 제출 핸들러가 참조한다.
  var currentProductId = null;

  function showAlert(text, showLoginLink) {
    formAlertEl.hidden = false;
    formAlertEl.className = 'form-alert form-alert--error';
    formAlertTextEl.textContent = text;
    formAlertLoginLinkEl.hidden = !showLoginLink;
    if (showLoginLink) {
      formAlertLoginLinkEl.href = '/login.html?redirect=' + encodeURIComponent(window.location.pathname + window.location.search);
    }
    formAlertEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  function hideAlert() {
    formAlertEl.hidden = true;
    formAlertTextEl.textContent = '';
    formAlertLoginLinkEl.hidden = true;
  }

  function showEditStatus(text, variant) {
    editStatusEl.hidden = false;
    editStatusEl.textContent = text;
    editStatusEl.className = 'product-status product-status--' + variant;
  }

  function hideEditStatus() {
    editStatusEl.hidden = true;
    editStatusEl.textContent = '';
  }

  function showPriceTiersError(text) {
    priceTiersErrorEl.hidden = false;
    priceTiersErrorEl.textContent = text;
  }

  function hidePriceTiersError() {
    priceTiersErrorEl.hidden = true;
    priceTiersErrorEl.textContent = '';
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

  /**
   * 가격 구간 행이 1개뿐이면 그 행의 삭제 버튼을 숨긴다(마지막 1행 삭제 불가).
   * 2개 이상이면 모든 행의 삭제 버튼을 보여준다.
   */
  function updateRemoveButtonsVisibility() {
    var rows = priceTierRowsEl.querySelectorAll('.price-tier-row');
    var onlyOneRow = rows.length <= 1;
    rows.forEach(function (row) {
      var removeBtn = row.querySelector('.price-tier-row__remove');
      if (removeBtn) {
        removeBtn.hidden = onlyOneRow;
      }
    });
  }

  function createPriceTierRow(initialMinCount, initialPrice) {
    var row = document.createElement('div');
    row.className = 'price-tier-row';

    var minCountGroup = document.createElement('div');
    minCountGroup.className = 'form-group price-tier-row__field';
    var minCountLabel = document.createElement('label');
    minCountLabel.className = 'form-label';
    minCountLabel.textContent = '최소 인원';
    var minCountInput = document.createElement('input');
    minCountInput.className = 'form-input';
    minCountInput.type = 'number';
    minCountInput.min = '1';
    minCountInput.step = '1';
    minCountInput.placeholder = '예: 2';
    minCountInput.setAttribute('data-field', 'minCount');
    if (typeof initialMinCount === 'number') {
      minCountInput.value = String(initialMinCount);
    }
    minCountGroup.appendChild(minCountLabel);
    minCountGroup.appendChild(minCountInput);

    var priceGroup = document.createElement('div');
    priceGroup.className = 'form-group price-tier-row__field';
    var priceLabel = document.createElement('label');
    priceLabel.className = 'form-label';
    priceLabel.textContent = '1인당 가격';
    var priceInput = document.createElement('input');
    priceInput.className = 'form-input';
    priceInput.type = 'number';
    priceInput.min = '0';
    priceInput.step = '1';
    priceInput.placeholder = '예: 18000';
    priceInput.setAttribute('data-field', 'price');
    if (typeof initialPrice === 'number') {
      priceInput.value = String(initialPrice);
    }
    priceGroup.appendChild(priceLabel);
    priceGroup.appendChild(priceInput);

    var removeBtn = document.createElement('button');
    removeBtn.type = 'button';
    removeBtn.className = 'btn btn-ghost btn-sm price-tier-row__remove';
    removeBtn.textContent = '삭제';
    removeBtn.setAttribute('aria-label', '이 가격 구간 삭제');
    removeBtn.addEventListener('click', function () {
      removePriceTierRow(row);
    });

    row.appendChild(minCountGroup);
    row.appendChild(priceGroup);
    row.appendChild(removeBtn);

    return row;
  }

  function addPriceTierRow(initialMinCount, initialPrice) {
    priceTierRowsEl.appendChild(createPriceTierRow(initialMinCount, initialPrice));
    updateRemoveButtonsVisibility();
  }

  function removePriceTierRow(row) {
    var rows = priceTierRowsEl.querySelectorAll('.price-tier-row');
    if (rows.length <= 1) {
      return; // 최소 1행은 유지한다.
    }
    priceTierRowsEl.removeChild(row);
    updateRemoveButtonsVisibility();
  }

  /**
   * @returns {{tiers: Array<{minCount: number, price: number}>|null, message: string|null}}
   *   tiers가 null이면 message에 담긴 사유로 제출을 막아야 한다.
   */
  function collectPriceTiers() {
    var rows = priceTierRowsEl.querySelectorAll('.price-tier-row');
    var tiers = [];

    for (var i = 0; i < rows.length; i++) {
      var minCountInput = rows[i].querySelector('[data-field="minCount"]');
      var priceInput = rows[i].querySelector('[data-field="price"]');
      var minCountRaw = minCountInput ? minCountInput.value.trim() : '';
      var priceRaw = priceInput ? priceInput.value.trim() : '';

      if (!minCountRaw || !priceRaw) {
        return { tiers: null, message: '가격 구간의 최소 인원과 가격을 모두 입력해주세요.' };
      }

      var minCount = Number(minCountRaw);
      var price = Number(priceRaw);

      if (!Number.isInteger(minCount) || !Number.isInteger(price) || minCount < 1 || price < 0) {
        return { tiers: null, message: '가격 구간의 값이 올바르지 않습니다.' };
      }

      tiers.push({ minCount: minCount, price: price });
    }

    return { tiers: tiers, message: null };
  }

  /**
   * 서버가 강제하지 않는 부분(오름차순/중복/범위)을 UX로 보완하는 가드레일. SSOT는 서버 응답이다.
   * @param {Array<{minCount: number, price: number}>} tiers
   * @param {number} maxParticipants
   * @returns {string|null} 문제가 있으면 안내 문구, 없으면 null
   */
  function validatePriceTiersGuardrail(tiers, maxParticipants) {
    var seen = {};
    var previousMinCount = null;

    for (var i = 0; i < tiers.length; i++) {
      var tier = tiers[i];

      if (seen[tier.minCount]) {
        return '가격 구간의 최소 인원은 중복될 수 없습니다.';
      }
      seen[tier.minCount] = true;

      if (previousMinCount !== null && tier.minCount <= previousMinCount) {
        return '가격 구간은 최소 인원 오름차순으로 입력해주세요.';
      }
      previousMinCount = tier.minCount;

      if (tier.minCount < 2) {
        return '가격 구간의 최소 인원은 2명 이상을 권장합니다.';
      }

      if (typeof maxParticipants === 'number' && tier.minCount > maxParticipants) {
        return '가격 구간의 최소 인원은 팀 최대 인원 이하여야 합니다.';
      }
    }

    return null;
  }

  function fillForm(product) {
    nameInput.value = product.name || '';
    categorySelect.value = product.category || 'ETC';
    descriptionInput.value = product.description || '';
    basePriceInput.value = typeof product.basePrice === 'number' ? String(product.basePrice) : '';
    maxParticipantsInput.value =
      typeof product.maxParticipants === 'number' ? String(product.maxParticipants) : '';
    imageUrlInput.value = product.imageUrl || '';
    openAtInput.value = product.openAt ? product.openAt.slice(0, 16) : '';
    autoRefundOnCancelInput.checked = Boolean(product.autoRefundOnCancel);

    var tiers = Array.isArray(product.priceTiers) ? product.priceTiers : [];
    if (tiers.length === 0) {
      addPriceTierRow();
      return;
    }

    tiers.forEach(function (tier) {
      addPriceTierRow(tier.minCount, tier.price);
    });
  }

  function loadProduct(productId) {
    showEditStatus('상품 정보를 불러오는 중입니다...', 'loading');
    form.hidden = true;

    return window.Api.get('/products/' + productId)
      .then(function (product) {
        fillForm(product);
        hideEditStatus();
        form.hidden = false;
      })
      .catch(function (err) {
        console.error('[seller-product-edit.js] failed to load product:', err);
        if (err && err.code === 'PRODUCT_NOT_FOUND') {
          showEditStatus('상품을 찾을 수 없습니다.', 'error');
        } else {
          var message = (err && err.message) || '상품 정보를 불러오지 못했습니다.';
          showEditStatus(message, 'error');
        }
      });
  }

  function handleSubmitError(err) {
    console.error('[seller-product-edit.js] product update failed:', err);

    var status = err && err.status;
    var code = err && err.code;
    var message = (err && err.message) || '상품 수정에 실패했습니다. 잠시 후 다시 시도해주세요.';

    if (status === 401 || code === 'UNAUTHORIZED') {
      showAlert('로그인이 필요합니다.', true);
      return;
    }

    // 403(FORBIDDEN, 본인 상품 아님/구매자 계정) / 404(PRODUCT_NOT_FOUND) / 400(VALIDATION_FAILED)
    // 등 나머지는 서버 message를 그대로 안내한다.
    showAlert(message, false);
  }

  form.addEventListener('submit', function (event) {
    event.preventDefault();
    hideAlert();
    hidePriceTiersError();

    var name = nameInput.value.trim();
    var description = descriptionInput.value.trim();
    var basePriceRaw = basePriceInput.value.trim();
    var maxParticipantsRaw = maxParticipantsInput.value.trim();
    var imageUrl = imageUrlInput.value.trim();

    // UX 보조용 최소 필수값 체크. 실제 판정 기준(SSOT)은 서버 응답(VALIDATION_FAILED)이다.
    if (!name || !basePriceRaw || !maxParticipantsRaw) {
      showAlert('상품명, 정가, 팀 최대 인원을 모두 입력해주세요.');
      return;
    }

    var basePrice = Number(basePriceRaw);
    var maxParticipants = Number(maxParticipantsRaw);

    if (!Number.isInteger(basePrice) || basePrice < 0 || !Number.isInteger(maxParticipants) || maxParticipants < 2) {
      showAlert('정가와 팀 최대 인원 값이 올바르지 않습니다.');
      return;
    }

    var collected = collectPriceTiers();
    if (collected.tiers === null) {
      showPriceTiersError(collected.message);
      return;
    }

    var guardrailMessage = validatePriceTiersGuardrail(collected.tiers, maxParticipants);
    if (guardrailMessage) {
      showPriceTiersError(guardrailMessage);
      return;
    }

    submitBtn.disabled = true;

    window.Api.put('/products/' + currentProductId, {
      name: name,
      description: description,
      basePrice: basePrice,
      maxParticipants: maxParticipants,
      priceTiers: collected.tiers,
      imageUrl: imageUrl || null,
      autoRefundOnCancel: autoRefundOnCancelInput.checked,
      category: categorySelect.value,
      openAt: toOpenAtPayload(openAtInput.value),
    })
      .then(function () {
        window.location.href = '/seller/mypage.html';
      })
      .catch(function (err) {
        submitBtn.disabled = false;
        handleSubmitError(err);
      });
  });

  addPriceTierBtn.addEventListener('click', function () {
    addPriceTierRow();
  });

  function init() {
    var productId = parseProductId();

    if (productId === null) {
      showEditStatus('잘못된 접근입니다. 올바른 상품 수정 링크로 다시 시도해주세요.', 'error');
      return;
    }

    currentProductId = productId;
    loadProduct(productId);
  }

  init();
})();
