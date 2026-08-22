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
 * - 찜(product/wishlist): 상품 사진(#product-image) 위 하트 버튼 — main.js(메인 카드)의
 *   toggleWishlist와 동일한 정책(멱등 POST/DELETE, 낙관적 토글, 비로그인은 로그인 페이지로, 403은
 *   안내 배너)을 독립적으로 재구현한다. 초기 active 상태는 개별 조회 API가 없어 로그인한 구매자에
 *   한해 GET /api/buyer/mypage/wishlist(내 찜 전체 목록)로 현재 상품 포함 여부를 확인해 판정한다.
 * - 구매 UI 노출 제어(product/purchase-visibility): 서버는 구매(공구팀 신설/참가/탈퇴, 결제 시작)를
 *   Role.BUYER인 회원에게만 허용한다(상품은 항상 SELLER가 등록하므로 "자기 상품" 보는 판매자도 이미
 *   "역할이 SELLER"에 포함됨). 로그인한 회원의 role이 BUYER가 아니면(SELLER/ADMIN) 신설/구매/계속
 *   쇼핑하기(#product-actions), 환불 동의 체크박스(#refund-notice-field), 목표 인원 선택
 *   (#target-participants-field), 각 팀의 "참가하기" 버튼을 전부 숨긴다(applyPurchaseRoleVisibility,
 *   isNonBuyerMember). role은 currentMemberId와 같은 시점('gong9ri:auth-resolved')에 채워지고,
 *   그 이벤트가 loadTeams()보다 늦게 도착할 수 있어 도착 시 팀 목록도 다시 불러온다. 비로그인은
 *   대상이 아니다 — 기존처럼 구매 UI를 그대로 보여주고 클릭 시 401 → 로그인 안내로 유도한다.
 *   구매 UI가 다 사라지면 페이지를 나갈 방법이 없어지고(2026-08-22 리포트) 서머리 카드에 빈
 *   여백만 남으므로, #product-actions 밖에 항상 보이는 뒤로가기 링크(.product-back-link)를 두고
 *   그 자리에 안내 문구(#purchase-role-notice)를 채우며 카드도 늘어나지 않게 한다(.product-detail-summary--compact).
 */
(function () {
  var pageAlertEl = document.getElementById('page-alert');
  var pageAlertTextEl = document.getElementById('page-alert-text');
  var pageAlertLoginLinkEl = document.getElementById('page-alert-login-link');
  var pageAlertPayLinkEl = document.getElementById('page-alert-pay-link');
  var statusEl = document.getElementById('product-status');
  var detailEl = document.getElementById('product-detail');
  var imageEl = document.getElementById('product-image');
  var galleryThumbsEl = document.getElementById('product-gallery-thumbs');
  var galleryHintEl = document.getElementById('product-gallery-hint');
  var wishlistBtnEl = document.getElementById('product-wishlist-btn');

  var sellerEl = document.getElementById('product-seller');
  var sellerTrustEl = document.getElementById('product-seller-trust');
  var nameEl = document.getElementById('product-name');
  var productCodeEl = document.getElementById('product-code');
  var descriptionEl = document.getElementById('product-description');
  var descriptionStatusEl = document.getElementById('product-description-status');
  var basePriceEl = document.getElementById('product-base-price');
  var maxParticipantsEl = document.getElementById('product-max-participants');
  var priceTiersTableEl = document.getElementById('price-tiers-table');
  var priceTiersBodyEl = document.getElementById('price-tiers-body');
  var targetParticipantsFieldEl = document.getElementById('target-participants-field');
  var targetParticipantsOptionsEl = document.getElementById('target-participants-options');
  var refundNoticeCheckboxEl = document.getElementById('refund-notice-checkbox');
  var refundNoticeFieldEl = document.getElementById('refund-notice-field');

  var openAtNoticeEl = document.getElementById('open-at-notice');
  var productActionsEl = document.getElementById('product-actions');
  var purchaseRoleNoticeEl = document.getElementById('purchase-role-notice');
  var summaryEl = document.getElementById('product-detail-summary');
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
    !imageEl || !wishlistBtnEl ||
    !sellerEl || !nameEl || !descriptionEl || !descriptionStatusEl || !basePriceEl || !maxParticipantsEl ||
    !priceTiersTableEl || !priceTiersBodyEl || !targetParticipantsFieldEl || !targetParticipantsOptionsEl ||
    !refundNoticeCheckboxEl || !refundNoticeFieldEl ||
    !openAtNoticeEl || !productActionsEl || !purchaseRoleNoticeEl || !summaryEl ||
    !buyAloneBtn || !createTeamBtn || !kakaoShareBtn ||
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
  // 로그인한 회원의 역할('BUYER'|'SELLER'|'ADMIN'). 비로그인이면 null(currentMemberId와 동시에 채워짐).
  // 구매(공구팀 신설/참가/탈퇴, 결제 시작)는 서버가 BUYER에게만 허용하므로(TeamService·PaymentService의
  // requireBuyer) 구매 UI 노출 여부 판단에 쓴다 — isNonBuyerMember() 참고.
  // 'gong9ri:auth-resolved'로 함께 채워진다. 찜(product/wishlist) 초기 상태는 로그인한 구매자에
  // 한해서만 조회한다(main.js와 동일 조건).
  var currentMemberRole = null;
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

  // ---------- 상품 이미지 갤러리 (product/image) ----------
  // 상세 응답의 imageUrls는 product_image가 없는 상품이면 대표 이미지 한 장짜리로 채워져 오므로
  // (ProductResponse.withImages), 이 기능 이전에 등록된 상품도 별도 분기 없이 그대로 동작한다.
  // 사진 전환은 아래 캐러셀 썸네일 클릭만으로 한다 — 원래 큰 사진 위에 화살표·카운터를 얹은
  // 갤러리 UI가 있었는데, 그 래퍼가 레이아웃 공간을 차지해 사진이 "붕 떠 보이는" 원인이 됐고
  // 어차피 아래 캐러셀과 기능이 겹쳐서 걷어냈다(2026-08-21 사용자 요청).
  var galleryUrls = [];
  var galleryIndex = 0;

  function renderGallery(productName) {
    var hasMultiple = galleryUrls.length > 1;

    clearChildren(imageEl);
    if (galleryUrls.length) {
      var imgEl = document.createElement('img');
      imgEl.src = galleryUrls[galleryIndex];
      imgEl.alt = productName || '';
      imageEl.appendChild(imgEl);
    }
    // 찜(product/wishlist) 하트 — clearChildren이 방금 이 노드를 떼어냈을 뿐 파괴하진 않았으므로,
    // 들고 있던 참조를 다시 붙인다(캐러셀 썸네일을 눌러 사진을 넘길 때마다 renderGallery가
    // 다시 호출되므로 매번 필요).
    imageEl.appendChild(wishlistBtnEl);

    if (!galleryThumbsEl) {
      return;
    }
    // 썸네일 줄은 사진 개수와 무관하게 항상 보인다 — 한 장뿐이면 실제 썸네일 대신 빈 앨범
    // 플레이스홀더 슬롯을 채워서, 왼쪽 사진 칸이 "여기가 사진첩 자리"라는 인상을 유지하게 한다
    // (2026-08-21 사용자 요청). 가로 스크롤 캐러셀이라 4개를 채워도 넘치면 자연스럽게 옆으로
    // 걸쳐 보인다.
    if (galleryHintEl) {
      galleryHintEl.hidden = !hasMultiple;
    }
    clearChildren(galleryThumbsEl);
    if (!hasMultiple) {
      for (var i = 0; i < 4; i++) {
        var placeholder = document.createElement('li');
        placeholder.className = 'product-gallery-thumb product-gallery-thumb--empty';
        placeholder.setAttribute('aria-hidden', 'true');
        galleryThumbsEl.appendChild(placeholder);
      }
      return;
    }
    galleryUrls.forEach(function (url, index) {
      var item = document.createElement('li');
      var btn = document.createElement('button');
      btn.type = 'button';
      var active = index === galleryIndex;
      btn.className = 'product-gallery-thumb' + (active ? ' is-active' : '');
      if (active) {
        // 캐러셀이 가로 스크롤이라, 키보드로 넘기면 선택된 썸네일이 화면 밖으로 나갈 수 있다.
        // 렌더 직후 보이는 위치로 끌어온다(마우스 클릭 때는 이미 보이는 상태라 영향 없음).
        setTimeout(function () {
          if (btn.scrollIntoView) {
            btn.scrollIntoView({ block: 'nearest', inline: 'nearest' });
          }
        }, 0);
      }
      btn.setAttribute('aria-label', (index + 1) + '번째 사진 보기');
      var thumb = document.createElement('img');
      thumb.src = url;
      thumb.alt = '';
      btn.appendChild(thumb);
      btn.addEventListener('click', function () {
        galleryIndex = index;
        renderGallery(productName);
      });
      item.appendChild(btn);
      galleryThumbsEl.appendChild(item);
    });
  }

  /**
   * 키보드 좌우로 사진 넘기기 (product/image).
   *
   * 화살표 버튼은 레이아웃 공간을 차지해 걷어냈지만(팀원 정리, 2026-08-21), 키보드 이동은 화면을
   * 전혀 차지하지 않으면서 접근성을 되살려준다.
   *
   * <b>입력 중일 때는 절대 동작하면 안 된다</b> — 상담 채팅이나 문의를 쓰다가 좌우 키를 누르면
   * 커서를 옮기려던 건데 사진이 넘어가버린다. 그래서 입력 요소·편집 가능 영역에 포커스가 있으면
   * 건너뛰고, 조합키(Ctrl/Cmd/Alt)가 눌린 경우도 브라우저 단축키일 수 있어 건너뛴다.
   */
  function bindGalleryKeyboard(productName) {
    if (document.body.dataset.galleryKeysBound) {
      return;
    }
    document.body.dataset.galleryKeysBound = '1';

    document.addEventListener('keydown', function (event) {
      if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') {
        return;
      }
      if (event.ctrlKey || event.metaKey || event.altKey) {
        return;
      }
      var el = event.target;
      var tag = el && el.tagName ? el.tagName.toUpperCase() : '';
      if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || (el && el.isContentEditable)) {
        return;
      }
      if (galleryUrls.length < 2) {
        return;
      }
      var step = event.key === 'ArrowRight' ? 1 : -1;
      galleryIndex = (galleryIndex + step + galleryUrls.length) % galleryUrls.length;
      renderGallery(productName);
      event.preventDefault(); // 가로 스크롤이 같이 움직이는 걸 막는다
    });
  }

  function renderProduct(product) {
    galleryUrls = (product.imageUrls && product.imageUrls.length)
        ? product.imageUrls
        : (product.imageUrl ? [product.imageUrl] : []);
    galleryIndex = 0;
    renderGallery(product.name);
    bindGalleryKeyboard(product.name);

    updateOpenAtNotice(product.openAt);

    currentSellerId = typeof product.sellerId === 'number' ? product.sellerId : null;

    // 판매자 사진 + 이름(member/profile-image 노출). 신뢰 판매자 배지는 형제 엘리먼트라 그대로 둔다.
    while (sellerEl.firstChild) sellerEl.removeChild(sellerEl.firstChild);
    sellerEl.appendChild(
        window.Avatar.withName(product.sellerName || '', product.sellerProfileImageUrl, 'sm'));
    sellerTrustEl.hidden = !product.sellerTrustedBadge;
    nameEl.textContent = product.name || '';

    // 상품코드(admin-identifier-codes) — 백필 전 기존 상품은 값이 없을 수 있어 그때는 숨긴다.
    if (productCodeEl) {
      if (product.productCode) {
        productCodeEl.hidden = false;
        productCodeEl.textContent = product.productCode;
      } else {
        productCodeEl.hidden = true;
        productCodeEl.textContent = '';
      }
    }

    var headerRatingEl = document.getElementById('product-header-rating');
    if (headerRatingEl) {
      headerRatingEl.hidden = false;
      var rating = typeof product.ratingAverage === 'number' ? product.ratingAverage : 0;
      var count = typeof product.reviewCount === 'number' ? product.reviewCount : 0;
      var fullStars = Math.min(5, Math.max(0, Math.round(rating)));

      var starsHtml = '';
      for (var i = 0; i < 5; i++) {
        if (i < fullStars) {
          starsHtml += '★';
        } else {
          starsHtml += '<span class="star-empty">☆</span>';
        }
      }

      headerRatingEl.innerHTML =
        '<span class="card-rating-stars">' + starsHtml + '</span>' +
        '<span class="card-rating-score">' + (rating > 0 ? rating.toFixed(1) : '0.0') + '</span>' +
        '<span class="card-rating-count">(리뷰 ' + count + '개)</span>';

      headerRatingEl.onclick = function (e) {
        e.preventDefault();
        var tabsEl = document.querySelector('.product-tabs');
        if (tabsEl) {
          tabsEl.scrollIntoView({ behavior: 'smooth' });
        }
        var reviewTabBtn = document.querySelector('[data-tab="reviews"]');
        if (reviewTabBtn) {
          reviewTabBtn.click();
        }
      };
    }
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

    // 서버가 준 정식 주소를 쓴다. window.location.href를 쓰면 **공유한 사람이 보고 있던 주소**가
    // 그대로 나가서, 로컬에서 공유하면 받는 사람 기기의 localhost를 찾다가 아무것도 안 열린다
    // (2026-08-20). shareUrl은 이 필드 추가 이전에 캐시된 응답에서 null일 수 있어 폴백을 남긴다.
    var shareUrl = shareTargetProduct.shareUrl || window.location.href;
    var link = {
      mobileWebUrl: shareUrl,
      webUrl: shareUrl
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

  /**
   * 로그인은 했지만 구매 권한이 없는 회원(SELLER/ADMIN)인지. 비로그인(currentMemberRole === null)은
   * 여기 포함하지 않는다 — 비로그인은 기존처럼 구매 UI를 그대로 보여주고 클릭 시 401 → 로그인 안내로
   * 유도하는 흐름을 유지한다(product/purchase-visibility design.md 결정).
   */
  function isNonBuyerMember() {
    return currentMemberRole !== null && currentMemberRole !== 'BUYER';
  }

  /**
   * 구매 권한이 없는 회원에게 신설/구매/계속 쇼핑하기(#product-actions)와 환불 동의
   * 체크박스(#refund-notice-field)를 통째로 숨긴다. 목표 인원 선택(#target-participants-field)도
   * 신설 버튼이 없으면 의미가 없어 같이 숨기되, tier가 없어 이미 숨겨진 경우(renderTargetParticipantsOptions)는
   * 되돌려 보이게 하지 않는다 — 여기서는 "숨기는" 방향으로만 개입한다.
   * renderProduct() 이후와 'gong9ri:auth-resolved' 도착 시 둘 다 호출한다(둘 중 뭐가 먼저 올지 보장 없음).
   */
  function applyPurchaseRoleVisibility() {
    var hide = isNonBuyerMember();
    productActionsEl.hidden = hide;
    refundNoticeFieldEl.hidden = hide;
    if (hide) {
      targetParticipantsFieldEl.hidden = true;
    }
    // 구매 버튼 자리를 안내 문구로 대신 채운다(왜 버튼이 없는지 설명) + 서머리 카드가 사진 칸
    // 높이에 억지로 맞춰 늘어나지(align-items: stretch, 기본값) 않게 한다 — 그대로 두면 짧아진
    // 카드 안에 텅 빈 흰 여백만 남는다(2026-08-22 사용자 리포트).
    purchaseRoleNoticeEl.hidden = !hide;
    summaryEl.classList.toggle('product-detail-summary--compact', hide);
  }

  function updateCreateTeamButtonState() {
    createTeamBtn.hidden = productNotYetOpen;
    createTeamBtn.disabled = productNotYetOpen || selectedTargetParticipants === null || !refundNoticeAccepted();
  }

  /**
   * 오픈예정(product/product-launch) 안내 — product.openAt이 미래면 배너를 띄우고 구매/신설 버튼을
   * 숨긴다(비활성화만으로는 회색 버튼이 계속 눈에 띄어 완전히 안 보이게 처리하기로 했다, design.md
   * 결정). 실제 최종 판정은 항상 서버(PRODUCT_NOT_YET_OPEN)이고, 이건 UX 보조일 뿐이다 — 예를 들어
   * 페이지를 열어둔 채로 오픈 시각이 지나도 새로고침 전까진 여기서 다시 노출하지 않는다(그 경우
   * 서버가 정상 처리하므로 기능상 문제는 없음, 다음 새로고침에서 배너가 사라짐).
   */
  function updateOpenAtNotice(openAtIso) {
    var openAt = openAtIso ? new Date(openAtIso) : null;
    productNotYetOpen = !!(openAt && !isNaN(openAt.getTime()) && openAt.getTime() > Date.now());

    if (!productNotYetOpen) {
      openAtNoticeEl.hidden = true;
      buyAloneBtn.hidden = false;
      buyAloneBtn.disabled = false;
      return;
    }

    openAtNoticeEl.hidden = false;
    openAtNoticeEl.className = 'form-alert form-alert--error';
    openAtNoticeEl.textContent =
      '오픈예정 상품입니다(' + openAt.toLocaleString('ko-KR') + ' 공개 예정). 그 전까지는 구매·팀 신설이 불가합니다.';
    buyAloneBtn.hidden = true;
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
   * 첫 번째 옵션을 자동으로 선택된 상태로 만든다(옵션 개수와 무관, backlog 6번) — DOM의
   * `checked`만 켜면 `updateCreateTeamButtonState()`가 검사하는 `selectedTargetParticipants`
   * 변수는 그대로 null로 남아 버튼이 계속 비활성인 불일치가 생기므로, 두 곳을 함께 채운다.
   */
  function renderTargetParticipantsOptions(tiers) {
    selectedTargetParticipants = null;
    clearChildren(targetParticipantsOptionsEl);

    if (!tiers || tiers.length === 0) {
      targetParticipantsFieldEl.hidden = true;
      updateCreateTeamButtonState();
      applyPurchaseRoleVisibility();
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

      if (index === 0) {
        input.checked = true;
        selectedTargetParticipants = tier.minCount;
      }
    });

    targetParticipantsOptionsEl.appendChild(fragment);
    targetParticipantsFieldEl.hidden = false;
    updateCreateTeamButtonState();
    applyPurchaseRoleVisibility();
  }

  /**
   * 리뷰/문의 작성일시(backlog 1번) — "언제 쓴 글인지"가 CS·신뢰성 관점에서 의미가 있어(참여자
   * 목록의 대략적 표기와 달리) 절대 날짜+시각으로 보여준다. 코드베이스 기존 관행
   * (header-notifications.js, admin-refunds.js 등)과 동일하게 `toLocaleString('ko-KR')`을 쓴다.
   */
  function formatAbsoluteDateTime(isoString) {
    var date = isoString ? new Date(isoString) : null;
    if (!date || isNaN(date.getTime())) {
      return '';
    }
    return date.toLocaleString('ko-KR');
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

    // 공구팀 번호(admin-identifier-codes) — 백필 전 기존 팀은 값이 없을 수 있어 있을 때만 붙인다.
    if (team.teamNo) {
      var teamNoEl = document.createElement('span');
      teamNoEl.className = 'team-item-count';
      teamNoEl.textContent = team.teamNo;
      infoEl.appendChild(teamNoEl);
    }

    var countEl = document.createElement('span');
    countEl.className = 'team-item-count';
    var current = typeof team.currentCount === 'number' ? team.currentCount : '?';
    var max = typeof team.maxParticipants === 'number' ? team.maxParticipants : '?';
    countEl.textContent = current + ' / ' + max + '명';
    infoEl.appendChild(countEl);

    // 팀별 마감일 — 메인 카드의 "마감임박" 배지(activeTeamDeadline)는 대표 팀 하나만 보여주고,
    // 여기 팀 목록에는 예전부터 마감일 자체가 아예 안 나와 있었다(TeamResponse.deadline은 이미 응답에
    // 있었는데 화면에 안 그렸을 뿐). "언제까지인지 안 나온다"는 피드백으로 추가.
    if (team.deadline) {
      var deadlineDate = new Date(team.deadline);
      if (!isNaN(deadlineDate.getTime())) {
        var deadlineEl = document.createElement('span');
        deadlineEl.className = 'team-item-deadline';
        deadlineEl.textContent = '마감 ' + deadlineDate.toLocaleString('ko-KR') + '까지';
        infoEl.appendChild(deadlineEl);
      }
    }

    li.appendChild(infoEl);

    // 로그인 사용자 자신이 이미 이 팀의 참여자면(joinedByCurrentMember) "참가하기" 대신 "참여 취소"를
    // 보여준다(team-payment-enforcement) — 마이페이지(buyer-mypage.js)의 참여 취소 버튼과 동일한
    // 확인창 문구·비활성화 처리를 따른다.
    if (team.joinedByCurrentMember) {
      var leaveBtn = document.createElement('button');
      leaveBtn.type = 'button';
      leaveBtn.className = 'btn btn-ghost btn-sm team-item-leave-btn';
      leaveBtn.textContent = '참여 취소';
      leaveBtn.addEventListener('click', function () {
        handleLeaveTeam(team.teamId, leaveBtn);
      });
      li.appendChild(leaveBtn);
    } else if (!isNonBuyerMember()) {
      // 구매 권한이 없는 회원(SELLER/ADMIN)에게는 "참가하기"를 아예 그리지 않는다 — TeamService.join도
      // BUYER 전용이라 #product-actions와 같은 이유(product/purchase-visibility design.md).
      var joinBtn = document.createElement('button');
      joinBtn.type = 'button';
      joinBtn.className = 'btn btn-secondary btn-sm team-item-join-btn';
      joinBtn.textContent = '참가하기';
      joinBtn.disabled = !refundNoticeAccepted();
      joinBtn.addEventListener('click', function () {
        handleJoin(team.teamId, joinBtn);
      });
      li.appendChild(joinBtn);
    }

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
        // 참가 성공 시 결제 페이지로 강제 이동시킨다(team-payment-enforcement) — 배너로 안내만 하고
        // 그냥 남아 있으면 결제 없이도 참여자로 남는 문제가 있었다.
        window.location.href = 'checkout.html?productId=' + currentProductId + '&teamId=' + teamId;
      })
      .catch(function (err) {
        joinBtn.disabled = !refundNoticeAccepted();
        handleActionError(err, currentProductId);
      });
  }

  /**
   * 상품 상세 페이지의 "참여 취소" 버튼(team-payment-enforcement) — 확인창 문구·비활성화 처리는
   * buyer-mypage.js의 참여 취소 버튼과 동일하게 맞췄다.
   */
  function handleLeaveTeam(teamId, leaveBtn) {
    var confirmed = window.confirm(
      '이 공구팀 참여를 취소하시겠습니까? 결제하신 금액이 있으면 환불 요청이 자동으로 생성됩니다.');
    if (!confirmed) {
      return;
    }

    hidePageAlert();
    leaveBtn.disabled = true;

    window.Api.post('/teams/' + teamId + '/leave')
      .then(function () {
        loadTeams(currentProductId);
      })
      .catch(function (err) {
        leaveBtn.disabled = false;
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
        // 신설 성공 시 결제 페이지로 강제 이동시킨다(team-payment-enforcement, handleJoin과 동일한 이유).
        window.location.href = 'checkout.html?productId=' + currentProductId + '&teamId=' + newTeamId;
      })
      .catch(function (err) {
        handleActionError(err, currentProductId);
        updateCreateTeamButtonState();
      });
  }

  function handleBuyAlone() {
    window.location.href = 'checkout.html?productId=' + currentProductId;
  }

  // ---------- 찜(product/wishlist) ----------
  // 상품 상세 페이지 최초 도입(backlog 5번) — main.js(메인 페이지 카드)의 toggleWishlist 패턴과
  // 동일한 정책(멱등 POST/DELETE, 낙관적 토글, 비로그인은 로그인 페이지로, 403은 안내)을 따르되,
  // main.js는 인덱스 페이지 전용 스크립트라 그 클로저 내부 함수를 그대로 호출할 수 없어 여기서
  // 독립적으로 둔다.

  /**
   * 페이지 진입 시 하트의 초기 active 상태를 조회한다. 위시리스트에는 "이 상품이 찜 상태인지"
   * 개별 조회 API가 없어(docs/api/wishlist.md) 내 찜 전체 목록을 불러와 현재 상품 id가 포함돼
   * 있는지로 판정한다(main.js와 동일한 방식). 로그인한 구매자가 아니면 호출 자체를 생략한다.
   */
  function loadWishlistState(productId) {
    if (currentMemberRole !== 'BUYER') {
      return;
    }
    window.Api.get('/buyer/mypage/wishlist')
      .then(function (items) {
        var wished = (items || []).some(function (item) {
          return item.productId === productId;
        });
        wishlistBtnEl.classList.toggle('active', wished);
      })
      .catch(function () {
        // 조용히 무시 — 하트가 빈 상태로 남을 뿐 페이지 자체는 정상 동작한다(main.js와 동일).
      });
  }

  function handleToggleWishlist() {
    if (!currentMemberId) {
      window.location.href =
        '/login.html?redirect=' + encodeURIComponent(window.location.pathname + window.location.search);
      return;
    }

    var wasActive = wishlistBtnEl.classList.contains('active');
    wishlistBtnEl.disabled = true;

    var request = wasActive
      ? window.Api.del('/products/' + currentProductId + '/wishlist')
      : window.Api.post('/products/' + currentProductId + '/wishlist', {});

    request
      .then(function () {
        wishlistBtnEl.classList.toggle('active', !wasActive);
        // 헤더 찜 아이콘 옆 개수 뱃지(js/header-wishlist-badge.js)가 새로고침 없이도 바로 갱신되게
        // 알린다(main.js와 동일 이벤트 재사용).
        document.dispatchEvent(new CustomEvent('gong9ri:wishlist-changed'));
      })
      .catch(function (err) {
        // 찜은 구매자 전용(WishlistService.requireBuyer) — 판매자/관리자 계정으로 시도하면 403이 온다.
        if (err && err.status === 403) {
          showPageAlert('구매자 계정으로 로그인해야 찜할 수 있어요.', 'error');
        }
      })
      .then(function () {
        wishlistBtnEl.disabled = false;
      });
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

    // 작성자 사진 + 이름, 그 뒤에 별점(member/profile-image 노출). 이름과 별점이 한 덩어리 텍스트였는데
    // 아바타가 들어가면서 이름만 아바타 옆으로 묶이고 별점은 그 뒤에 따로 붙는다.
    var titleEl = document.createElement('span');
    titleEl.className = 'mypage-list-item__title';
    titleEl.appendChild(
        window.Avatar.withName(review.memberName || '', review.memberProfileImageUrl, 'sm'));
    var ratingEl = document.createElement('span');
    ratingEl.textContent = ' · ' + review.rating + '점';
    titleEl.appendChild(ratingEl);
    infoEl.appendChild(titleEl);

    var metaEl = document.createElement('span');
    metaEl.className = 'mypage-list-item__meta';
    metaEl.textContent = review.content || '';
    infoEl.appendChild(metaEl);

    // 작성일시(backlog 1번) — 본문(metaEl)과 시각적으로 구분되게 별도 줄로 둔다.
    var dateEl = document.createElement('span');
    dateEl.className = 'mypage-list-item__date';
    dateEl.textContent = formatAbsoluteDateTime(review.createdAt);
    infoEl.appendChild(dateEl);

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
    titleEl.appendChild(
        window.Avatar.withName(inquiry.memberName || '', inquiry.memberProfileImageUrl, 'sm'));
    var statusEl = document.createElement('span');
    statusEl.textContent = inquiry.answered ? ' · 답변완료' : ' · 미답변';
    titleEl.appendChild(statusEl);
    infoEl.appendChild(titleEl);

    var contentEl = document.createElement('span');
    contentEl.className = 'mypage-list-item__meta';
    contentEl.textContent = inquiry.content || '';
    infoEl.appendChild(contentEl);

    // 작성일시(backlog 1번) — 본문(contentEl)과 시각적으로 구분되게 별도 줄로 둔다.
    var dateEl = document.createElement('span');
    dateEl.className = 'mypage-list-item__date';
    dateEl.textContent = formatAbsoluteDateTime(inquiry.createdAt);
    infoEl.appendChild(dateEl);

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
    wishlistBtnEl.addEventListener('click', handleToggleWishlist);

    // header-auth.js가 이미 GET /api/auth/me를 호출하므로 그 결과를 재사용한다(중복 호출 방지).
    // 이 이벤트가 loadReviews()/loadInquiries()보다 늦게 올 수도 있어(비동기 순서 보장 없음), 도착하면
    // 다시 불러와서 "내가 쓴 리뷰/문의"의 수정/삭제 버튼과 판매자 답변 UI가 정확히 반영되게 한다.
    // 찜(product/wishlist) 초기 상태 조회도 이 이벤트로 role이 확정된 뒤에 한다(main.js와 동일 조건).
    document.addEventListener('gong9ri:auth-resolved', function (event) {
      var detail = event.detail || {};
      currentMemberId = detail.loggedIn && detail.member ? detail.member.memberId : null;
      currentMemberRole = detail.loggedIn && detail.member ? detail.member.role : null;
      applyPurchaseRoleVisibility();
      loadReviews(productId);
      loadInquiries(productId);
      loadWishlistState(productId);
      // 팀 목록도 다시 불러온다 — 이 이벤트가 loadTeams()보다 늦게 도착하면(비동기 순서 보장 없음)
      // 이미 그려진 "참가하기" 버튼이 role 확정 전 상태(currentMemberRole === null)로 남기 때문
      // (createTeamItem의 isNonBuyerMember() 판단, product/purchase-visibility design.md).
      loadTeams(productId);
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
