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
 * - 광고 배너(#promo-bar): 프론트 전용 장식(백엔드 연동 없음, product/category와 무관). 한 줄짜리
 *   공지 문구 3개를 4초 간격으로 자동 순환하고(opacity 페이드), 점 클릭으로 수동 전환도 가능하다.
 *   `prefers-reduced-motion: reduce`면 자동 순환을 시작하지 않는다. "자세히 보기"는 실제 이벤트
 *   페이지가 없어 상품 그리드로 스크롤 이동하는 앵커 링크로 둔다(죽은 링크를 만들지 않기 위함).
 * - 카테고리 바(#category-bar): product/category. 클릭 시 `?category=`를 URL에 반영하고
 *   목록을 처음부터(page 0) 다시 불러온다. 새로고침해도 선택이 유지되게 URL 쿼리파라미터를
 *   진실의 원천으로 쓴다(뒤로가기 히스토리 대응은 스코프 밖 — replaceState만 쓴다).
 * - 카드 참여 진행바(product/list-progress): 상품 응답의 activeTeamCurrentCount/
 *   activeTeamTargetParticipants가 둘 다 있을 때만(RECRUITING 팀이 있을 때만) 그린다 — 이 값은
 *   서버가 캐시 없이 매 요청 최신으로 계산해 내려준다(docs/api/product.md 참고).
 * - 정렬(#sort-select, product/list-sort): "최신순"(LATEST, 기본값)/"인기순"(POPULAR) 중 선택하면
 *   `?sort=`를 URL에 반영하고 카테고리와 동일하게 목록을 처음부터 다시 불러온다.
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

  // 광고 배너 — 슬림 공지 바. 프론트 전용 장식이지만 "자세히 보기"는 각 슬라이드 내용과 실제로
  // 연결되는 링크여야 한다(죽은 링크 금지, design.md 참고) — product-grid와 무관하게 독립적으로
  // 동작해야 해서 별도 IIFE로 분리한다.
  (function setUpPromoBar() {
    var barEl = document.getElementById('promo-bar');
    var trackEl = document.getElementById('promo-bar-track');
    var dotsEl = document.getElementById('promo-bar-dots');
    var ctaEl = document.getElementById('promo-bar-cta');
    if (!barEl || !trackEl || !dotsEl || !ctaEl) {
      return;
    }

    // 1번 슬라이드(인기 상품)는 실제 데이터가 없으면 "인기 급상승" 같은 근거 없는 문구를 지어내지
    // 않는다 — fetchPopularProductSlide()가 실제 인기순 1위 상품을 찾으면 그때 이 자리를 채운다.
    var SLIDES = [
      { emoji: '🔥', text: '실시간 인기 공구', highlight: '지금 가장 많이 모인 상품 보러가기', link: '#product-grid', ctaLabel: '자세히 보기 →' },
      { emoji: '🎉', text: '신규 가입하면', highlight: '카카오톡으로 3초만에 시작', link: '/api/auth/kakao/login', ctaLabel: '가입하기 →' },
      { emoji: '💸', text: '모일수록 저렴해지는', highlight: '지금 진행 중인 공구팀 둘러보기', link: '#product-grid', ctaLabel: '자세히 보기 →' },
    ];
    var AUTO_ROTATE_MS = 4000;
    var current = 0;
    var timer = null;
    var prefersReducedMotion =
      window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    var slideEls = [];
    var textEls = [];
    var boldEls = [];
    var dots = [];
    SLIDES.forEach(function (slide, index) {
      var slideEl = document.createElement('div');
      slideEl.className = 'promo-bar-slide';

      var emojiEl = document.createElement('span');
      emojiEl.className = 'promo-bar-slide__emoji';
      emojiEl.textContent = slide.emoji;
      slideEl.appendChild(emojiEl);

      var textEl = document.createElement('span');
      textEl.className = 'promo-bar-slide__text';
      textEl.textContent = slide.text + ' ';
      var boldEl = document.createElement('b');
      boldEl.textContent = slide.highlight;
      textEl.appendChild(boldEl);
      slideEl.appendChild(textEl);

      trackEl.appendChild(slideEl);
      slideEls.push(slideEl);
      textEls.push(textEl);
      boldEls.push(boldEl);

      var dotEl = document.createElement('button');
      dotEl.type = 'button';
      dotEl.className = 'promo-bar-dot';
      dotEl.setAttribute('aria-label', (index + 1) + '번째 공지로 이동');
      dotEl.addEventListener('click', function () {
        goTo(index);
        stopAutoRotate();
        startAutoRotate();
      });
      dotsEl.appendChild(dotEl);
      dots.push(dotEl);
    });

    function render() {
      slideEls.forEach(function (slideEl, index) {
        slideEl.classList.toggle('active', index === current);
      });
      dots.forEach(function (dot, index) {
        dot.classList.toggle('active', index === current);
      });
      ctaEl.href = SLIDES[current].link;
      ctaEl.textContent = SLIDES[current].ctaLabel;
    }

    function goTo(index) {
      current = (index + SLIDES.length) % SLIDES.length;
      render();
    }

    function startAutoRotate() {
      if (prefersReducedMotion || timer) {
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

    barEl.addEventListener('mouseenter', stopAutoRotate);
    barEl.addEventListener('mouseleave', startAutoRotate);

    // 실제 인기순 1위 상품을 찾아 1번 슬라이드를 채운다 — 진행 중인 팀이 있는 상품이 하나도 없으면
    // (RECRUITING 팀 없음) 그 슬라이드는 일반 문구로 그대로 둔다(가짜 상품/이벤트를 지어내지 않음).
    if (window.Api && typeof window.Api.get === 'function') {
      window.Api.get('/products?sort=POPULAR&size=1')
        .then(function (data) {
          var top = data && data.content && data.content[0];
          if (!top || typeof top.activeTeamCurrentCount !== 'number') {
            return;
          }
          SLIDES[0].highlight = top.name + ' · ' + top.activeTeamCurrentCount + '명 참여 중';
          SLIDES[0].link = 'product.html?id=' + top.productId;
          textEls[0].firstChild.textContent = SLIDES[0].text + ' ';
          boldEls[0].textContent = SLIDES[0].highlight;
          if (current === 0) {
            ctaEl.href = SLIDES[0].link;
          }
        })
        .catch(function () {
          // 실패해도 조용히 기본 문구(그리드 스크롤 링크)로 유지 — 배너는 장식이라 에러를 노출하지 않는다.
        });
    }

    render();
    startAutoRotate();
  })();

  var PRODUCTS_PATH = '/products';

  var CATEGORIES = [
    { value: null, label: '전체' },
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

  if (!gridEl || !statusEl || !loadMoreBtn || !categoryBarEl || !sortSelectEl) {
    return;
  }

  var state = {
    page: -1, // 아직 아무 페이지도 불러오지 않음
    loadedCount: 0,
    totalElements: 0,
    loading: false,
    category: new URLSearchParams(window.location.search).get('category') || null,
    sort: new URLSearchParams(window.location.search).get('sort') || 'LATEST',
  };
  sortSelectEl.value = state.sort;

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
    link.appendChild(imageEl);

    var bodyEl = document.createElement('div');
    bodyEl.className = 'card-body';

    var sellerEl = document.createElement('span');
    sellerEl.className = 'card-seller';
    sellerEl.textContent = product.sellerName || '';
    bodyEl.appendChild(sellerEl);

    var titleEl = document.createElement('h3');
    titleEl.className = 'card-title';
    titleEl.textContent = product.name || '';
    bodyEl.appendChild(titleEl);

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
    }

    link.appendChild(bodyEl);

    return link;
  }

  /**
   * 진행 중인 팀이 있는 상품에만(product/list-progress) "N명 참여중 / M명 달성 시" 진행바를 그린다.
   * activeTeamCurrentCount/activeTeamTargetParticipants가 둘 다 없으면(RECRUITING 팀 없음) null을
   * 반환해 진행바 자체를 렌더링하지 않는다.
   */
  function createProgressBar(product) {
    var current = product.activeTeamCurrentCount;
    var target = product.activeTeamTargetParticipants;
    if (typeof current !== 'number' || typeof target !== 'number' || target <= 0) {
      return null;
    }

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
    fillEl.style.width = Math.min(100, Math.round((current / target) * 100)) + '%';
    trackEl.appendChild(fillEl);
    wrapEl.appendChild(trackEl);

    return wrapEl;
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
          showStatus('아직 등록된 상품이 없습니다. 곧 새로운 공동구매가 열릴 예정이에요!', 'empty');
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

  function renderCategoryBar() {
    var fragment = document.createDocumentFragment();
    CATEGORIES.forEach(function (category) {
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'category-pill' + (state.category === category.value ? ' active' : '');
      btn.textContent = category.label;
      btn.addEventListener('click', function () {
        if (state.category === category.value) {
          return;
        }
        state.category = category.value;

        var url = new URL(window.location.href);
        if (category.value) {
          url.searchParams.set('category', category.value);
        } else {
          url.searchParams.delete('category');
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
