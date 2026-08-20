/**
 * admin-products.js — 상품 현황(admin/products.html) 전용 스크립트
 *
 * 목록은 GET /api/admin/products를 쓴다 — 공개 목록(GET /api/products)은 숨김 상품을 빼기 때문에,
 * 그걸 쓰면 숨긴 상품을 되돌릴 방법이 없어진다(2026-08-21).
 *
 * 행마다 액션이 셋이다.
 * - **숨김/숨김 해제**: PATCH /api/admin/products/{id}/hidden?hidden=true|false.
 *   되돌릴 수 있는 정리 — 결제·리뷰가 붙어 삭제할 수 없는 상품을 목록에서 치울 때.
 * - **삭제**: DELETE /api/admin/products/{id}. 활동(결제·공구팀·리뷰)이 있으면 409로 거절된다.
 * - **강제 삭제**: 위 409가 났을 때만 노출. DELETE ...?force=true로 결제·리뷰까지 함께 지운다.
 *   되돌릴 수 없어서 확인을 한 번 더 받는다(장난성 게시물 정리용).
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
    metaEl.textContent = buildMeta(product);
    infoEl.appendChild(metaEl);

    li.appendChild(infoEl);

    var actionsEl = document.createElement('div');
    actionsEl.className = 'mypage-list-item__actions';
    var viewLink = document.createElement('a');
    viewLink.className = 'btn btn-secondary btn-sm';
    viewLink.href = '/product.html?id=' + product.productId;
    viewLink.textContent = '상세보기';
    actionsEl.appendChild(viewLink);

    var hideBtn = document.createElement('button');
    hideBtn.type = 'button';
    hideBtn.className = 'btn btn-secondary btn-sm';
    hideBtn.textContent = product.hidden ? '숨김 해제' : '숨기기';
    hideBtn.addEventListener('click', function () {
      handleToggleHidden(product, li, hideBtn, metaEl);
    });
    actionsEl.appendChild(hideBtn);

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

  function buildMeta(product) {
    return [
      product.sellerName || '',
      product.category,
      formatPrice(product.basePrice),
      product.openAt ? '오픈예정' : '',
      product.hidden ? '숨김' : '',
    ].filter(Boolean).join(' · ');
  }

  function handleToggleHidden(product, li, btn, metaEl) {
    var next = !product.hidden;
    btn.disabled = true;
    window.Api.patch('/admin/products/' + product.productId + '/hidden?hidden=' + next)
      .then(function () {
        product.hidden = next;
        btn.textContent = next ? '숨김 해제' : '숨기기';
        metaEl.textContent = buildMeta(product);
      })
      .catch(function (err) {
        console.error('[admin-products.js] toggle hidden failed:', err);
        window.alert((err && err.message) || '처리에 실패했습니다.');
      })
      .then(function () {
        btn.disabled = false;
      });
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
        // 활동(결제·공구팀·리뷰)이 있어 거절된 경우에만 강제 삭제를 제안한다. 그 외 실패는 그대로 알린다.
        if (err && err.code === 'PRODUCT_HAS_ACTIVITY') {
          promptForceDelete(product, li, btn);
          return;
        }
        window.alert((err && err.message) || '삭제에 실패했습니다.');
        btn.disabled = false;
      });
  }

  function promptForceDelete(product, li, btn) {
    var forced = window.confirm(
      '"' + (product.name || '') + '" 상품에는 결제·공구팀·리뷰가 있습니다.\n\n' +
      '[확인]을 누르면 그 기록까지 전부 삭제합니다. 되돌릴 수 없습니다.\n' +
      '기록을 남겨둔 채 목록에서만 치우려면 [취소] 후 "숨기기"를 쓰세요.');
    if (!forced) {
      btn.disabled = false;
      return;
    }
    window.Api.del('/admin/products/' + product.productId + '?force=true')
      .then(function () {
        li.remove();
        state.totalElements -= 1;
        state.loadedCount -= 1;
        updateLoadMoreButton();
      })
      .catch(function (err) {
        console.error('[admin-products.js] force delete failed:', err);
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

    window.Api.get('/admin/products?page=' + page + '&size=' + PAGE_SIZE)
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
