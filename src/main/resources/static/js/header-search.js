/**
 * header-search.js — 헤더 전역 검색바 + 통합 검색 오버레이(최근검색어/카테고리/실시간 인기검색어)
 *
 * - partials/header.html에 있는 #header-search-form은 모든 페이지에 공통으로 뜨는 검색 진입점이다.
 *   제출하면 `/index.html?keyword=`로 이동한다 — 실제 검색/목록 필터링(js/main.js,
 *   product/list-search)은 건드리지 않고, 그 로직이 이미 페이지 로드 시 `keyword` 쿼리파라미터를
 *   읽어 처리하는 걸 그대로 재사용한다.
 * - 검색바 옆 티커(#header-search-trends-ticker)가 순위 하나씩 자동으로 바뀌며 상시 노출된다
 *   (product/list-enhancements의 main.js 티커와 동일한 페이드 전환 패턴, 자리만 헤더로 옮김).
 * - 티커를 클릭하거나 검색창을 포커스/클릭하면 통합 검색 오버레이(#header-search-trends-panel)가
 *   열린다 — 최근 검색어(로그인 무관, 브라우저 localStorage) → 카테고리 바로가기(정적, API 불필요,
 *   main.js의 CATEGORIES와 동일한 라벨을 여기서도 별도로 둔다 — index.html 밖의 모든 페이지에서도
 *   똑같이 떠야 해서 그 스코프에 갇힌 main.js 상수를 재사용할 수 없다) → 실시간 인기 검색어(기존
 *   기능, 데이터는 페이지 로드 시 한 번만 불러와 티커·오버레이 둘 다에서 재사용) 순서.
 * - 검색창 자체의 placeholder는 몇 가지 문구를 순환 노출한다 — 포커스 중엔(입력을 가릴 일 없어도
 *   시선이 분산되지 않도록) 멈춘다.
 * - js/include.js가 헤더를 삽입한 뒤 발행하는 'gong9ri:includes-ready' 이벤트를 구독해서 초기화한다
 *   (js/header-auth.js와 같은 패턴).
 */
