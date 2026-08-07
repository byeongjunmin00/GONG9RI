/**
 * main.js — 메인 페이지(`/`) 전용 스크립트
 *
 * GET /api/products 로 상품 목록을 가져와 카드 그리드로 렌더링한다.
 * - 로딩/빈 목록/에러 상태는 #product-status에 문구로 표시한다.
 * - "더 보기" 클릭 시 다음 page를 요청해 카드 뒤에 이어붙이고(append),
 *   totalElements만큼 다 불러왔으면 버튼을 숨긴다.
 * - 상품명/판매자명 등 사용자(판매자) 입력 문자열은 textContent로만 넣어 XSS를 방지한다.
 */
(function () {
  var PRODUCTS_PATH = '/products';

  var gridEl = document.getElementById('product-grid');
  var statusEl = document.getElementById('product-status');
  var loadMoreBtn = document.getElementById('load-more-btn');

  if (!gridEl || !statusEl || !loadMoreBtn) {
    return;
  }

  var state = {
    page: -1, // 아직 아무 페이지도 불러오지 않음
    loadedCount: 0,
    totalElements: 0,
    loading: false,
  };

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

    link.appendChild(bodyEl);

    return link;
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

    var path = page > 0 ? PRODUCTS_PATH + '?page=' + page : PRODUCTS_PATH;

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

  fetchProducts(0);
})();
