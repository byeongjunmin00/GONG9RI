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
 * - 각 팀 항목의 "참여자 보기"를 누르면 그때 GET /api/teams/{teamId}/participants를 개별 조회해
 *   마스킹된 이름·팀장 배지·대략적 참여 시각을 펼쳐 보여준다(팀 목록 로드 시점에 한꺼번에 불러오지 않음).
 * - "팀 성사 후 환불 불가" 체크박스(#refund-notice-checkbox)를 체크하지 않으면 "신규 팀 신설하기"와
 *   각 팀의 "참가하기" 버튼이 비활성 상태를 유지한다(명시적 확인 없이는 팀 신설/참가를 진행할 수 없게
 *   하는 가드 — 기존 목표 인원 라디오 가드와 같은 방식). "혼자 구매하기"는 이 제약과 무관하다(솔로
 *   구매는 환불 불가 규칙의 대상이 아니라서).
 * - "카카오톡 공유하기": product.html의 Kakao JS SDK(CDN)를 상품 상세 응답의 kakaoJsKey로 초기화한다.
 *   키가 없으면(로컬에서 KAKAO_JS_KEY 미설정 등) 버튼을 숨긴 채로 둔다(docs/dev/share/kakao-share/design.md).
 * - 문의하기: GET /api/products/{id}/inquiries로 목록을 불러온다(비로그인도 조회 가능, 리뷰와 달리
 *   구매 이력 없이도 누구나 작성 가능). 작성 폼은 항상 노출하고, 비로그인이면(UNAUTHORIZED) 서버 응답을
 *   그대로 안내한다. 본인이 쓴 문의 중 아직 답변이 없는 것에만 수정/삭제 버튼을 보여주고(답변 달린 문의는
 *   서버가 409 INQUIRY_ALREADY_ANSWERED로 거절), 로그인한 회원이 그 상품의 판매자(product.sellerId와
 *   currentMemberId 비교)일 때만 각 문의 항목에 답변 등록/수정/삭제 UI를 보여준다(review 섹션과 동일한
 *   'gong9ri:auth-resolved' currentMemberId 패턴 재사용, design.md 결정).
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
  var descriptionStatusEl = document.getElementById('product-description-status');
  var basePriceEl = document.getElementById('product-base-price');
  var maxParticipantsEl = document.getElementById('product-max-participants');
  var priceTiersTableEl = document.getElementById('price-tiers-table');
  var priceTiersBodyEl = document.getElementById('price-tiers-body');
  var targetParticipantsFieldEl = document.getElementById('target-participants-field');
  var targetParticipantsOptionsEl = document.getElementById('target-participants-options');
  var refundNoticeCheckboxEl = document.getElementById('refund-notice-checkbox');

  var openAtNoticeEl = document.getElementById('open-at-notice');
  var buyAloneBtn = document.getElementById('buy-alone-btn');
  var createTeamBtn = document.getElementById('create-team-btn');
  var kakaoShareBtn = document.getElementById('kakao-share-btn');

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

  var inquiriesCountEl = document.getElementById('inquiries-count');
  var inquiriesStatusEl = document.getElementById('inquiries-status');
  var inquiriesListEl = document.getElementById('inquiries-list');
  var inquiryFormEl = document.getElementById('inquiry-form');
  var inquiryFormAlertEl = document.getElementById('inquiry-form-alert');
  var inquiryContentEl = document.getElementById('inquiry-content');
  var inquirySubmitBtn = document.getElementById('inquiry-submit');

  if (
    !pageAlertEl || !pageAlertTextEl || !pageAlertLoginLinkEl || !pageAlertPayLinkEl || !statusEl || !detailEl ||
    !imageEl ||
    !sellerEl || !nameEl || !descriptionEl || !descriptionStatusEl || !basePriceEl || !maxParticipantsEl ||
    !priceTiersTableEl || !priceTiersBodyEl || !targetParticipantsFieldEl || !targetParticipantsOptionsEl ||
    !refundNoticeCheckboxEl ||
    !openAtNoticeEl || !buyAloneBtn || !createTeamBtn || !kakaoShareBtn ||
    !teamStatusEl || !teamListEl ||
    !reviewAverageEl || !reviewsStatusEl || !reviewsListEl || !reviewFormEl || !reviewFormAlertEl ||
    !reviewRatingEl || !reviewContentEl || !reviewSubmitBtn ||
    !inquiriesCountEl || !inquiriesStatusEl || !inquiriesListEl || !inquiryFormEl || !inquiryFormAlertEl ||
    !inquiryContentEl || !inquirySubmitBtn
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
  // 카카오톡 공유하기 버튼 클릭 시 쓸 상품 정보. renderProduct에서 채워진다.
  var shareTargetProduct = null;
  // 문의 폼이 "새로 작성" 모드인지 "기존 문의 수정" 모드인지 구분한다. null이면 작성 모드.
  var editingInquiryId = null;
  // 이 상품의 판매자 id. renderProduct에서 채워진다 — currentMemberId와 비교해 답변 등록/수정/삭제
  // UI 노출 여부를 결정한다(design.md 결정).
  var currentSellerId = null;
  // 오픈예정(product/product-launch) — product.openAt이 미래 시각이면 true. renderProduct에서 채워진다.
  // true면 "혼자 구매하기"를 비활성화하고, "신규 팀 신설하기"는 기존 게이트(목표인원+환불동의)와
  // 함께 updateCreateTeamButtonState()에서 판단한다.
  var productNotYetOpen = false;

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
   * 상품정보/리뷰/문의 탭 전환 — 표시/숨김만 바꾼다. 리뷰/문의 데이터는 이미 init()에서 탭과
   * 무관하게 먼저 불러와 둔 상태라 여기서는 재조회하지 않는다(design.md 결정).
   */
  function switchTab(targetPanelId) {
    var tabButtons = document.querySelectorAll('.product-tab');
    var tabPanels = document.querySelectorAll('.product-tab-panel');

    tabButtons.forEach(function (btn) {
      var isActive = btn.getAttribute('data-tab-target') === targetPanelId;
      btn.classList.toggle('is-active', isActive);
      btn.setAttribute('aria-selected', isActive ? 'true' : 'false');
    });

    tabPanels.forEach(function (panel) {
      panel.hidden = panel.id !== targetPanelId;
    });
  }

  function setUpTabs() {
    var tabButtons = document.querySelectorAll('.product-tab');
    tabButtons.forEach(function (btn) {
      btn.addEventListener('click', function () {
        switchTab(btn.getAttribute('data-tab-target'));
      });
    });
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

    updateOpenAtNotice(product.openAt);

    currentSellerId = typeof product.sellerId === 'number' ? product.sellerId : null;

    sellerEl.textContent = product.sellerName || '';
    nameEl.textContent = product.name || '';
    if (product.description) {
      descriptionEl.hidden = false;
      descriptionEl.textContent = product.description;
      descriptionStatusEl.hidden = true;
    } else {
      descriptionEl.hidden = true;
      descriptionEl.textContent = '';
      descriptionStatusEl.hidden = false;
    }
    basePriceEl.textContent = formatPrice(product.basePrice);
    maxParticipantsEl.textContent =
      typeof product.maxParticipants === 'number' ? String(product.maxParticipants) : '';

    setUpKakaoShare(product);

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
   * 카카오 JS SDK 초기화 + 공유 버튼 노출 여부 결정. product.kakaoJsKey가 비어있으면(로컬 개발 환경에
   * KAKAO_JS_KEY를 안 넣은 경우 등) 버튼을 계속 숨긴 채로 둔다 — 키 없이 Kakao.init을 시도하면
   * 공유 시점에 에러가 나므로 아예 진입 자체를 막는 게 안전하다.
   */
  function setUpKakaoShare(product) {
    if (!product.kakaoJsKey || typeof window.Kakao === 'undefined') {
      kakaoShareBtn.hidden = true;
      return;
    }
    if (!window.Kakao.isInitialized()) {
      window.Kakao.init(product.kakaoJsKey);
    }
    shareTargetProduct = product;
    kakaoShareBtn.hidden = false;
  }

  /**
   * "카카오톡 공유하기" — Kakao Share 기본 템플릿으로 상품명/가격/썸네일/현재 페이지 링크를 담아
   * 카카오톡 공유 대화상자를 연다. 썸네일(product.imageUrl)이 절대경로(https://...)가 아니면 카카오
   * 서버가 접근하지 못해 이미지 없이 뜰 수 있다(design.md 리스크 참고) — 별도 방어 로직 없이 그대로
   * 카카오 SDK에 맡긴다.
   */
  function handleKakaoShare() {
    if (!shareTargetProduct || !window.Kakao || !window.Kakao.isInitialized()) {
      return;
    }

    var link = {
      mobileWebUrl: window.location.href,
      webUrl: window.location.href
    };
    var description = formatPrice(shareTargetProduct.basePrice) + '부터 · 함께할수록 더 저렴해져요';
    var buttons = [
      { title: '상품 보러가기', link: link }
    ];

    // feed 템플릿(objectType: 'feed')은 카카오 SDK 상 imageUrl이 필수라, 상품에 등록된 이미지가
    // 없으면 이미지 없이도 되는 text 템플릿으로 전환한다(design.md 평가 기준 — 이미지 없는 상품도
    // 공유 카드가 깨지지 않아야 함).
    var templateArgs = shareTargetProduct.imageUrl
      ? {
          objectType: 'feed',
          content: {
            title: shareTargetProduct.name || 'GONG9RI 공동구매',
            description: description,
            imageUrl: shareTargetProduct.imageUrl,
            link: link
          },
          buttons: buttons
        }
      : {
          objectType: 'text',
          text: (shareTargetProduct.name || 'GONG9RI 공동구매') + '\n' + description,
          link: link,
          buttons: buttons
        };

    window.Kakao.Share.sendDefault(templateArgs);
  }

  /**
   * "팀 성사 후 환불 불가" 체크박스를 체크했는지 여부 — "신규 팀 신설하기"/각 팀의 "참가하기" 버튼
   * 게이트로 쓴다(design.md 결정, 혼자 구매하기는 이 제약과 무관).
   */
  function refundNoticeAccepted() {
    return refundNoticeCheckboxEl.checked;
  }

  function updateCreateTeamButtonState() {
    createTeamBtn.disabled = productNotYetOpen || selectedTargetParticipants === null || !refundNoticeAccepted();
  }

  /**
   * 오픈예정(product/product-launch) 안내 — product.openAt이 미래면 배너를 띄우고 구매/신설 버튼을
   * 막는다. 실제 최종 판정은 항상 서버(PRODUCT_NOT_YET_OPEN)이고, 이건 UX 보조일 뿐이다 — 예를 들어
   * 페이지를 열어둔 채로 오픈 시각이 지나도 새로고침 전까진 여기서 다시 활성화하지 않는다(그 경우
   * 서버가 정상 처리하므로 기능상 문제는 없음, 다음 새로고침에서 배너가 사라짐).
   */
  function updateOpenAtNotice(openAtIso) {
    var openAt = openAtIso ? new Date(openAtIso) : null;
    productNotYetOpen = !!(openAt && !isNaN(openAt.getTime()) && openAt.getTime() > Date.now());

    if (!productNotYetOpen) {
      openAtNoticeEl.hidden = true;
      buyAloneBtn.disabled = false;
      return;
    }

    openAtNoticeEl.hidden = false;
    openAtNoticeEl.className = 'form-alert form-alert--error';
    openAtNoticeEl.textContent =
      '오픈예정 상품입니다(' + openAt.toLocaleString('ko-KR') + ' 공개 예정). 그 전까지는 구매·팀 신설이 불가합니다.';
    buyAloneBtn.disabled = true;
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

  // ---------- 문의하기 ----------

  function showInquiriesStatus(text, variant) {
    inquiriesStatusEl.hidden = false;
    inquiriesStatusEl.textContent = text;
    inquiriesStatusEl.className = 'product-status product-status--' + variant;
  }

  function hideInquiriesStatus() {
    inquiriesStatusEl.hidden = true;
    inquiriesStatusEl.textContent = '';
  }

  function showInquiryFormAlert(text, variant) {
    inquiryFormAlertEl.hidden = false;
    inquiryFormAlertEl.textContent = text;
    inquiryFormAlertEl.className = 'form-alert form-alert--' + variant;
  }

  function hideInquiryFormAlert() {
    inquiryFormAlertEl.hidden = true;
    inquiryFormAlertEl.textContent = '';
  }

  function resetInquiryForm() {
    editingInquiryId = null;
    inquiryContentEl.value = '';
    inquirySubmitBtn.textContent = '문의 작성';
  }

  function startEditingInquiry(inquiry) {
    editingInquiryId = inquiry.inquiryId;
    inquiryContentEl.value = inquiry.content || '';
    inquirySubmitBtn.textContent = '문의 수정 저장';
    hideInquiryFormAlert();
    inquiryFormEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  function handleDeleteInquiry(inquiryId) {
    if (!window.confirm('문의를 삭제할까요?')) {
      return;
    }
    window.Api.del('/inquiries/' + inquiryId)
      .then(function () {
        if (editingInquiryId === inquiryId) {
          resetInquiryForm();
        }
        loadInquiries(currentProductId);
      })
      .catch(function (err) {
        console.error('[product.js] failed to delete inquiry:', err);
        window.alert((err && err.message) || '문의 삭제에 실패했습니다.');
      });
  }

  function handleDeleteAnswer(inquiryId) {
    if (!window.confirm('답변을 삭제할까요?')) {
      return;
    }
    window.Api.del('/inquiries/' + inquiryId + '/answer')
      .then(function () {
        loadInquiries(currentProductId);
      })
      .catch(function (err) {
        console.error('[product.js] failed to delete answer:', err);
        window.alert((err && err.message) || '답변 삭제에 실패했습니다.');
      });
  }

  /**
   * 판매자용 답변 등록/수정 인라인 폼 — review-form처럼 페이지에 하나 두고 재사용하는 대신, 여러
   * 문의에 동시에 답변할 수 있게 문의 항목마다 개별로 만든다(design.md 결정). 문의에 이미 답변이
   * 있으면 PUT(수정), 없으면 POST(등록)를 호출한다.
   */
  function createAnswerForm(inquiry) {
    var wrapper = document.createElement('div');
    wrapper.className = 'form-group';

    var alertEl = document.createElement('div');
    alertEl.className = 'form-alert';
    alertEl.hidden = true;
    wrapper.appendChild(alertEl);

    var textarea = document.createElement('textarea');
    textarea.className = 'form-input';
    textarea.rows = 2;
    textarea.maxLength = 1000;
    textarea.value = inquiry.answerContent || '';
    wrapper.appendChild(textarea);

    var submitBtn = document.createElement('button');
    submitBtn.type = 'button';
    submitBtn.className = 'btn btn-primary btn-sm';
    submitBtn.textContent = inquiry.answered ? '답변 수정 저장' : '답변 등록';
    wrapper.appendChild(submitBtn);

    submitBtn.addEventListener('click', function () {
      var content = textarea.value.trim();
      submitBtn.disabled = true;

      var request = inquiry.answered
        ? window.Api.put('/inquiries/' + inquiry.inquiryId + '/answer', { content: content })
        : window.Api.post('/inquiries/' + inquiry.inquiryId + '/answer', { content: content });

      request
        .then(function () {
          loadInquiries(currentProductId);
        })
        .catch(function (err) {
          console.error('[product.js] failed to save answer:', err);
          alertEl.hidden = false;
          alertEl.textContent = (err && err.message) || '답변 저장에 실패했습니다.';
          alertEl.className = 'form-alert form-alert--error';
          submitBtn.disabled = false;
        });
    });

    return wrapper;
  }

  function createInquiryItem(inquiry) {
    var li = document.createElement('li');
    li.className = 'mypage-list-item';

    var infoEl = document.createElement('div');
    infoEl.className = 'mypage-list-item__info';

    var titleEl = document.createElement('span');
    titleEl.className = 'mypage-list-item__title';
    titleEl.textContent = inquiry.memberName + (inquiry.answered ? ' · 답변완료' : ' · 미답변');
    infoEl.appendChild(titleEl);

    var contentEl = document.createElement('span');
    contentEl.className = 'mypage-list-item__meta';
    contentEl.textContent = inquiry.content || '';
    infoEl.appendChild(contentEl);

    if (inquiry.answered) {
      var answerEl = document.createElement('span');
      answerEl.className = 'mypage-list-item__meta';
      answerEl.textContent = '판매자 답변: ' + (inquiry.answerContent || '');
      infoEl.appendChild(answerEl);
    }

    li.appendChild(infoEl);

    var actionsEl = document.createElement('div');
    actionsEl.className = 'mypage-list-item__actions';
    var hasActions = false;

    // 작성자 본인 — 답변이 없는 문의만 수정/삭제 가능(답변 달린 문의는 서버가 409로 거절하므로
    // 애초에 버튼을 노출하지 않는다).
    if (currentMemberId !== null && inquiry.memberId === currentMemberId && !inquiry.answered) {
      hasActions = true;

      var editBtn = document.createElement('button');
      editBtn.type = 'button';
      editBtn.className = 'btn btn-secondary btn-sm';
      editBtn.textContent = '수정';
      editBtn.addEventListener('click', function () {
        startEditingInquiry(inquiry);
      });
      actionsEl.appendChild(editBtn);

      var deleteBtn = document.createElement('button');
      deleteBtn.type = 'button';
      deleteBtn.className = 'btn btn-ghost btn-sm';
      deleteBtn.textContent = '삭제';
      deleteBtn.addEventListener('click', function () {
        handleDeleteInquiry(inquiry.inquiryId);
      });
      actionsEl.appendChild(deleteBtn);
    }

    // 그 상품의 판매자 본인 — 답변 등록/수정/삭제.
    if (currentMemberId !== null && currentSellerId !== null && currentMemberId === currentSellerId) {
      hasActions = true;

      var answerFormEl = null;
      var toggleAnswerBtn = document.createElement('button');
      toggleAnswerBtn.type = 'button';
      toggleAnswerBtn.className = 'btn btn-secondary btn-sm';
      toggleAnswerBtn.textContent = inquiry.answered ? '답변 수정' : '답변 등록';
      toggleAnswerBtn.addEventListener('click', function () {
        if (answerFormEl) {
          answerFormEl.remove();
          answerFormEl = null;
          return;
        }
        answerFormEl = createAnswerForm(inquiry);
        li.appendChild(answerFormEl);
      });
      actionsEl.appendChild(toggleAnswerBtn);

      if (inquiry.answered) {
        var deleteAnswerBtn = document.createElement('button');
        deleteAnswerBtn.type = 'button';
        deleteAnswerBtn.className = 'btn btn-ghost btn-sm';
        deleteAnswerBtn.textContent = '답변 삭제';
        deleteAnswerBtn.addEventListener('click', function () {
          handleDeleteAnswer(inquiry.inquiryId);
        });
        actionsEl.appendChild(deleteAnswerBtn);
      }
    }

    if (hasActions) {
      li.appendChild(actionsEl);
    }

    return li;
  }

  function renderInquiries(data) {
    inquiriesCountEl.textContent = typeof data.count === 'number' && data.count > 0 ? '(' + data.count + ')' : '';

    clearChildren(inquiriesListEl);
    var fragment = document.createDocumentFragment();
    (data.inquiries || []).forEach(function (inquiry) {
      fragment.appendChild(createInquiryItem(inquiry));
    });
    inquiriesListEl.appendChild(fragment);
  }

  function loadInquiries(productId) {
    showInquiriesStatus('문의를 불러오는 중입니다...', 'loading');
    clearChildren(inquiriesListEl);

    return window.Api.get('/products/' + productId + '/inquiries')
      .then(function (data) {
        if (!data.inquiries || data.inquiries.length === 0) {
          inquiriesCountEl.textContent = '';
          showInquiriesStatus('아직 등록된 문의가 없습니다.', 'empty');
          return;
        }
        hideInquiriesStatus();
        renderInquiries(data);
      })
      .catch(function (err) {
        console.error('[product.js] failed to load inquiries:', err);
        var message = (err && err.message) || '문의를 불러오지 못했습니다.';
        showInquiriesStatus(message, 'error');
      });
  }

  function handleInquiryFormError(err) {
    var status = err && err.status;
    var code = err && err.code;
    var message = (err && err.message) || '요청 처리 중 오류가 발생했습니다.';

    if (status === 401 || code === 'UNAUTHORIZED') {
      showInquiryFormAlert('로그인 후 작성할 수 있습니다.', 'error');
      return;
    }
    showInquiryFormAlert(message, 'error');
  }

  function handleInquiryFormSubmit(event) {
    event.preventDefault();
    hideInquiryFormAlert();

    var content = inquiryContentEl.value.trim();
    var body = { content: content };

    inquirySubmitBtn.disabled = true;

    var request = editingInquiryId
      ? window.Api.put('/inquiries/' + editingInquiryId, body)
      : window.Api.post('/products/' + currentProductId + '/inquiries', body);

    request
      .then(function () {
        showInquiryFormAlert(editingInquiryId ? '문의를 수정했습니다.' : '문의를 작성했습니다.', 'success');
        resetInquiryForm();
        loadInquiries(currentProductId);
      })
      .catch(handleInquiryFormError)
      .then(function () {
        inquirySubmitBtn.disabled = false;
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
        // renderProduct()가 currentSellerId를 채운 뒤에 다시 불러와야 판매자 답변 UI가 정확히
        // 반영된다(init()의 초기 loadInquiries() 호출은 currentSellerId가 아직 null일 수 있음).
        loadInquiries(productId);
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

    setUpTabs();

    buyAloneBtn.addEventListener('click', handleBuyAlone);
    createTeamBtn.addEventListener('click', handleCreateTeam);
    kakaoShareBtn.addEventListener('click', handleKakaoShare);
    reviewFormEl.addEventListener('submit', handleReviewFormSubmit);
    inquiryFormEl.addEventListener('submit', handleInquiryFormSubmit);

    // header-auth.js가 이미 GET /api/auth/me를 호출하므로 그 결과를 재사용한다(중복 호출 방지).
    // 이 이벤트가 loadReviews()/loadInquiries()보다 늦게 올 수도 있어(비동기 순서 보장 없음), 도착하면
    // 다시 불러와서 "내가 쓴 리뷰/문의"의 수정/삭제 버튼과 판매자 답변 UI가 정확히 반영되게 한다.
    document.addEventListener('gong9ri:auth-resolved', function (event) {
      var detail = event.detail || {};
      currentMemberId = detail.loggedIn && detail.member ? detail.member.memberId : null;
      loadReviews(productId);
      loadInquiries(productId);
    });

    refundNoticeCheckboxEl.addEventListener('change', function () {
      updateCreateTeamButtonState();
      updateJoinButtonsState();
    });

    loadProduct(productId);
    loadReviews(productId);
    loadInquiries(productId);
    connectRealtime(productId);
  }

  init();
})();