(function () {
  document.addEventListener('gong9ri:includes-ready', function () {
    var formEl = document.getElementById('header-search-form');
    var inputEl = document.getElementById('header-search-input');
    var panelEl = document.getElementById('header-search-trends-panel');
    var listEl = document.getElementById('header-search-trends-list');
    var updatedEl = document.getElementById('header-search-trends-updated');
    var tickerEl = document.getElementById('header-search-trends-ticker');
    var wrapEl = document.querySelector('.site-header__search');
    var recentListEl = document.getElementById('header-search-recent-list');
    var recentClearBtn = document.getElementById('header-search-recent-clear');
    var categoryListEl = document.getElementById('header-search-category-list');
    var overlayCloseBtn = document.getElementById('header-search-overlay-close');

    if (
      !formEl || !inputEl || !panelEl || !listEl || !updatedEl || !tickerEl || !wrapEl ||
      !recentListEl || !recentClearBtn || !categoryListEl || !overlayCloseBtn || !window.Api
    ) {
      return;
    }

    var trendsLoaded = false;
    var trendKeywords = [];

    // ---------- 최근 검색어 (localStorage, 로그인 상태와 무관 — 이 브라우저 기준) ----------
    var RECENT_SEARCHES_KEY = 'gong9ri:recentSearches';
    var RECENT_SEARCHES_MAX = 8;

    function loadRecentSearches() {
      try {
        var raw = window.localStorage.getItem(RECENT_SEARCHES_KEY);
        var parsed = raw ? JSON.parse(raw) : [];
        return Array.isArray(parsed) ? parsed.filter(function (v) { return typeof v === 'string' && v; }) : [];
      } catch (e) {
        // localStorage가 막혀있거나(시크릿모드 일부 브라우저) 저장된 값이 깨져있어도 검색 자체엔
        // 지장 없어야 한다 — 최근 검색어 기능만 조용히 빈 목록으로 대체.
        return [];
      }
    }

    function saveRecentSearches(keywords) {
      try {
        window.localStorage.setItem(RECENT_SEARCHES_KEY, JSON.stringify(keywords));
      } catch (e) {
        // 저장 실패해도(용량 초과 등) 검색 자체는 계속 동작해야 하므로 조용히 무시.
      }
    }

    function addRecentSearch(keyword) {
      var trimmed = keyword && keyword.trim();
      if (!trimmed) {
        return;
      }
      var current = loadRecentSearches().filter(function (k) { return k !== trimmed; });
      current.unshift(trimmed);
      saveRecentSearches(current.slice(0, RECENT_SEARCHES_MAX));
    }

    function removeRecentSearch(keyword) {
      saveRecentSearches(loadRecentSearches().filter(function (k) { return k !== keyword; }));
      renderRecentSearches();
    }

    function clearRecentSearches() {
      saveRecentSearches([]);
      renderRecentSearches();
    }

    function renderRecentSearches() {
      var keywords = loadRecentSearches();
      recentListEl.innerHTML = '';
      recentClearBtn.hidden = keywords.length === 0;

      if (!keywords.length) {
        var emptyEl = document.createElement('li');
        emptyEl.className = 'header-search-recent__empty';
        emptyEl.textContent = '최근 검색어가 없습니다.';
        recentListEl.appendChild(emptyEl);
        return;
      }

      keywords.forEach(function (keyword) {
        var itemEl = document.createElement('li');
        itemEl.className = 'header-search-recent__item';

        var btnEl = document.createElement('button');
        btnEl.type = 'button';
        btnEl.className = 'header-search-recent__keyword';
        // keyword는 사용자가 직접 입력해서 이 브라우저에 저장해둔 값이라(XSS 방지 원칙은 "다른
        // 사용자가 입력한 값"에 적용되는 것) 여기선 본인 입력이지만, 원칙을 일관되게 지켜 그대로
        // textContent로만 대입한다.
        btnEl.textContent = keyword;
        btnEl.addEventListener('click', function () {
          goToSearch(keyword);
        });
        itemEl.appendChild(btnEl);

        var removeBtnEl = document.createElement('button');
        removeBtnEl.type = 'button';
        removeBtnEl.className = 'header-search-recent__remove';
        removeBtnEl.setAttribute('aria-label', '"' + keyword + '" 최근 검색어 삭제');
        removeBtnEl.textContent = '×';
        removeBtnEl.addEventListener('click', function (event) {
          event.stopPropagation();
          removeRecentSearch(keyword);
        });
        itemEl.appendChild(removeBtnEl);

        recentListEl.appendChild(itemEl);
      });
    }

    recentClearBtn.addEventListener('click', clearRecentSearches);

    // ---------- 카테고리 바로가기 (정적 — main.js의 CATEGORIES와 동일한 라벨, API 호출 불필요) ----------
    // 이 목록엔 원래 "전체" 항목이 없다(goToCategory가 항상 특정 카테고리 값을 요구하는 구조라서) —
    // "오픈예정"(openSoon: true, product/list-enhancements)도 실제 카테고리 값이 아니라 main.js
    // 카테고리 바처럼 [전체] 다음이 아니라 이 목록의 맨 앞에 둔다(참고 주석 위와 동일한 이유).
    var CATEGORIES = [
      { openSoon: true, label: '오픈예정' },
      { value: 'FOOD', label: '식품' },
      { value: 'LIVING', label: '생활/주방' },
      { value: 'BEAUTY', label: '뷰티' },
      { value: 'FASHION', label: '패션/잡화' },
      { value: 'DIGITAL', label: '디지털/가전' },
      { value: 'ETC', label: '기타' },
    ];

    function goToCategory(categoryValue) {
      var url = new URL('/index.html', window.location.origin);
      url.searchParams.set('category', categoryValue);
      window.location.href = url.pathname + url.search;
    }

    function goToOpenSoon() {
      var url = new URL('/index.html', window.location.origin);
      url.searchParams.set('openSoon', 'true');
      window.location.href = url.pathname + url.search;
    }

    function renderCategories() {
      var fragment = document.createDocumentFragment();
      CATEGORIES.forEach(function (category) {
        var itemEl = document.createElement('li');
        var btnEl = document.createElement('button');
        btnEl.type = 'button';
        btnEl.className = 'category-pill category-pill--sm';
        btnEl.textContent = category.label;
        btnEl.addEventListener('click', function () {
          if (category.openSoon) {
            goToOpenSoon();
          } else {
            goToCategory(category.value);
          }
        });
        itemEl.appendChild(btnEl);
        fragment.appendChild(itemEl);
      });
      categoryListEl.appendChild(fragment);
    }

    renderCategories();

    function goToSearch(keyword) {
      var trimmed = keyword && keyword.trim();
      if (trimmed) {
        addRecentSearch(trimmed);
      }
      var url = new URL('/index.html', window.location.origin);
      if (trimmed) {
        url.searchParams.set('keyword', trimmed);
      }
      window.location.href = url.pathname + url.search;
    }

    function renderTrends(keywords) {
      listEl.innerHTML = '';
      if (!keywords || !keywords.length) {
        var emptyEl = document.createElement('li');
        emptyEl.className = 'header-search-trends__empty';
        emptyEl.textContent = '아직 오늘 검색된 키워드가 없습니다.';
        listEl.appendChild(emptyEl);
        return;
      }
      keywords.forEach(function (keyword, index) {
        var itemEl = document.createElement('li');
        var btnEl = document.createElement('button');
        btnEl.type = 'button';
        btnEl.className = 'header-search-trends__item';

        var rankEl = document.createElement('span');
        rankEl.className = 'header-search-trends__rank';
        rankEl.textContent = String(index + 1);
        btnEl.appendChild(rankEl);

        // keyword는 다른 방문자가 검색창에 직접 입력한 값이라(Redis에 그대로 저장), 반드시
        // textContent로만 넣는다(js/main.js의 실시간 인기 검색어 렌더링과 동일한 원칙, XSS 방지).
        var textEl = document.createElement('span');
        textEl.textContent = keyword;
        btnEl.appendChild(textEl);

        btnEl.addEventListener('click', function () {
          goToSearch(keyword);
        });

        itemEl.appendChild(btnEl);
        listEl.appendChild(itemEl);
      });
    }

    function ensureTrendsLoaded() {
      if (trendsLoaded) {
        return;
      }
      trendsLoaded = true;
      window.Api.get('/products/search-trends?limit=10')
        .then(function (data) {
          trendKeywords = (data && data.keywords) || [];
          renderTrends(trendKeywords);
          startTicker(trendKeywords);
          // 집계 자체는 실시간이지만, 화면에 뜬 순위는 지금 이 응답을 받은 시점의 스냅샷이라
          // 언제 기준인지 명시한다(사용자 피드백 — "이게 몇 시 기준인지 안 나와있다").
          updatedEl.textContent = new Date().toLocaleString('ko-KR') + ' 기준';
        })
        .catch(function () {
          trendsLoaded = false;
          renderTrends(null);
          updatedEl.textContent = '';
        });
    }

    function openPanel() {
      panelEl.hidden = false;
      renderRecentSearches();
      ensureTrendsLoaded();
    }

    function closePanel() {
      panelEl.hidden = true;
    }

    // 티커 전용 — 티커는 오버레이를 여는 트리거이면서 동시에 오버레이 바깥(wrapEl 안쪽)에 있어서,
    // 열린 상태에서 눌러도 바깥 클릭 닫기 핸들러가 무시해버린다. 그래서 열기만 걸어두면 "열려 있을 때
    // 티커를 누르면 아무 일도 안 일어나는" 죽은 영역이 된다(사용자가 실제로 "안 닫힌다"고 리포트,
    // 2026-08-20). 여는 트리거를 다시 누르면 닫히는 게 자연스러워서 토글로 바꾼다.
    function togglePanel() {
      if (panelEl.hidden) {
        openPanel();
      } else {
        closePanel();
      }
    }

    overlayCloseBtn.addEventListener('click', closePanel);

    formEl.addEventListener('submit', function (event) {
      event.preventDefault();
      goToSearch(inputEl.value.trim() || null);
    });

    inputEl.addEventListener('focus', openPanel);
    inputEl.addEventListener('click', openPanel);

    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape') {
        closePanel();
      }
    });

    document.addEventListener('click', function (event) {
      if (!wrapEl.contains(event.target)) {
        closePanel();
      }
    });

    // ---------- 실시간 인기 검색어 슬라이드 티커 ----------
    // 검색창을 포커스/클릭하지 않아도 상시 보이는 축약 노출. main.js에 있던 원래 티커와 같은
    // 페이드 전환 패턴(먼저 fade-out, 안 보이게 된 다음 텍스트 교체, 다시 fade-in)을 그대로 쓴다.
    var TICKER_ROTATE_MS = 2200;
    var TICKER_FADE_MS = 220;
    var tickerTimer = null;
    var tickerFadeTimeout = null;
    var tickerIndex = 0;
    var tickerRankEl = null;
    var tickerKeywordEl = null;

    tickerEl.addEventListener('click', togglePanel);

    function stopTicker() {
      if (tickerTimer) {
        window.clearInterval(tickerTimer);
        tickerTimer = null;
      }
      if (tickerFadeTimeout) {
        window.clearTimeout(tickerFadeTimeout);
        tickerFadeTimeout = null;
      }
    }

    var TICKER_KEYWORD_MAX_LENGTH = 10;

    // 티커 옆 검색창이 밀리지 않도록 슬롯을 고정폭으로 두는 대신, 검색어 자체를 여기서 10자로
    // 잘라 티커가 가질 수 있는 최대 너비를 예측 가능한 좁은 범위로 묶어둔다(사용자 피드백 —
    // 슬롯을 고정폭으로 예약해두지 말 것).
    function truncateKeyword(keyword) {
      if (keyword.length <= TICKER_KEYWORD_MAX_LENGTH) {
        return keyword;
      }
      return keyword.slice(0, TICKER_KEYWORD_MAX_LENGTH) + '...';
    }

    function showTickerCurrent(keywords) {
      tickerRankEl.textContent = String(tickerIndex + 1);
      tickerKeywordEl.textContent = truncateKeyword(keywords[tickerIndex]);
    }

    function startTicker(keywords) {
      stopTicker();
      tickerEl.innerHTML = '';
      if (!keywords || !keywords.length) {
        tickerEl.hidden = true;
        return;
      }

      tickerRankEl = document.createElement('b');
      tickerKeywordEl = document.createElement('span');
      tickerEl.appendChild(tickerRankEl);
      tickerEl.appendChild(tickerKeywordEl);

      tickerIndex = 0;
      var prefersReducedMotion =
        window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

      showTickerCurrent(keywords);
      tickerEl.hidden = false;

      if (keywords.length > 1 && !prefersReducedMotion) {
        tickerTimer = window.setInterval(function () {
          tickerEl.classList.add('is-swapping');
          tickerFadeTimeout = window.setTimeout(function () {
            tickerIndex = (tickerIndex + 1) % keywords.length;
            showTickerCurrent(keywords);
            tickerEl.classList.remove('is-swapping');
            tickerFadeTimeout = null;
          }, TICKER_FADE_MS);
        }, TICKER_ROTATE_MS);
      }
    }

    // 페이지 로드 시 바로 한 번 불러와 티커가 상시 노출되게 한다(팝업은 클릭 전까지 안 열림).
    ensureTrendsLoaded();

    // ---------- 검색창 placeholder 순환 노출 ----------
    // 입력값을 가리는 게 아니라(placeholder는 값이 비어있을 때만 보임) 검색창이 비어있는 동안
    // 뭘 검색할 수 있는지 힌트를 몇 개 순환해서 보여준다. 포커스 중엔(입력하려는 사용자 시선이
    // 분산되지 않도록) 멈추고, 블러되면 다시 돈다.
    var PLACEHOLDER_ROTATE_MS = 3200;
    var PLACEHOLDERS = [
      '상품명이나 판매자 이름으로 검색해보세요',
      '이번 주 마감 임박한 공구를 찾아보세요',
      '요즘 인기 있는 상품은 뭘까요?',
      '관심 있는 카테고리를 둘러보세요',
    ];
    var placeholderIndex = 0;
    var placeholderTimer = null;

    function startPlaceholderRotation() {
      placeholderTimer = window.setInterval(function () {
        placeholderIndex = (placeholderIndex + 1) % PLACEHOLDERS.length;
        inputEl.placeholder = PLACEHOLDERS[placeholderIndex];
      }, PLACEHOLDER_ROTATE_MS);
    }

    startPlaceholderRotation();
    inputEl.addEventListener('focus', function () {
      window.clearInterval(placeholderTimer);
    });
    inputEl.addEventListener('blur', startPlaceholderRotation);
  });
})();
