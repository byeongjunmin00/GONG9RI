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
 * - 다른 사용자의 참가로 팀 정원이 바뀌면 "/topic/products/{id}/teams" STOMP 브로드캐스트를 받아
 *   자동으로 팀 목록을 다시 그린다(connectRealtime, 실패해도 조용히 폴백 — 기존 흐름은 그대로 동작).
 * - 리뷰: GET /api/products/{id}/reviews로 목록+평균 평점을 불러온다(비로그인도 조회 가능).
 *   작성 폼은 항상 노출하고, 자격이 없으면(구매 안 함=REVIEW_NOT_ELIGIBLE, 이미 작성함=DUPLICATE_REVIEW,
 *   비로그인=UNAUTHORIZED) 서버 응답을 그대로 안내한다 — 클라이언트에서 미리 자격을 예측하지 않는다
 *   (서버가 SSOT라는 이 프로젝트의 기존 원칙과 동일). 본인이 쓴 리뷰에만 수정/삭제 버튼을 보여주는데,
 *   로그인한 회원 정보는 js/header-auth.js가 발행하는 'gong9ri:auth-resolved' 이벤트로 재사용한다
 *   (중복으로 /api/auth/me를 호출하지 않기 위함, js/chat-widget.js와 동일 패턴).
 */
(function () {
  var pageAlertEl = document.getElementById('page-alert');
  var pageAlertTextEl = document.getElementById('page-alert-text');
  var pageAlertLoginLinkEl = document.getElementById('page-alert-login-link');
  var pageAlertPayLinkEl = document.getElementById('page-alert-pay-link');
  var statusEl = document.getElementById('product-status');
  var detailEl = document.getElementById('product-detail');
  var imageEl = document.getElementById('product-image');

  var sellerEl = document.getElementById('product-seller');
  var nameEl = document.getElementById('product-name');
  var descriptionEl = document.getElementById('product-description');
  var basePriceEl = document.getElementById('product-base-price');
  var maxParticipantsEl = document.getElementById('product-max-participants');
  var priceTiersTableEl = document.getElementById('price-tiers-table');
  var priceTiersBodyEl = document.getElementById('price-tiers-body');
  var targetParticipantsFieldEl = document.getElementById('target-participants-field');
  var targetParticipantsOptionsEl = document.getElementById('target-participants-options');

  var buyAloneBtn = document.getElementById('buy-alone-btn');
  var createTeamBtn = document.getElementById('create-team-btn');

  var teamStatusEl = document.getElementById('team-status');
  var teamListEl = document.getElementById('team-list');

  var reviewAverageEl = document.getElementById('review-average');
  var reviewsStatusEl = document.getElementById('reviews-status');
  var reviewsListEl = document.getElementById('reviews-list');
  var reviewFormEl = document.getElementById('review-form');
  var reviewFormAlertEl = document.getElementById('review-form-alert');
  var reviewRatingEl = document.getElementById('review-rating');
  var reviewContentEl = document.getElementById('review-content');
  var reviewSubmitBtn = document.getElementById('review-submit');

  if (
    !pageAlertEl || !pageAlertTextEl || !pageAlertLoginLinkEl || !pageAlertPayLinkEl || !statusEl || !detailEl ||
    !imageEl ||
    !sellerEl || !nameEl || !descriptionEl || !basePriceEl || !maxParticipantsEl ||
    !priceTiersTableEl || !priceTiersBodyEl || !targetParticipantsFieldEl || !targetParticipantsOptionsEl ||
    !buyAloneBtn || !createTeamBtn ||
    !teamStatusEl || !teamListEl ||
    !reviewAverageEl || !reviewsStatusEl || !reviewsListEl || !reviewFormEl || !reviewFormAlertEl ||
    !reviewRatingEl || !reviewContentEl || !reviewSubmitBtn
  ) {
    return;
  }

  // init()에서 확정되면 이후 액션 핸들러들이 참조한다.
  var currentProductId = null;
  // 구매자가 라디오로 고른 목표 인원(price_tier.minCount). 아무 것도 안 고르면 null.
  var selectedTargetParticipants = null;
  // 'gong9ri:auth-resolved'로 채워진다. 비로그인이면 null — 리뷰 목록에서 "내가 쓴 리뷰"를 가려낼 때 쓴다.
  var currentMemberId = null;
  // 리뷰 폼이 "새로 작성" 모드인지 "기존 리뷰 수정" 모드인지 구분한다. null이면 작성 모드.
  var editingReviewId = null;

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
    if (showLoginLink) {
      pageAlertLoginLinkEl.href = '/login.html?redirect=' + encodeURIComponent(window.location.pathname + window.location.search);
    }

    if (payLinkHref) {
      pageAlertPayLinkEl.href = payLinkHref;
      pageAlertPayLinkEl.hidden = false;
    } else {
      pageAlertPayLinkEl.hidden = true;
    }

    pageAlertEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
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
    clearChildren(imageEl);
    if (product.imageUrl) {
      var imgEl = document.createElement('img');
      imgEl.src = product.imageUrl;
      imgEl.alt = product.name || '';
      imageEl.appendChild(imgEl);
    }

    sellerEl.textContent = product.sellerName || '';
    nameEl.textContent = product.name || '';
    descriptionEl.textContent = product.description || '';
    basePriceEl.textContent = formatPrice(product.basePrice);
    maxParticipantsEl.textContent =
      typeof product.maxParticipants === 'number' ? String(product.maxParticipants) : '';

    var tiers = Array.isArray(product.priceTiers) ? product.priceTiers : [];
    clearChildren(priceTiersBodyEl);
    renderTargetParticipantsOptions(tiers);

    if (tiers.length === 0) {
      priceTiersTableEl.hidden = true;
      return;
    }

    var fragment = document.createDocumentFragment();
    tiers.forEach(function (tier) {
      var row = document.createElement('tr');

      var countCell = document.createElement('td');
      countCell.textContent = (typeof tier.minCount === 'number' ? tier.minCount : '') + '명';
      row.appendChild(countCell);

      var priceCell = document.createElement('td');
      priceCell.textContent = formatPrice(tier.price);
      row.appendChild(priceCell);

      fragment.appendChild(row);
    });
    priceTiersBodyEl.appendChild(fragment);
    priceTiersTableEl.hidden = false;
  }

  /**
   * "신규 팀 신설하기"에 쓸 목표 인원(price_tier.minCount) 라디오 옵션을 그린다.
   * 아무것도 선택되지 않은 상태로 초기화하고, create-team-btn은 선택 전까지 비활성 상태를 유지한다.
   */
  function renderTargetParticipantsOptions(tiers) {
    selectedTargetParticipants = null;
    createTeamBtn.disabled = true;
    clearChildren(targetParticipantsOptionsEl);

    if (!tiers || tiers.length === 0) {
      targetParticipantsFieldEl.hidden = true;
      return;
    }

    var fragment = document.createDocumentFragment();
    tiers.forEach(function (tier, index) {
      if (typeof tier.minCount !== 'number') {
        return;
      }

      var optionId = 'target-participants-option-' + index;

      var label = document.createElement('label');
      label.className = 'target-participants-option';
      label.setAttribute('for', optionId);

      var input = document.createElement('input');
      input.type = 'radio';
      input.name = 'target-participants';
      input.id = optionId;
      input.value = String(tier.minCount);
      input.addEventListener('change', function () {
        selectedTargetParticipants = tier.minCount;
        createTeamBtn.disabled = false;
      });
      label.appendChild(input);

      var textEl = document.createElement('span');
      textEl.textContent = tier.minCount + '명';
      label.appendChild(textEl);

      fragment.appendChild(label);
    });

    targetParticipantsOptionsEl.appendChild(fragment);
    targetParticipantsFieldEl.hidden = false;
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
    if (selectedTargetParticipants === null) {
      showPageAlert('목표 인원을 먼저 선택해주세요.', 'error');
      return;
    }

    hidePageAlert();
    createTeamBtn.disabled = true;

    window.Api.post('/products/' + currentProductId + '/teams', { targetParticipants: selectedTargetParticipants })
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
        createTeamBtn.disabled = selectedTargetParticipants === null;
      });
  }

  function handleBuyAlone() {
    window.location.href = 'checkout.html?productId=' + currentProductId;
  }

  /**
   * 실시간 메시징(발제 도전과제) — 이 상품의 공구팀 정원이 바뀌면(다른 사용자의 참가) 서버가
   * "/topic/products/{productId}/teams"로 브로드캐스트한다. 세밀한 DOM 패치 대신 이미 있는
   * loadTeams()를 그대로 재사용해서 통째로 다시 그린다(기존 코드 스타일과 동일).
   * StompJs가 없거나(CDN 로드 실패) 연결이 실패해도 예외를 던지지 않고 조용히 넘어간다 —
   * 실시간 갱신이 안 될 뿐 기존 수동 새로고침 흐름은 그대로 동작한다.
   */
  function connectRealtime(productId) {
    if (typeof StompJs === 'undefined') {
      return;
    }

    var protocol = window.location.protocol === 'https:' ? 'wss://' : 'ws://';
    var client = new StompJs.Client({
      brokerURL: protocol + window.location.host + '/ws-team',
      reconnectDelay: 5000,
    });

    client.onConnect = function () {
      client.subscribe('/topic/products/' + productId + '/teams', function () {
        loadTeams(productId);
      });
    };

    client.activate();
  }

  // ---------- 리뷰 ----------

  function showReviewsStatus(text, variant) {
    reviewsStatusEl.hidden = false;
    reviewsStatusEl.textContent = text;
    reviewsStatusEl.className = 'product-status product-status--' + variant;
  }

  function hideReviewsStatus() {
    reviewsStatusEl.hidden = true;
    reviewsStatusEl.textContent = '';
  }

  function showReviewFormAlert(text, variant) {
    reviewFormAlertEl.hidden = false;
    reviewFormAlertEl.textContent = text;
    reviewFormAlertEl.className = 'form-alert form-alert--' + variant;
  }

  function hideReviewFormAlert() {
    reviewFormAlertEl.hidden = true;
    reviewFormAlertEl.textContent = '';
  }

  function resetReviewForm() {
    editingReviewId = null;
    reviewRatingEl.value = '5';
    reviewContentEl.value = '';
    reviewSubmitBtn.textContent = '리뷰 작성';
  }

  function startEditingReview(review) {
    editingReviewId = review.reviewId;
    reviewRatingEl.value = String(review.rating);
    reviewContentEl.value = review.content || '';
    reviewSubmitBtn.textContent = '리뷰 수정 저장';
    hideReviewFormAlert();
    reviewFormEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  function handleDeleteReview(reviewId) {
    if (!window.confirm('리뷰를 삭제할까요?')) {
      return;
    }
    window.Api.del('/reviews/' + reviewId)
      .then(function () {
        if (editingReviewId === reviewId) {
          resetReviewForm();
        }
        loadReviews(currentProductId);
      })
      .catch(function (err) {
        console.error('[product.js] failed to delete review:', err);
        window.alert((err && err.message) || '리뷰 삭제에 실패했습니다.');
      });
  }

  function createReviewItem(review) {
    var li = document.createElement('li');
    li.className = 'mypage-list-item';

    var infoEl = document.createElement('div');
    infoEl.className = 'mypage-list-item__info';

    var titleEl = document.createElement('span');
    titleEl.className = 'mypage-list-item__title';
    titleEl.textContent = review.memberName + ' · ' + review.rating + '점';
    infoEl.appendChild(titleEl);

    var metaEl = document.createElement('span');
    metaEl.className = 'mypage-list-item__meta';
    metaEl.textContent = review.content || '';
    infoEl.appendChild(metaEl);

    li.appendChild(infoEl);

    if (currentMemberId !== null && review.memberId === currentMemberId) {
      var actionsEl = document.createElement('div');
      actionsEl.className = 'mypage-list-item__actions';

      var editBtn = document.createElement('button');
      editBtn.type = 'button';
      editBtn.className = 'btn btn-secondary btn-sm';
      editBtn.textContent = '수정';
      editBtn.addEventListener('click', function () {
        startEditingReview(review);
      });
      actionsEl.appendChild(editBtn);

      var deleteBtn = document.createElement('button');
      deleteBtn.type = 'button';
      deleteBtn.className = 'btn btn-ghost btn-sm';
      deleteBtn.textContent = '삭제';
      deleteBtn.addEventListener('click', function () {
        handleDeleteReview(review.reviewId);
      });
      actionsEl.appendChild(deleteBtn);

      li.appendChild(actionsEl);
    }

    return li;
  }

  function renderReviews(data) {
    if (typeof data.averageRating === 'number') {
      reviewAverageEl.textContent = '평균 ' + data.averageRating.toFixed(1) + '점 (' + data.count + '개)';
    } else {
      reviewAverageEl.textContent = '';
    }

    clearChildren(reviewsListEl);
    var fragment = document.createDocumentFragment();
    (data.reviews || []).forEach(function (review) {
      fragment.appendChild(createReviewItem(review));
    });
    reviewsListEl.appendChild(fragment);
  }

  function loadReviews(productId) {
    showReviewsStatus('리뷰를 불러오는 중입니다...', 'loading');
    clearChildren(reviewsListEl);

    return window.Api.get('/products/' + productId + '/reviews')
      .then(function (data) {
        if (!data.reviews || data.reviews.length === 0) {
          reviewAverageEl.textContent = '';
          showReviewsStatus('아직 작성된 리뷰가 없습니다.', 'empty');
          return;
        }
        hideReviewsStatus();
        renderReviews(data);
      })
      .catch(function (err) {
        console.error('[product.js] failed to load reviews:', err);
        var message = (err && err.message) || '리뷰를 불러오지 못했습니다.';
        showReviewsStatus(message, 'error');
      });
  }

  function handleReviewFormError(err) {
    var status = err && err.status;
    var code = err && err.code;
    var message = (err && err.message) || '요청 처리 중 오류가 발생했습니다.';

    if (status === 401 || code === 'UNAUTHORIZED') {
      showReviewFormAlert('로그인 후 작성할 수 있습니다.', 'error');
      return;
    }
    showReviewFormAlert(message, 'error');
  }

  function handleReviewFormSubmit(event) {
    event.preventDefault();
    hideReviewFormAlert();

    var rating = Number(reviewRatingEl.value);
    var content = reviewContentEl.value.trim();
    var body = { rating: rating, content: content };

    reviewSubmitBtn.disabled = true;

    var request = editingReviewId
      ? window.Api.put('/reviews/' + editingReviewId, body)
      : window.Api.post('/products/' + currentProductId + '/reviews', body);

    request
      .then(function () {
        showReviewFormAlert(editingReviewId ? '리뷰를 수정했습니다.' : '리뷰를 작성했습니다.', 'success');
        resetReviewForm();
        loadReviews(currentProductId);
      })
      .catch(handleReviewFormError)
      .then(function () {
        reviewSubmitBtn.disabled = false;
      });
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
    reviewFormEl.addEventListener('submit', handleReviewFormSubmit);

    // header-auth.js가 이미 GET /api/auth/me를 호출하므로 그 결과를 재사용한다(중복 호출 방지).
    // 이 이벤트가 loadReviews()보다 늦게 올 수도 있어(비동기 순서 보장 없음), 도착하면 리뷰를
    // 다시 불러와서 "내가 쓴 리뷰"의 수정/삭제 버튼이 정확히 반영되게 한다.
    document.addEventListener('gong9ri:auth-resolved', function (event) {
      var detail = event.detail || {};
      currentMemberId = detail.loggedIn && detail.member ? detail.member.memberId : null;
      loadReviews(productId);
    });

    loadProduct(productId);
    loadReviews(productId);
    connectRealtime(productId);
  }

  init();
})();
