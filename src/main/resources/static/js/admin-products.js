/**
 * admin-products.js — 상품 현황(admin/products.html) 전용 스크립트
 *
 * 목록은 새 백엔드를 만들지 않고 기존 공개 GET /api/products를 그대로 호출한다(이미 전체 상품을
 * 페이지네이션 조회할 수 있어서 관리자용으로 새로 만들 이유가 없음, docs/dev/admin/design.md 참고).
 *
 * 삭제는 관리자 전용이라 별도 경로다: window.confirm 확인 후 DELETE /api/admin/products/{id}.
 * 성공(204)하면 목록에서 그 행만 제거하고, 409(PRODUCT_HAS_ACTIVITY — 결제·공구팀·리뷰가 있는 상품)면
 * 그 사유를 alert로 보여준다(회원 삭제의 MEMBER_HAS_ACTIVITY와 같은 정책, 2026-08-21 사용자 요청).
 */
(function () {
  var pageAlertEl = document.getElementById('page-alert');
  var pageAlertTextEl = document.getElementById('page-alert-text');
  var statusEl = document.getElementById('products-status');
  var listEl = document.getElementById('products-list');
  var loadMoreBtn = document.getElementById('load-more-btn');

  if (!pageAlertEl || !pageAlertTextEl || !statusEl || !listEl || !loadMoreBtn || !window.AdminGuard) {
    return;
  }

  var PAGE_SIZE = 20;
  var state = { page: -1, loadedCount: 0, totalElements: 0, loading: false };

  function showError(text) {
    pageAlertEl.hidden = false;
    pageAlertTextEl.textContent = text;
  }

  function formatPrice(value) {
    return typeof value === 'number' ? value.toLocaleString('ko-KR') + '원' : '';
  }

  function createProductItem(product) {
    var li = document.createElement('li');
    li.className = 'mypage-list-item';

    var infoEl = document.createElement('div');
    infoEl.className = 'mypage-list-item__info';

    var titleEl = document.createElement('span');
    titleEl.className = 'mypage-list-item__title';
    titleEl.textContent = product.name || '';
    infoEl.appendChild(titleEl);

    var metaEl = document.createElement('span');
    metaEl.className = 'mypage-list-item__meta';
    metaEl.textContent =
      (product.sellerName || '') + ' · ' + product.category + ' · ' + formatPrice(product.basePrice) +
      (product.openAt ? ' · 오픈예정' : '');
    infoEl.appendChild(metaEl);

    li.appendChild(infoEl);

    var actionsEl = document.createElement('div');
    actionsEl.className = 'mypage-list-item__actions';
    var viewLink = document.createElement('a');
    viewLink.className = 'btn btn-secondary btn-sm';
    viewLink.href = '/product.html?id=' + product.productId;
    viewLink.textContent = '상세보기';
    actionsEl.appendChild(viewLink);

    var deleteBtn = document.createElement('button');
    deleteBtn.type = 'button';
    deleteBtn.className = 'btn btn-ghost btn-sm';
    deleteBtn.textContent = '삭제';
    deleteBtn.addEventListener('click', function () {
      handleDelete(product, li, deleteBtn);
    });
    actionsEl.appendChild(deleteBtn);

    li.appendChild(actionsEl);

    return li;
  }

  function handleDelete(product, li, btn) {
    var confirmed = window.confirm('정말 "' + (product.name || '') + '" 상품을 삭제하시겠습니까? 되돌릴 수 없습니다.');
    if (!confirmed) {
      return;
    }
    btn.disabled = true;
    window.Api.del('/admin/products/' + product.productId)
      .then(function () {
        li.remove();
        state.totalElements -= 1;
        state.loadedCount -= 1;
        updateLoadMoreButton();
      })
      .catch(function (err) {
        console.error('[admin-products.js] delete failed:', err);
        window.alert((err && err.message) || '삭제에 실패했습니다.');
        btn.disabled = false;
      });
  }

  function updateLoadMoreButton() {
    if (state.loadedCount >= state.totalElements) {
      loadMoreBtn.hidden = true;
      return;
    }
    loadMoreBtn.hidden = false;
    loadMoreBtn.disabled = false;
    loadMoreBtn.textContent = '더 보기';
  }

  function fetchProducts(page) {
    if (state.loading) {
      return;
    }
    state.loading = true;
    if (page === 0) {
      statusEl.hidden = false;
      statusEl.textContent = '불러오는 중...';
    } else {
      loadMoreBtn.disabled = true;
      loadMoreBtn.textContent = '불러오는 중...';
    }

    window.Api.get('/products?page=' + page + '&size=' + PAGE_SIZE)
      .then(function (data) {
        state.page = page;
        state.totalElements = data.totalElements;
        state.loadedCount += data.content.length;

        var fragment = document.createDocumentFragment();
        data.content.forEach(function (product) {
          fragment.appendChild(createProductItem(product));
        });
        listEl.appendChild(fragment);

        statusEl.hidden = true;
        if (state.loadedCount === 0) {
          statusEl.hidden = false;
          statusEl.textContent = '상품이 없습니다.';
        }
        updateLoadMoreButton();
      })
      .catch(function (err) {
        console.error('[admin-products.js] failed to load products:', err);
        var message = (err && err.message) || '상품 목록을 불러오지 못했습니다.';
        showError(message);
        statusEl.hidden = true;
      })
      .then(function () {
        state.loading = false;
      });
  }

  loadMoreBtn.addEventListener('click', function () {
    fetchProducts(state.page + 1);
  });

  window.AdminGuard.requireAdmin().then(function (member) {
    if (!member) {
      return;
    }
    fetchProducts(0);
  });
})();
