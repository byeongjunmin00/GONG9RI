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

  function isPushCandidate(product) {
    if (product.hidden) return false;
    var highRating = typeof product.ratingAverage === 'number' && product.ratingAverage >= 4.5;
    var activeProgressRatio = 0;
    if (typeof product.activeTeamCurrentCount === 'number' && typeof product.activeTeamTargetParticipants === 'number' && product.activeTeamTargetParticipants > 0) {
      activeProgressRatio = product.activeTeamCurrentCount / product.activeTeamTargetParticipants;
    }
    return highRating || activeProgressRatio >= 0.5;
  }

  function getStatusEmoji(product) {
    if (product.openAt && new Date(product.openAt).getTime() > Date.now()) {
      return '⏱️';
    }
    if (product.hidden) {
      return '⚠️';
    }
    if (isPushCandidate(product)) {
      return '🚀';
    }
    return '📦';
  }


  function createCompactProductThumb(product) {
    var thumbEl = document.createElement('div');
    thumbEl.style.width = '30px';
    thumbEl.style.height = '30px';
    thumbEl.style.borderRadius = 'var(--radius-sm)';
    thumbEl.style.overflow = 'hidden';
    thumbEl.style.flexShrink = '0';
    thumbEl.style.background = 'var(--color-surface-alt)';
    thumbEl.style.display = 'flex';
    thumbEl.style.alignItems = 'center';
    thumbEl.style.justifyContent = 'center';
    thumbEl.style.fontSize = '14px';

    var emoji = getStatusEmoji(product);

    if (product.imageUrl) {
      var img = document.createElement('img');
      img.src = product.imageUrl;
      img.alt = product.name || '상품';
      img.style.width = '100%';
      img.style.height = '100%';
      img.style.objectFit = 'cover';
      img.onerror = function () {
        thumbEl.textContent = emoji;
      };
      thumbEl.appendChild(img);
    } else {
      thumbEl.textContent = emoji;
    }
    return thumbEl;
  }

  function createProductItem(product) {
    var card = document.createElement('div');
    card.className = 'admin-card';
    card.setAttribute('data-product-id', String(product.productId));

    // Row 1: Header (Thumb + Name + Price)
    var row1 = document.createElement('div');
    row1.className = 'admin-card__row1';

    var titleGroup = document.createElement('div');
    titleGroup.className = 'admin-card__title-group';

    var thumbEl = createCompactProductThumb(product);
    titleGroup.appendChild(thumbEl);

    var nameEl = document.createElement('h3');
    nameEl.className = 'admin-card__title';
    nameEl.textContent = product.name || '';
    titleGroup.appendChild(nameEl);
    row1.appendChild(titleGroup);

    var priceEl = document.createElement('span');
    priceEl.style.fontSize = '12px';
    priceEl.style.fontWeight = '700';
    priceEl.style.color = 'var(--color-brand)';
    priceEl.style.flexShrink = '0';
    priceEl.textContent = formatPrice(product.basePrice);
    row1.appendChild(priceEl);
    card.appendChild(row1);

    // Row 2: Meta (Seller + 상태 배지 — 썸네일이 이미지로 가려져도 항상 보이도록)
    var row2 = document.createElement('div');
    row2.className = 'admin-card__row2';
    row2.style.display = 'flex';
    row2.style.alignItems = 'center';
    row2.style.justifyContent = 'space-between';

    var sellerText = document.createElement('span');
    if (product.sellerName) {
      sellerText.appendChild(window.Avatar.create(product.sellerName, product.sellerProfileImageUrl, 'xs'));
      sellerText.classList.add('avatar-name');
    }
    var sellerLabelEl = document.createElement('span');
    sellerLabelEl.textContent = (product.sellerName ? '판매자: ' + product.sellerName : '')
        + (product.category ? ' · ' + product.category : '');
    sellerText.appendChild(sellerLabelEl);
    sellerText.style.whiteSpace = 'nowrap';
    sellerText.style.overflow = 'hidden';
    sellerText.style.textOverflow = 'ellipsis';
    row2.appendChild(sellerText);

    var statusBadgeGroup = document.createElement('div');
    statusBadgeGroup.style.display = 'flex';
    statusBadgeGroup.style.gap = '3px';
    statusBadgeGroup.style.flexShrink = '0';

    var isUpcoming = product.openAt && new Date(product.openAt).getTime() > Date.now();
    var statusBadge = document.createElement('span');
    statusBadge.style.fontSize = '10px';
    statusBadge.style.padding = '2px 4px';
    if (product.hidden) {
      statusBadge.className = 'badge badge-failed';
      statusBadge.textContent = '⚠️ 숨김';
    } else if (isUpcoming) {
      statusBadge.className = 'badge badge-time';
      statusBadge.textContent = '⏱️ 오픈예정';
    } else {
      statusBadge.className = 'badge badge-success';
      statusBadge.textContent = '공개';
    }
    statusBadgeGroup.appendChild(statusBadge);

    if (isPushCandidate(product)) {
      var pushBadge = document.createElement('span');
      pushBadge.className = 'badge badge-brand';
      pushBadge.style.fontSize = '10px';
      pushBadge.style.padding = '2px 4px';
      pushBadge.textContent = '🚀푸시';
      statusBadgeGroup.appendChild(pushBadge);
    }
    row2.appendChild(statusBadgeGroup);
    card.appendChild(row2);

    // Row 3: Stats (Rating & Team Progress)
    var row3 = document.createElement('div');
    row3.className = 'admin-card__row3';
    var statParts = [];
    if (typeof product.ratingAverage === 'number' && product.ratingAverage > 0) {
      statParts.push('평점 ' + product.ratingAverage.toFixed(1) + ' (리뷰 ' + (product.reviewCount || 0) + '건)');
    }
    if (typeof product.activeTeamCurrentCount === 'number' && typeof product.activeTeamTargetParticipants === 'number') {
      statParts.push('공구 참여 ' + product.activeTeamCurrentCount + '/' + product.activeTeamTargetParticipants + '명');
    }
    row3.textContent = statParts.length > 0 ? statParts.join(' · ') : '등록상품';
    card.appendChild(row3);

    // Row 4: Actions
    var row4 = document.createElement('div');
    row4.className = 'admin-card__row4';
    row4.style.justifyContent = 'space-between';

    var statusEmojiEl = document.createElement('span');
    statusEmojiEl.style.fontSize = '14px';
    statusEmojiEl.textContent = getStatusEmoji(product);
    row4.appendChild(statusEmojiEl);

    var actionGroup = document.createElement('div');
    actionGroup.style.display = 'flex';
    actionGroup.style.gap = 'var(--space-2)';

    var viewLink = document.createElement('a');
    viewLink.className = 'btn btn-secondary btn-sm admin-card__btn-xs';
    viewLink.href = '/product.html?id=' + product.productId;
    viewLink.textContent = '상세';
    actionGroup.appendChild(viewLink);

    var hideBtn = document.createElement('button');
    hideBtn.type = 'button';
    hideBtn.className = 'btn btn-secondary btn-sm admin-card__btn-xs';
    hideBtn.textContent = product.hidden ? '해제' : '숨김';
    hideBtn.addEventListener('click', function () {
      handleToggleHidden(product, card, hideBtn);
    });
    actionGroup.appendChild(hideBtn);

    var deleteBtn = document.createElement('button');
    deleteBtn.type = 'button';
    deleteBtn.className = 'btn btn-ghost btn-sm admin-card__btn-xs';
    deleteBtn.textContent = '삭제';
    deleteBtn.addEventListener('click', function () {
      handleDelete(product, card, deleteBtn);
    });
    actionGroup.appendChild(deleteBtn);

    row4.appendChild(actionGroup);
    card.appendChild(row4);
    return card;
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
    if (activeFilter === 'VISIBLE' || activeFilter === 'HIDDEN' || activeFilter === 'PUSH' || activeFilter === 'UPCOMING') {
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
