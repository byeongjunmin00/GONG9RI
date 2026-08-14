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
 * - 각 팀 항목의 "참여자 보기"를 누르면 그때 GET /api/teams/{teamId}/participants를 개별 조회해
 *   마스킹된 이름·팀장 배지·대략적 참여 시각을 펼쳐 보여준다(팀 목록 로드 시점에 한꺼번에 불러오지 않음).
 * - "팀 성사 후 환불 불가" 체크박스(#refund-notice-checkbox)를 체크하지 않으면 "신규 팀 신설하기"와
 *   각 팀의 "참가하기" 버튼이 비활성 상태를 유지한다(명시적 확인 없이는 팀 신설/참가를 진행할 수 없게
 *   하는 가드 — 기존 목표 인원 라디오 가드와 같은 방식). "혼자 구매하기"는 이 제약과 무관하다(솔로
 *   구매는 환불 불가 규칙의 대상이 아니라서).
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
  var refundNoticeCheckboxEl = document.getElementById('refund-notice-checkbox');

  var buyAloneBtn = document.getElementById('buy-alone-btn');
  var createTeamBtn = document.getElementById('create-team-btn');

  var teamStatusEl = document.getElementById('team-status');
  var teamListEl = document.getElementById('team-list');

  if (
    !pageAlertEl || !pageAlertTextEl || !pageAlertLoginLinkEl || !pageAlertPayLinkEl || !statusEl || !detailEl ||
    !imageEl ||
    !sellerEl || !nameEl || !descriptionEl || !basePriceEl || !maxParticipantsEl ||
    !priceTiersTableEl || !priceTiersBodyEl || !targetParticipantsFieldEl || !targetParticipantsOptionsEl ||
    !refundNoticeCheckboxEl ||
    !buyAloneBtn || !createTeamBtn ||
    !teamStatusEl || !teamListEl
  ) {
    return;
  }

  // init()에서 확정되면 이후 액션 핸들러들이 참조한다.
  var currentProductId = null;
  // 구매자가 라디오로 고른 목표 인원(price_tier.minCount). 아무 것도 안 고르면 null.
  var selectedTargetParticipants = null;

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
   * "팀 성사 후 환불 불가" 체크박스를 체크했는지 여부 — "신규 팀 신설하기"/각 팀의 "참가하기" 버튼
   * 게이트로 쓴다(design.md 결정, 혼자 구매하기는 이 제약과 무관).
   */
  function refundNoticeAccepted() {
    return refundNoticeCheckboxEl.checked;
  }

  function updateCreateTeamButtonState() {
    createTeamBtn.disabled = selectedTargetParticipants === null || !refundNoticeAccepted();
  }

  /**
   * 이미 렌더링된 모든 팀 항목의 "참가하기" 버튼 활성 상태를 체크박스 상태에 맞춰 다시 적용한다
   * (체크박스를 늦게 체크해도 이미 그려진 팀 목록의 참가 버튼이 즉시 활성화되게).
   */
  function updateJoinButtonsState() {
    var joinButtons = teamListEl.querySelectorAll('.team-item-join-btn');
    joinButtons.forEach(function (btn) {
      btn.disabled = !refundNoticeAccepted();
    });
  }

  /**
   * "신규 팀 신설하기"에 쓸 목표 인원(price_tier.minCount) 라디오 옵션을 그린다.
   * 아무것도 선택되지 않은 상태로 초기화하고, create-team-btn은 선택 전까지 비활성 상태를 유지한다.
   */
  function renderTargetParticipantsOptions(tiers) {
    selectedTargetParticipants = null;
    updateCreateTeamButtonState();
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
        updateCreateTeamButtonState();
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

  /**
   * joinedAt(ISO-8601 원본)을 정확한 시:분:초가 아니라 대략적인 단위로 보여준다
   * (design.md 결정: "대략적으로만" 노출, 정확한 포맷 문자열은 Generate 재량).
   */
  function formatApproxJoinedAt(isoString) {
    var date = isoString ? new Date(isoString) : null;
    if (!date || isNaN(date.getTime())) {
      return '';
    }
    var diffDays = Math.floor((Date.now() - date.getTime()) / (1000 * 60 * 60 * 24));
    if (diffDays <= 0) {
      return '오늘 참여';
    }
    return diffDays + '일 전 참여';
  }

  function createParticipantItem(participant) {
    var li = document.createElement('li');
    li.className = 'participant-item';

    var nameEl = document.createElement('span');
    nameEl.className = 'participant-item-name';
    nameEl.textContent = participant.displayName || '';
    li.appendChild(nameEl);

    if (participant.isLeader) {
      var leaderBadgeEl = document.createElement('span');
      leaderBadgeEl.className = 'badge badge-leader';
      leaderBadgeEl.textContent = '팀장';
      li.appendChild(leaderBadgeEl);
    }

    var joinedAtEl = document.createElement('span');
    joinedAtEl.className = 'participant-item-joined-at';
    joinedAtEl.textContent = formatApproxJoinedAt(participant.joinedAt);
    li.appendChild(joinedAtEl);

    return li;
  }

  /**
   * "참여자 보기"를 펼칠 때만 그 팀의 참여자를 개별 조회한다(팀 목록 로드 시점에 한꺼번에 불러오지 않음
   * — design.md 결정, N개 팀 × 참여자 조회 요청이 동시에 나가는 걸 피함).
   */
  function loadParticipants(teamId, listEl, statusEl) {
    statusEl.hidden = false;
    statusEl.textContent = '참여자 목록을 불러오는 중입니다...';
    statusEl.className = 'product-status product-status--loading';
    clearChildren(listEl);

    return window.Api.get('/teams/' + teamId + '/participants')
      .then(function (participants) {
        var list = Array.isArray(participants) ? participants : [];
        if (list.length === 0) {
          statusEl.textContent = '아직 참여자가 없습니다.';
          statusEl.className = 'product-status product-status--empty';
          return;
        }
        statusEl.hidden = true;
        var fragment = document.createDocumentFragment();
        list.forEach(function (participant) {
          fragment.appendChild(createParticipantItem(participant));
        });
        listEl.appendChild(fragment);
      })
      .catch(function (err) {
        console.error('[product.js] failed to load participants:', err);
        statusEl.hidden = false;
        statusEl.textContent = (err && err.message) || '참여자 목록을 불러오지 못했습니다.';
        statusEl.className = 'product-status product-status--error';
      });
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
    joinBtn.className = 'btn btn-secondary btn-sm team-item-join-btn';
    joinBtn.textContent = '참가하기';
    joinBtn.disabled = !refundNoticeAccepted();
    joinBtn.addEventListener('click', function () {
      handleJoin(team.teamId, joinBtn);
    });
    li.appendChild(joinBtn);

    var toggleParticipantsBtn = document.createElement('button');
    toggleParticipantsBtn.type = 'button';
    toggleParticipantsBtn.className = 'btn btn-ghost btn-sm';
    toggleParticipantsBtn.textContent = '참여자 보기';
    li.appendChild(toggleParticipantsBtn);

    var participantsStatusEl = document.createElement('div');
    participantsStatusEl.className = 'product-status';
    participantsStatusEl.hidden = true;

    var participantsListEl = document.createElement('ul');
    participantsListEl.className = 'participant-list';

    var participantsPanelEl = document.createElement('div');
    participantsPanelEl.className = 'participants-panel';
    participantsPanelEl.hidden = true;
    participantsPanelEl.appendChild(participantsStatusEl);
    participantsPanelEl.appendChild(participantsListEl);
    li.appendChild(participantsPanelEl);

    var participantsLoaded = false;
    toggleParticipantsBtn.addEventListener('click', function () {
      var wasOpen = !participantsPanelEl.hidden;
      if (wasOpen) {
        participantsPanelEl.hidden = true;
        toggleParticipantsBtn.textContent = '참여자 보기';
        return;
      }
      participantsPanelEl.hidden = false;
      toggleParticipantsBtn.textContent = '참여자 숨기기';
      if (!participantsLoaded) {
        participantsLoaded = true;
        loadParticipants(team.teamId, participantsListEl, participantsStatusEl);
      }
    });

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
    if (!refundNoticeAccepted()) {
      showPageAlert('먼저 "팀 성사 후 환불 불가" 안내를 확인해주세요.', 'error');
      return;
    }

    hidePageAlert();
    joinBtn.disabled = true;

    window.Api.post('/teams/' + teamId + '/join')
      .then(function () {
        var payLink = 'checkout.html?productId=' + currentProductId + '&teamId=' + teamId;
        showPageAlert('공구팀에 참가했습니다.', 'success', false, payLink);
        loadTeams(currentProductId);
      })
      .catch(function (err) {
        joinBtn.disabled = !refundNoticeAccepted();
        handleActionError(err, currentProductId);
      });
  }

  function handleCreateTeam() {
    if (selectedTargetParticipants === null) {
      showPageAlert('목표 인원을 먼저 선택해주세요.', 'error');
      return;
    }
    if (!refundNoticeAccepted()) {
      showPageAlert('먼저 "팀 성사 후 환불 불가" 안내를 확인해주세요.', 'error');
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
        updateCreateTeamButtonState();
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
    refundNoticeCheckboxEl.addEventListener('change', function () {
      updateCreateTeamButtonState();
      updateJoinButtonsState();
    });

    loadProduct(productId);
    connectRealtime(productId);
  }

  init();
})();
