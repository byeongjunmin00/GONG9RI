/**
 * admin-products.js — 상품 현황(admin/products.html) 전용 스크립트
 *
 * GET /api/admin/products?page=0&size=20&search=...&status=...
 * - 서버 사이드 동적 페이징 및 DB 키워드 검색/상태(공개/숨김/추천푸시) 필터링 연동
 * - 대표 썸네일 이미지 및 리뷰 평점, 활성 공구팀 진행률 렌더링
 * - 관리자 푸시/제재 판별 인사이트 배지 (🚀 추천/인기 푸시 vs ⚠️ 숨김/제재) 노출
 * - 숨김/숨김해제 (PATCH), 삭제 (DELETE), 강제 삭제 (DELETE ?force=true) 액션
 */
(function () {
  var pageAlertEl = document.getElementById('page-alert');
  var pageAlertTextEl = document.getElementById('page-alert-text');
  var statusEl = document.getElementById('products-status');
  var listEl = document.getElementById('products-list');
  var loadMoreBtn = document.getElementById('load-more-btn');
  var searchInputEl = document.getElementById('product-search-input');
  var filterBtns = document.querySelectorAll('.product-filter-btn');

  if (!pageAlertEl || !pageAlertTextEl || !statusEl || !listEl || !loadMoreBtn || !window.AdminGuard) {
    return;
  }

  var PAGE_SIZE = 20;
  var state = { page: -1, loadedCount: 0, totalElements: 0, loading: false };
  var activeFilter = 'ALL';
  var searchQuery = '';
  var searchDebounceTimer = null;

  function showError(text) {
    pageAlertEl.hidden = false;
    pageAlertTextEl.textContent = text;
  }

  function formatPrice(value) {
    return typeof value === 'number' ? value.toLocaleString('ko-KR') + '원' : '';
  }

  function createThumbnailElement(imageUrl, altText) {
    var thumbEl = document.createElement('div');
    thumbEl.className = 'mypage-list-item__thumb';

    if (imageUrl) {
      var img = document.createElement('img');
      img.src = imageUrl;
      img.alt = altText || '상품 이미지';
      img.onerror = function () {
        thumbEl.innerHTML = '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><circle cx="8.5" cy="8.5" r="1.5"></circle><polyline points="21 15 16 10 5 21"></polyline></svg>';
      };
      thumbEl.appendChild(img);
    } else {
      thumbEl.innerHTML = '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><circle cx="8.5" cy="8.5" r="1.5"></circle><polyline points="21 15 16 10 5 21"></polyline></svg>';
    }
    return thumbEl;
  }

  function isPushCandidate(product) {
    if (product.hidden) return false;
    var highRating = typeof product.ratingAverage === 'number' && product.ratingAverage >= 4.5;
    var activeProgressRatio = 0;
    if (typeof product.activeTeamCurrentCount === 'number' && typeof product.activeTeamTargetParticipants === 'number' && product.activeTeamTargetParticipants > 0) {
      activeProgressRatio = product.activeTeamCurrentCount / product.activeTeamTargetParticipants;
    }
    return highRating || activeProgressRatio >= 0.5;
  }

  function createProductItem(product) {
    var li = document.createElement('li');
    li.className = 'mypage-list-item';
    li.setAttribute('data-product-id', String(product.productId));

    var mainEl = document.createElement('div');
    mainEl.className = 'mypage-list-item__main';

    var thumbEl = createThumbnailElement(product.imageUrl, product.name);
    mainEl.appendChild(thumbEl);

    var infoEl = document.createElement('div');
    infoEl.className = 'mypage-list-item__info';

    var titleEl = document.createElement('span');
    titleEl.className = 'mypage-list-item__title';
    titleEl.textContent = product.name || '';
    infoEl.appendChild(titleEl);

    var metaEl = document.createElement('span');
    metaEl.className = 'mypage-list-item__meta';
    var metaParts = [
      product.sellerName ? '판매자: ' + product.sellerName : '',
      product.category || '',
      formatPrice(product.basePrice)
    ].filter(Boolean);
    metaEl.textContent = metaParts.join(' · ');
    infoEl.appendChild(metaEl);

    var badgeGroupEl = document.createElement('div');
    badgeGroupEl.style.display = 'flex';
    badgeGroupEl.style.gap = 'var(--space-2)';
    badgeGroupEl.style.flexWrap = 'wrap';
    badgeGroupEl.style.marginTop = 'var(--space-2)';

    if (product.hidden) {
      var hiddenBadge = document.createElement('span');
      hiddenBadge.className = 'badge badge-failed';
      hiddenBadge.textContent = '⚠️ 숨김 (제재됨)';
      badgeGroupEl.appendChild(hiddenBadge);
    } else {
      var visibleBadge = document.createElement('span');
      visibleBadge.className = 'badge badge-success';
      visibleBadge.textContent = '정상 공개';
      badgeGroupEl.appendChild(visibleBadge);
    }

    if (isPushCandidate(product)) {
      var pushBadge = document.createElement('span');
      pushBadge.className = 'badge badge-brand';
      pushBadge.textContent = '🚀 추천/인기 푸시 대상';
      badgeGroupEl.appendChild(pushBadge);
    }

    if (product.openAt && new Date(product.openAt).getTime() > Date.now()) {
      var openAtBadge = document.createElement('span');
      openAtBadge.className = 'badge badge-time';
      openAtBadge.textContent = '⏱️ 오픈예정';
      badgeGroupEl.appendChild(openAtBadge);
    }

    if (typeof product.ratingAverage === 'number' && product.ratingAverage > 0) {
      var ratingBadge = document.createElement('span');
      ratingBadge.className = 'badge';
      ratingBadge.style.background = 'var(--color-surface-alt)';
      ratingBadge.style.color = 'var(--color-text)';
      ratingBadge.textContent = '⭐ ' + product.ratingAverage.toFixed(1) + ' (' + (product.reviewCount || 0) + '개)';
      badgeGroupEl.appendChild(ratingBadge);
    }

    if (typeof product.activeTeamCurrentCount === 'number' && typeof product.activeTeamTargetParticipants === 'number') {
      var teamProgressBadge = document.createElement('span');
      teamProgressBadge.className = 'badge';
      teamProgressBadge.style.background = 'var(--color-surface-alt)';
      teamProgressBadge.style.color = 'var(--color-brand)';
      teamProgressBadge.textContent = '👥 활성팀 ' + product.activeTeamCurrentCount + '/' + product.activeTeamTargetParticipants + '명';
      badgeGroupEl.appendChild(teamProgressBadge);
    }

    infoEl.appendChild(badgeGroupEl);
    mainEl.appendChild(infoEl);
    li.appendChild(mainEl);

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
      handleToggleHidden(product, li, hideBtn);
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

  function clearChildren(parent) {
    while (parent.firstChild) {
      parent.removeChild(parent.firstChild);
    }
  }

  function handleToggleHidden(product, li, btn) {
    var next = !product.hidden;
    btn.disabled = true;
    window.Api.patch('/admin/products/' + product.productId + '/hidden?hidden=' + next)
      .then(function () {
        product.hidden = next;
        var newLi = createProductItem(product);
        li.replaceWith(newLi);
      })
      .catch(function (err) {
        console.error('[admin-products.js] toggle hidden failed:', err);
        window.alert((err && err.message) || '처리에 실패했습니다.');
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

  function fetchProducts(page, reset) {
    if (state.loading) {
      return;
    }
    state.loading = true;

    if (reset) {
      clearChildren(listEl);
      state.page = -1;
      state.loadedCount = 0;
      state.totalElements = 0;
    }

    if (page === 0) {
      statusEl.hidden = false;
      statusEl.textContent = '상품 목록을 불러오는 중입니다...';
    } else {
      loadMoreBtn.disabled = true;
      loadMoreBtn.textContent = '불러오는 중...';
    }

    // 서버 사이드 페이징/검색/필터 쿼리 스트링 구성
    var queryParams = ['page=' + page, 'size=' + PAGE_SIZE];
    if (searchQuery) {
      queryParams.push('search=' + encodeURIComponent(searchQuery));
    }
    if (activeFilter === 'VISIBLE' || activeFilter === 'HIDDEN' || activeFilter === 'PUSH') {
      queryParams.push('status=' + activeFilter);
    }

    var path = '/admin/products?' + queryParams.join('&');

    window.Api.get(path)
      .then(function (data) {
        state.page = page;
        state.totalElements = data.totalElements;
        state.loadedCount += (data.content || []).length;

        var fragment = document.createDocumentFragment();
        (data.content || []).forEach(function (product) {
          fragment.appendChild(createProductItem(product));
        });
        listEl.appendChild(fragment);

        statusEl.hidden = true;
        if (state.loadedCount === 0) {
          statusEl.hidden = false;
          statusEl.textContent = '조건에 해당하는 상품이 없습니다.';
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

  // 필터 탭 클릭 이벤트 (서버 사이드 재조회)
  filterBtns.forEach(function (btn) {
    btn.addEventListener('click', function () {
      filterBtns.forEach(function (b) {
        b.classList.remove('active', 'btn-primary');
        b.classList.add('btn-secondary');
      });
      btn.classList.add('active', 'btn-primary');
      btn.classList.remove('btn-secondary');
      activeFilter = btn.getAttribute('data-filter') || 'ALL';
      fetchProducts(0, true);
    });
  });

  // 검색창 디바운스 입력 이벤트 (서버 사이드 재조회)
  if (searchInputEl) {
    searchInputEl.addEventListener('input', function () {
      if (searchDebounceTimer) {
        clearTimeout(searchDebounceTimer);
      }
      searchDebounceTimer = setTimeout(function () {
        searchQuery = searchInputEl.value.trim();
        fetchProducts(0, true);
      }, 300);
    });
  }

  loadMoreBtn.addEventListener('click', function () {
    fetchProducts(state.page + 1, false);
  });

  window.AdminGuard.requireAdmin().then(function (member) {
    if (!member) {
      return;
    }
    fetchProducts(0, true);
  });
})();
