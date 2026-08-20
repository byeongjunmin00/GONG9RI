/**
 * main.js — 메인 페이지(`/`) 전용 스크립트
 *
 * GET /api/products 로 상품 목록을 가져와 카드 그리드로 렌더링한다.
 * - 로딩/빈 목록/에러 상태는 #product-status에 문구로 표시한다.
 * - "더 보기" 클릭 시 다음 page를 요청해 카드 뒤에 이어붙이고(append),
 *   totalElements만큼 다 불러왔으면 버튼을 숨긴다.
 * - 상품명/판매자명 등 사용자(판매자) 입력 문자열은 textContent로만 넣어 XSS를 방지한다.
 * - `?kakaoRoleMismatch=BUYER|SELLER` 쿼리파라미터가 있으면 카카오 로그인 role 불일치 안내
 *   배너(#page-alert)를 표시한다(login.js의 ?signup=success와 같은 패턴).
 * - 메인 히어로 캐러셀(#hero-carousel): 3장 고정(1.소개 2.실시간 인기 공구 3.신규 가입), 배경
 *   풀블리드 + 흰 글씨 + 하단 그라디언트 스크림, 5초 간격 자동 순환(translateX,
 *   왼쪽으로 넘어가는 모션) + 점 클릭으로 수동 전환. `prefers-reduced-motion: reduce`면 자동 순환을
 *   시작하지 않는다. 2번(인기 공구) 슬라이드만 실제 인기순 1위 상품이 있을 때 이미지/이름/참여인원/
 *   가격으로 채운다(product/category와 무관, 없으면 기본 문구 유지 — 가짜 상품을 지어내지 않음).
 * - 카테고리 바(#category-bar): product/category. 클릭 시 `?category=`를 URL에 반영하고
 *   목록을 처음부터(page 0) 다시 불러온다. 새로고침해도 선택이 유지되게 URL 쿼리파라미터를
 *   진실의 원천으로 쓴다(뒤로가기 히스토리 대응은 스코프 밖 — replaceState만 쓴다).
 * - "오픈예정" 탭(product/list-enhancements, [전체] 바로 다음 2번째 탭): 다른 카테고리 pill과 완전히
 *   동일한 배타적 단일 선택으로 동작한다 — 클릭 시 `category`를 해제하고 `?openSoon=true`를 반영,
 *   다른 카테고리 클릭 시 `openSoon`을 해제하고 `?category=`를 반영한다. `ProductCategory` enum의
 *   실제 값이 아니라 `openAt` 기준 시간 필터라, 서버에도 `category`와 별개의 쿼리파라미터로 보낸다.
 *   카테고리 탭(전체 제외)은 서버가 그 카테고리에 속하더라도 아직 오픈 전인 상품을 결과에서
 *   제외한다(docs/api/product.md) — 오픈예정 상품은 오픈 시각이 지나기 전까지 자신의 실제 카테고리
 *   탭에는 보이지 않고 [전체]·[오픈예정] 탭에서만 보인다.
 * - 카드 참여 진행바(product/list-progress): 상품 응답의 activeTeamCurrentCount/
 *   activeTeamTargetParticipants가 둘 다 있을 때만(RECRUITING 팀이 있을 때만) 그린다 — 이 값은
 *   서버가 캐시 없이 매 요청 최신으로 계산해 내려준다(docs/api/product.md 참고).
 * - 정렬(#sort-select, product/list-sort): "최신순"(LATEST, 기본값)/"인기순"(POPULAR) 중 선택하면
 *   `?sort=`를 URL에 반영하고 카테고리와 동일하게 목록을 처음부터 다시 불러온다.
 * - 검색(#search-form, product/list-search): 상품명 또는 판매자명 검색. 제출 시 `?keyword=`를 URL에
 *   반영하고 카테고리/정렬과 동일하게 처음부터 다시 불러온다. 서버는 검색어가 있으면 목록 캐시를
 *   타지 않는다(docs/api/product.md).
 * - 마감임박 배지: activeTeamDeadline까지 3일(DEADLINE_URGENT_DAYS) 이하로 남았을 때만 카드 이미지에
 *   배지를 그린다(product/list-sort). "마감임박순" 정렬(sort=DEADLINE)과는 별개 기능 — 정렬은 항상
 *   가능하고, 배지는 실제로 임박했을 때만 뜬다.
 * - 오픈예정 배지(product/product-launch): openAt이 미래 시각인 상품 카드에 "오픈예정" 배지를
 *   그린다(마감임박 배지와는 구조적으로 동시에 뜨지 않음 — 오픈 전 상품은 팀을 가질 수 없다).
 * - 찜 하트(product/wishlist): 로그인한 구매자만 실제로 토글된다(서버 최종 판정, 403은 조용히 무시).
 *   비로그인 클릭은 로그인 페이지로 이동(redirect 쿼리파라미터로 원래 페이지 복귀). 로그인한 회원
 *   정보는 js/header-auth.js가 발행하는 'gong9ri:auth-resolved' 이벤트로 재사용한다(다른 페이지들과
 *   동일 패턴) — 그 시점에 GET /buyer/mypage/wishlist를 한 번 불러와 이미 렌더링된 카드의 하트를
 *   뒤늦게 채운다.
 */
(function () {
  // 카카오 로그인 role 불일치 안내(?kakaoRoleMismatch=BUYER|SELLER) — 회원가입 페이지의 역할별
  // 카카오 버튼으로 이미 다른 role로 가입된 계정에 로그인하면 AuthController.kakaoCallback()이
  // 이 쿼리파라미터를 실어 메인 페이지(`/`)로 리다이렉트한다(login.js의 ?signup=success와 같은 패턴).
  // 로그인 자체는 기존 role 그대로 진행되므로, 실제로 로그인된 role을 안내 문구에 그대로 반영한다.
  (function showKakaoRoleMismatchBanner() {
    var pageAlertEl = document.getElementById('page-alert');
    var pageAlertTextEl = document.getElementById('page-alert-text');
    if (!pageAlertEl || !pageAlertTextEl) {
      return;
    }

    var roleLabels = { BUYER: '구매자', SELLER: '판매자' };
    var mismatchedRole = new URLSearchParams(window.location.search).get('kakaoRoleMismatch');
    var roleLabel = roleLabels[mismatchedRole];
    if (!roleLabel) {
      return;
    }

    pageAlertEl.hidden = false;
    pageAlertTextEl.textContent = '이미 ' + roleLabel + '로 가입되어 있어 ' + roleLabel + '로 로그인되었습니다.';
  })();

  // 메인 히어로 캐러셀 — 3장 고정(1.소개 2.실시간 인기 공구 3.신규 가입).
  // 원래 "슬림 공지 바"(promo-bar)와 소개 캐러셀 두 개가 나란히 있어 난잡해 보인다는 피드백으로
  // 하나로 합쳤다 — 크기는 기존 소개 캐러셀(큰 배너), 테마는 기존 공지 바(어두운 배경 + 흰 글씨)를
  // 따르고, 전 슬라이드가 배경을 꽉 채운다(2번은 실제 상품 이미지, 나머지는 브랜드 그라디언트).
  // 2·3번은 정적 마크업(index.html)에 이미 있고, 2번(인기 공구)만 실제 인기순 1위 상품이 있을 때
  // 이미지/이름/참여인원/가격으로 덮어쓴다 — 없으면 기본 문구(그리드 스크롤 링크) 그대로 둔다(가짜
  // 상품/이벤트를 지어내지 않음). 트랙을 translateX로 밀어 왼쪽으로 넘어가는 모션으로 자동 순환한다.
  (function setUpHeroCarousel() {
    var carouselEl = document.getElementById('hero-carousel');
    var trackEl = document.getElementById('hero-carousel-track');
    var dotsEl = document.getElementById('hero-carousel-dots');
    var prevBtn = document.getElementById('hero-carousel-prev');
    var nextBtn = document.getElementById('hero-carousel-next');
    var popularSlideEl = document.getElementById('hero-carousel-popular-slide');
    var popularImageEl = document.getElementById('hero-carousel-popular-image');
    var popularTitleEl = document.getElementById('hero-carousel-popular-title');
    var popularDescEl = document.getElementById('hero-carousel-popular-desc');
    if (!carouselEl || !trackEl || !dotsEl) {
      return;
    }

    var AUTO_ROTATE_MS = 5000;
    var current = 0;
    var timer = null;
    var dots = [];
    var slideEls = Array.prototype.slice.call(trackEl.children);
    var prefersReducedMotion =
      window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    function render() {
      trackEl.style.transform = 'translateX(-' + current * 100 + '%)';
      dots.forEach(function (dot, index) {
        dot.classList.toggle('active', index === current);
      });
    }

    function goTo(index) {
      current = (index + slideEls.length) % slideEls.length;
      render();
    }

    function startAutoRotate() {
      if (prefersReducedMotion || timer || slideEls.length <= 1) {
        return;
      }
      timer = window.setInterval(function () {
        goTo(current + 1);
      }, AUTO_ROTATE_MS);
    }

    function stopAutoRotate() {
      if (timer) {
        window.clearInterval(timer);
        timer = null;
      }
    }

    slideEls.forEach(function (_, index) {
      var dotEl = document.createElement('button');
      dotEl.type = 'button';
      dotEl.className = 'hero-carousel-dot';
      dotEl.setAttribute('aria-label', (index + 1) + '번째 슬라이드로 이동');
      dotEl.addEventListener('click', function () {
        goToWithReset(index);
      });
      dotsEl.appendChild(dotEl);
      dots.push(dotEl);
    });

    // 화살표 클릭도 점 클릭과 동일하게 goTo() 재사용 + 자동 순환 타이머 리셋(눌러서 넘긴 직후
    // 곧바로 자동으로 또 넘어가 버리면 어색하니, 리셋해서 그 슬라이드를 최소 AUTO_ROTATE_MS만큼은
    // 보게 한다).
    function goToWithReset(index) {
      goTo(index);
      stopAutoRotate();
      startAutoRotate();
    }

    if (prevBtn) {
      prevBtn.addEventListener('click', function () {
        goToWithReset(current - 1);
      });
    }
    if (nextBtn) {
      nextBtn.addEventListener('click', function () {
        goToWithReset(current + 1);
      });
    }

    // 실제 인기순 1위 상품을 찾아 2번 슬라이드를 채운다 — 진행 중인 팀이 있는 상품이 하나도 없으면
    // (RECRUITING 팀 없음) 그 슬라이드는 기본 문구(그리드 스크롤 링크) 그대로 둔다(가짜 상품 금지).
    if (popularSlideEl && window.Api && typeof window.Api.get === 'function') {
      window.Api.get('/products?sort=POPULAR&size=1')
        .then(function (data) {
          var top = data && data.content && data.content[0];
          if (!top || typeof top.activeTeamCurrentCount !== 'number') {
            return;
          }
          popularSlideEl.href = 'product.html?id=' + top.productId;
          if (popularTitleEl) {
            popularTitleEl.textContent = top.name || popularTitleEl.textContent;
          }
          if (popularImageEl && top.imageUrl) {
            popularImageEl.src = top.imageUrl;
            popularImageEl.alt = top.name || '';
            popularImageEl.hidden = false;
          }
          if (popularDescEl) {
            var descParts = [top.activeTeamCurrentCount + '명 참여 중'];
            if (typeof top.basePrice === 'number' && typeof top.bestPrice === 'number') {
              descParts.push(formatPrice(top.basePrice) + ' → ' + formatPrice(top.bestPrice));
            }
            popularDescEl.textContent = descParts.join(' · ');
            popularDescEl.hidden = false;
          }
        })
        .catch(function () {
          // 실패해도 조용히 기본 문구로 유지 — 캐러셀은 장식적 요소라 에러를 노출하지 않는다.
        });
    }

    carouselEl.addEventListener('mouseenter', stopAutoRotate);
    carouselEl.addEventListener('mouseleave', startAutoRotate);

    render();
    startAutoRotate();
  })();

  var PRODUCTS_PATH = '/products';

  // "오픈예정"(openSoon)은 ProductCategory enum의 실제 값이 아니라 openAt 기준 시간 필터라
  // value를 null로 두고 openSoon 플래그로 구분한다(다른 항목과 달리 category 조건이 아니라
  // openSoon 쿼리파라미터로 서버에 전달됨, docs/dev/product/list-enhancements/design.md).
  var CATEGORIES = [
    { value: null, label: '전체' },
    { value: null, label: '오픈예정', openSoon: true },
    { value: 'FOOD', label: '식품' },
    { value: 'LIVING', label: '생활/주방' },
    { value: 'BEAUTY', label: '뷰티' },
    { value: 'FASHION', label: '패션/잡화' },
    { value: 'DIGITAL', label: '디지털/가전' },
    { value: 'ETC', label: '기타' },
  ];

  var gridEl = document.getElementById('product-grid');
  var statusEl = document.getElementById('product-status');
  var loadMoreBtn = document.getElementById('load-more-btn');
  var categoryBarEl = document.getElementById('category-bar');
  var sortSelectEl = document.getElementById('sort-select');
  var searchFormEl = document.getElementById('search-form');
  var searchInputEl = document.getElementById('search-input');

  if (!gridEl || !statusEl || !loadMoreBtn || !categoryBarEl || !sortSelectEl || !searchFormEl || !searchInputEl) {
    return;
  }

  var state = {
    page: -1, // 아직 아무 페이지도 불러오지 않음
    loadedCount: 0,
    totalElements: 0,
    loading: false,
    category: new URLSearchParams(window.location.search).get('category') || null,
    sort: new URLSearchParams(window.location.search).get('sort') || 'LATEST',
    keyword: new URLSearchParams(window.location.search).get('keyword') || null,
    openSoon: new URLSearchParams(window.location.search).get('openSoon') === 'true',
  };
  sortSelectEl.value = state.sort;
  searchInputEl.value = state.keyword || '';

  // 찜(product/wishlist) — 로그인한 구매자만 실제로 토글 가능하다(서버가 최종 판정). 카드는 이미
  // 렌더링된 뒤에 로그인 상태가 비동기로 확정되므로(js/header-auth.js의 'gong9ri:auth-resolved'),
  // 하트 버튼을 productId로 찾아둔 뒤 위시리스트 조회가 끝나면 뒤늦게 채운다.
  var currentMemberId = null;
  var wishlistedProductIds = new Set();
  var heartElsByProductId = {};

  document.addEventListener('gong9ri:auth-resolved', function (event) {
    var detail = event.detail || {};
    currentMemberId = detail.loggedIn && detail.member ? detail.member.memberId : null;
    var role = detail.loggedIn && detail.member ? detail.member.role : null;
    if (!currentMemberId || role !== 'BUYER') {
      return;
    }
    window.Api.get('/buyer/mypage/wishlist')
      .then(function (items) {
        (items || []).forEach(function (item) {
          wishlistedProductIds.add(item.productId);
          var heartEl = heartElsByProductId[item.productId];
          if (heartEl) {
            heartEl.classList.add('active');
          }
        });
      })
      .catch(function () {
        // 조용히 무시 — 하트가 전부 빈 상태로 남을 뿐, 카드 렌더링 자체를 막지 않는다.
      });
  });

  function toggleWishlist(productId, heartEl) {
    if (!currentMemberId) {
      window.location.href =
        '/login.html?redirect=' + encodeURIComponent(window.location.pathname + window.location.search);
      return;
    }

    var wasActive = heartEl.classList.contains('active');
    heartEl.disabled = true;

    var request = wasActive
      ? window.Api.del('/products/' + productId + '/wishlist')
      : window.Api.post('/products/' + productId + '/wishlist', {});

    request
      .then(function () {
        heartEl.classList.toggle('active', !wasActive);
        if (wasActive) {
          wishlistedProductIds.delete(productId);
        } else {
          wishlistedProductIds.add(productId);
        }
        // 헤더 찜 아이콘 옆 개수 뱃지(js/header-wishlist-badge.js)가 새로고침 없이도 바로 갱신되게
        // 알린다 — 전에는 페이지를 새로고침해야만 반영돼서 사용자가 "안 눌리나?" 헷갈렸음(2026-08-20).
        document.dispatchEvent(new CustomEvent('gong9ri:wishlist-changed'));
      })
      .catch(function (err) {
        // 찜은 구매자 전용(WishlistService.requireBuyer) — 판매자 계정으로 시도하면 403이 온다.
        // 예전엔 여기서 조용히 무시해서 "하트가 눌러지지 않는다"는 걸 사용자가 오류로 착각했다.
        if (err && err.status === 403) {
          showPageNotice('구매자 계정으로 로그인해야 찜할 수 있어요.', 'error');
        }
      })
      .then(function () {
        heartEl.disabled = false;
      });
  }

  /**
   * 페이지 상단 공용 안내 배너(#page-alert) — 원래 카카오 로그인 role 불일치 안내(showKakaoRoleMismatchBanner)
   * 전용이었는데, 찜 403 같은 "조용히 무시하면 사용자가 오류로 착각하는" 케이스에도 재사용한다.
   */
  function showPageNotice(text, variant) {
    var pageAlertEl = document.getElementById('page-alert');
    var pageAlertTextEl = document.getElementById('page-alert-text');
    if (!pageAlertEl || !pageAlertTextEl) {
      return;
    }
    pageAlertEl.hidden = false;
    pageAlertEl.className = 'form-alert form-alert--' + (variant || 'success');
    pageAlertTextEl.textContent = text;
    pageAlertEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  function formatPrice(value) {
    if (typeof value !== 'number') {
      return '';
    }
    return value.toLocaleString('ko-KR') + '원';
  }

  function formatPriceLabel(maxParticipants) {
    if (typeof maxParticipants !== 'number') {
      return '';
    }
    return maxParticipants + '인 모이면 1인당 최저가';
  }

  /**
   * 상품 1개 → 카드 마크업(design-system.html의 .card 구조 재사용).
   * 상세 페이지(product.html)로 이동한다. 라우팅 방식은 쿼리스트링(?id=)이다
   * (정적 리소스 서빙 구조상 /products/{id} 경로 세그먼트 매핑이 없어서다. frontend/product-detail design.md 참고).
   */
  var DEADLINE_URGENT_DAYS = 3;

  function createProductCard(product) {
    var link = document.createElement('a');
    link.className = 'card';
    link.href = 'product.html?id=' + product.productId;
    link.setAttribute('aria-label', (product.name || '상품') + ' 상세보기');

    var imageEl = document.createElement('div');
    imageEl.className = 'card-image';
    if (product.imageUrl) {
      var imgEl = document.createElement('img');
      imgEl.src = product.imageUrl;
      imgEl.alt = product.name || '';
      imageEl.appendChild(imgEl);
    }

    // 마감임박 배지(product/list-sort) — 대표 팀(진행바와 같은 팀) 마감까지 3일 이하로 남았을 때만
    // 노출한다. 모든 카드에 "N일 남음"을 항상 보여주면 정보 과잉이라, 실제로 급한 것만 강조한다
    // (와디즈/텀블벅 등 참고 사이트도 "마감임박"을 상시 카운터가 아니라 별도 태그로 씀).
    // 오픈예정(product/product-launch) — openAt이 미래인 상품은 아직 RECRUITING 팀을 가질 수 없어
    // (TeamService.create()가 서버에서 거절) 마감임박 배지와 동시에 뜨는 경우가 구조적으로 없다.
    var openAtBadgeEl = createOpenAtBadge(product.openAt);
    if (openAtBadgeEl) {
      imageEl.appendChild(openAtBadgeEl);
    } else {
      var deadlineBadgeEl = createDeadlineBadge(product.activeTeamDeadline);
      if (deadlineBadgeEl) {
        imageEl.appendChild(deadlineBadgeEl);
      }
    }

    // 찜 하트 — 카드 링크(<a>) 안에 있어 클릭 시 상세 페이지로 이동하지 않게 이벤트 전파를 막는다.
    var heartEl = document.createElement('button');
    heartEl.type = 'button';
    heartEl.className = 'card-wishlist-btn';
    heartEl.setAttribute('aria-label', '찜하기');
    heartEl.innerHTML =
      '<svg viewBox="0 0 24 24" width="18" height="18"><path d="M12 21s-7.5-4.6-10.2-9.1C.1 8.8 1.4 5 5 4.2c2.1-.5 4.1.4 5.2 2.1a.9.9 0 0 0 1.6 0C13 4.6 15 3.7 17.1 4.2c3.5.8 4.9 4.6 3.1 7.7C19.5 16.4 12 21 12 21z"/></svg>';
    heartEl.addEventListener('click', function (event) {
      event.preventDefault();
      event.stopPropagation();
      toggleWishlist(product.productId, heartEl);
    });
    if (wishlistedProductIds.has(product.productId)) {
      heartEl.classList.add('active');
    }
    heartElsByProductId[product.productId] = heartEl;
    imageEl.appendChild(heartEl);

    link.appendChild(imageEl);

    var bodyEl = document.createElement('div');
    bodyEl.className = 'card-body';

    var sellerRowEl = document.createElement('div');
    sellerRowEl.className = 'card-seller-row';

    var sellerEl = document.createElement('span');
    sellerEl.className = 'card-seller';
    sellerEl.textContent = product.sellerName || '';
    sellerRowEl.appendChild(sellerEl);

    // 판매자 신뢰 배지(product/seller-trust) — 이 판매자의 리뷰 평균 평점·개수가 기준을 넘을 때만
    // 노출한다(ProductService.isTrustedSeller). 새 평판 시스템을 별도로 만들지 않고 이미 있는 리뷰
    // 데이터로만 판단해, 근거 없는 "인기 판매자" 같은 막연한 배지보다 신뢰할 수 있는 신호로 삼는다.
    if (product.sellerTrustedBadge) {
      var trustEl = document.createElement('span');
      trustEl.className = 'card-seller-trust';
      trustEl.textContent = '신뢰 판매자';
      sellerRowEl.appendChild(trustEl);
    }

    bodyEl.appendChild(sellerRowEl);

    var titleEl = document.createElement('h3');
    titleEl.className = 'card-title';
    titleEl.textContent = product.name || '';
    bodyEl.appendChild(titleEl);

    var ratingRowEl = createRatingRowElement(product.ratingAverage, product.reviewCount);
    bodyEl.appendChild(ratingRowEl);

    var priceRowEl = document.createElement('div');
    priceRowEl.className = 'card-price-row';

    var baseEl = document.createElement('span');
    baseEl.className = 'card-price-base';
    baseEl.textContent = formatPrice(product.basePrice);
    priceRowEl.appendChild(baseEl);

    var bestEl = document.createElement('span');
    bestEl.className = 'card-price-best';
    bestEl.textContent = formatPrice(product.bestPrice);
    priceRowEl.appendChild(bestEl);

    bodyEl.appendChild(priceRowEl);

    var labelEl = document.createElement('span');
    labelEl.className = 'card-price-label';
    labelEl.textContent = formatPriceLabel(product.maxParticipants);
    bodyEl.appendChild(labelEl);

    var progressEl = createProgressBar(product);
    if (progressEl) {
      bodyEl.appendChild(progressEl);
    } else {
      bodyEl.appendChild(createEmptyTeamBadge());
    }

    link.appendChild(bodyEl);

    return link;
  }

  /**
   * 진행 중인 팀이 있는 상품에만(product/list-progress) "N명 참여중 / M명 달성 시" 진행바를 그린다.
   * activeTeamCurrentCount/activeTeamTargetParticipants가 둘 다 없으면(RECRUITING 팀 없음) null을
   * 반환해 진행바 자체를 렌더링하지 않는다.
   */
  /**
   * @param {string} openAtIso  ProductSummaryResponse.openAt(ISO 문자열) 또는 null
   * @returns {?HTMLElement} 미래 시각이면 "오픈예정" 배지, 아니면(이미 공개됨) null
   */
  function createOpenAtBadge(openAtIso) {
    if (!openAtIso) {
      return null;
    }
    var openAt = new Date(openAtIso);
    if (isNaN(openAt.getTime()) || openAt.getTime() <= Date.now()) {
      return null;
    }

    var badgeEl = document.createElement('span');
    badgeEl.className = 'badge badge-upcoming';
    badgeEl.textContent = '오픈예정';
    return badgeEl;
  }

  /**
   * @param {string} deadlineIso  ProductSummaryResponse.activeTeamDeadline(ISO 문자열) 또는 null
   * @returns {?HTMLElement} 마감까지 DEADLINE_URGENT_DAYS일 이하로 남았을 때만 배지 엘리먼트, 아니면 null
   */
  function createDeadlineBadge(deadlineIso) {
    if (!deadlineIso) {
      return null;
    }
    var deadline = new Date(deadlineIso);
    if (isNaN(deadline.getTime())) {
      return null;
    }
    var msRemaining = deadline.getTime() - Date.now();
    var daysRemaining = Math.ceil(msRemaining / (1000 * 60 * 60 * 24));
    if (daysRemaining > DEADLINE_URGENT_DAYS) {
      return null;
    }

    var badgeEl = document.createElement('span');
    badgeEl.className = 'badge badge-urgent';
    badgeEl.textContent = daysRemaining <= 0 ? '오늘 마감' : daysRemaining + '일 남음';
    return badgeEl;
  }

  function createProgressBar(product) {
    var current = product.activeTeamCurrentCount;
    var target = product.activeTeamTargetParticipants;
    if (typeof current !== 'number' || typeof target !== 'number' || target <= 0) {
      return null;
    }

    var percent = Math.min(100, Math.round((current / target) * 100));

    var wrapEl = document.createElement('div');
    wrapEl.className = 'card-progress';

    var labelEl = document.createElement('div');
    labelEl.className = 'card-progress-label';
    var currentEl = document.createElement('b');
    currentEl.textContent = String(current) + '명';
    labelEl.appendChild(currentEl);
    labelEl.appendChild(document.createTextNode(' 참여 중 · ' + target + '명 달성 시 성사'));
    wrapEl.appendChild(labelEl);

    var trackEl = document.createElement('div');
    trackEl.className = 'card-progress-track';
    var fillEl = document.createElement('div');
    fillEl.className = 'card-progress-fill';
    fillEl.style.width = percent + '%';
    trackEl.appendChild(fillEl);
    wrapEl.appendChild(trackEl);

    // 달성% 배지 — 와디즈/텀블벅 등 실제 크라우드펀딩 사이트에서 흔히 쓰는 강조 표시(사용자 요청).
    var percentEl = document.createElement('span');
    percentEl.className = 'card-progress-percent';
    percentEl.textContent = percent + '% 달성';
    wrapEl.appendChild(percentEl);

    return wrapEl;
  }

  /**
   * 진행 중인 팀이 없는 상품에 "🔥 첫 공구팀 신설하고 최저가 도전!" 컴팩트 뱃지를 그린다.
   */
  function createEmptyTeamBadge() {
    var badgeEl = document.createElement('div');
    badgeEl.className = 'card-no-team-badge';
    badgeEl.textContent = '🔥 첫 공구팀 신설하고 최저가 도전!';
    return badgeEl;
  }

  function createRatingRowElement(ratingAverage, reviewCount) {
    var rowEl = document.createElement('div');
    rowEl.className = 'card-rating-row';

    var rating = typeof ratingAverage === 'number' ? ratingAverage : 0;
    var count = typeof reviewCount === 'number' ? reviewCount : 0;

    var fullStars = Math.min(5, Math.max(0, Math.round(rating)));
    var starsEl = document.createElement('span');
    starsEl.className = 'card-rating-stars';

    var starsHtml = '';
    for (var i = 0; i < 5; i++) {
      if (i < fullStars) {
        starsHtml += '★';
      } else {
        starsHtml += '<span class="star-empty">☆</span>';
      }
    }
    starsEl.innerHTML = starsHtml;
    rowEl.appendChild(starsEl);

    var scoreEl = document.createElement('span');
    scoreEl.className = 'card-rating-score';
    scoreEl.textContent = rating > 0 ? rating.toFixed(1) : '0.0';
    rowEl.appendChild(scoreEl);

    var countEl = document.createElement('span');
    countEl.className = 'card-rating-count';
    countEl.textContent = '(리뷰 ' + count + '개)';
    rowEl.appendChild(countEl);

    return rowEl;
  }

  function renderProducts(products) {
    var fragment = document.createDocumentFragment();
    products.forEach(function (product) {
      fragment.appendChild(createProductCard(product));
    });
    gridEl.appendChild(fragment);
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

  function updateLoadMoreButton() {
    var hasMore = state.loadedCount < state.totalElements;
    if (!hasMore) {
      loadMoreBtn.hidden = true;
      return;
    }
    loadMoreBtn.hidden = false;
    loadMoreBtn.disabled = false;
    loadMoreBtn.textContent = '더 보기';
  }

  /**
   * @param {number} page 0-based 페이지 번호
   */
  function fetchProducts(page) {
    state.loading = true;

    if (page === 0) {
      showStatus('상품을 불러오는 중입니다...', 'loading');
    } else {
      loadMoreBtn.disabled = true;
      loadMoreBtn.textContent = '불러오는 중...';
    }

    var params = [];
    if (page > 0) {
      params.push('page=' + page);
    }
    if (state.category) {
      params.push('category=' + encodeURIComponent(state.category));
    }
    if (state.sort) {
      params.push('sort=' + encodeURIComponent(state.sort));
    }
    if (state.keyword) {
      params.push('keyword=' + encodeURIComponent(state.keyword));
    }
    if (state.openSoon) {
      params.push('openSoon=true');
    }
    var path = params.length > 0 ? PRODUCTS_PATH + '?' + params.join('&') : PRODUCTS_PATH;

    return window.Api.get(path)
      .then(function (data) {
        state.loading = false;

        var content = (data && data.content) || [];
        state.page = typeof (data && data.page) === 'number' ? data.page : page;
        state.totalElements =
          typeof (data && data.totalElements) === 'number'
            ? data.totalElements
            : state.loadedCount + content.length;
        state.loadedCount += content.length;

        if (page === 0 && content.length === 0) {
          var emptyMessage = state.openSoon
            ? '아직 오픈예정으로 등록된 상품이 없습니다. 곧 새로운 공동구매가 준비될 예정이에요!'
            : '아직 등록된 상품이 없습니다. 곧 새로운 공동구매가 열릴 예정이에요!';
          showStatus(emptyMessage, 'empty');
          loadMoreBtn.hidden = true;
          return;
        }

        hideStatus();
        renderProducts(content);
        updateLoadMoreButton();
      })
      .catch(function (err) {
        state.loading = false;
        console.error('[main.js] failed to load products:', err);

        if (page === 0) {
          var message = (err && err.message) || '상품 목록을 불러오지 못했습니다.';
          showStatus(message, 'error');
          loadMoreBtn.hidden = true;
        } else {
          // 이미 렌더링된 카드는 유지하고, 재시도할 수 있게 버튼만 되돌린다.
          loadMoreBtn.disabled = false;
          loadMoreBtn.textContent = '더 보기';
          showStatus('상품을 더 불러오지 못했습니다. 잠시 후 다시 시도해주세요.', 'error');
        }
      });
  }

  loadMoreBtn.addEventListener('click', function () {
    if (state.loading) {
      return;
    }
    fetchProducts(state.page + 1);
  });

  /** 카테고리 변경 시 목록을 처음부터 다시 불러온다(기존 페이지네이션 상태를 전부 초기화). */
  function resetAndReload() {
    state.page = -1;
    state.loadedCount = 0;
    state.totalElements = 0;
    gridEl.innerHTML = '';
    fetchProducts(0);
  }

  /**
   * "전체"와 "오픈예정" 둘 다 category.value가 null이라(오픈예정은 실제 카테고리 값이 아니므로)
   * openSoon 플래그까지 함께 봐야 어느 pill이 선택 상태인지 구분할 수 있다.
   */
  function isCategorySelected(category) {
    if (category.openSoon) {
      return state.openSoon === true;
    }
    return !state.openSoon && state.category === category.value;
  }

  function renderCategoryBar() {
    var fragment = document.createDocumentFragment();
    CATEGORIES.forEach(function (category) {
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'category-pill' + (isCategorySelected(category) ? ' active' : '');
      btn.textContent = category.label;
      btn.addEventListener('click', function () {
        if (isCategorySelected(category)) {
          return;
        }
        // 배타적 단일 선택 — 오픈예정을 고르면 category를 비우고, 카테고리를 고르면 openSoon을 끈다.
        state.category = category.openSoon ? null : category.value;
        state.openSoon = !!category.openSoon;

        var url = new URL(window.location.href);
        if (state.category) {
          url.searchParams.set('category', state.category);
        } else {
          url.searchParams.delete('category');
        }
        if (state.openSoon) {
          url.searchParams.set('openSoon', 'true');
        } else {
          url.searchParams.delete('openSoon');
        }
        window.history.replaceState(null, '', url.pathname + url.search);

        categoryBarEl.querySelectorAll('.category-pill').forEach(function (pill) {
          pill.classList.remove('active');
        });
        btn.classList.add('active');

        resetAndReload();
      });
      fragment.appendChild(btn);
    });
    categoryBarEl.appendChild(fragment);
  }

  function submitSearch(keyword) {
    state.keyword = keyword || null;

    var url = new URL(window.location.href);
    if (state.keyword) {
      url.searchParams.set('keyword', state.keyword);
    } else {
      url.searchParams.delete('keyword');
    }
    window.history.replaceState(null, '', url.pathname + url.search);

    resetAndReload();
  }

  searchFormEl.addEventListener('submit', function (event) {
    event.preventDefault();
    submitSearch(searchInputEl.value.trim());
  });

  sortSelectEl.addEventListener('change', function () {
    state.sort = sortSelectEl.value || 'LATEST';

    var url = new URL(window.location.href);
    url.searchParams.set('sort', state.sort);
    window.history.replaceState(null, '', url.pathname + url.search);

    resetAndReload();
  });

  renderCategoryBar();
  fetchProducts(0);
})();
