/**
 * header-search.js — 헤더 전역 검색바 + 실시간 인기 검색어 슬라이드 티커 + 전체 목록 팝업
 *
 * - partials/header.html에 있는 #header-search-form은 모든 페이지에 공통으로 뜨는 검색 진입점이다.
 *   제출하면 `/index.html?keyword=`로 이동한다 — 실제 검색/목록 필터링(js/main.js,
 *   product/list-search)은 건드리지 않고, 그 로직이 이미 페이지 로드 시 `keyword` 쿼리파라미터를
 *   읽어 처리하는 걸 그대로 재사용한다.
 * - 검색바 옆 티커(#header-search-trends-ticker)가 순위 하나씩 자동으로 바뀌며 상시 노출된다
 *   (product/list-enhancements의 main.js 티커와 동일한 페이드 전환 패턴, 자리만 헤더로 옮김).
 *   티커를 클릭하거나 검색창을 포커스/클릭하면 전체 20개 목록 팝업(#header-search-trends-panel)이
 *   열린다 — 데이터는 페이지 로드 시 한 번만 불러와 티커·팝업 둘 다에서 재사용한다(중복 호출 없음).
 * - js/include.js가 헤더를 삽입한 뒤 발행하는 'gong9ri:includes-ready' 이벤트를 구독해서 초기화한다
 *   (js/header-auth.js와 같은 패턴).
 */
(function () {
  document.addEventListener('gong9ri:includes-ready', function () {
    var formEl = document.getElementById('header-search-form');
    var inputEl = document.getElementById('header-search-input');
    var panelEl = document.getElementById('header-search-trends-panel');
    var listEl = document.getElementById('header-search-trends-list');
    var tickerEl = document.getElementById('header-search-trends-ticker');
    var wrapEl = document.querySelector('.site-header__search');

    if (!formEl || !inputEl || !panelEl || !listEl || !tickerEl || !wrapEl || !window.Api) {
      return;
    }

    var trendsLoaded = false;
    var trendKeywords = [];

    function goToSearch(keyword) {
      var url = new URL('/index.html', window.location.origin);
      if (keyword) {
        url.searchParams.set('keyword', keyword);
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
      window.Api.get('/products/search-trends?limit=20')
        .then(function (data) {
          trendKeywords = (data && data.keywords) || [];
          renderTrends(trendKeywords);
          startTicker(trendKeywords);
        })
        .catch(function () {
          trendsLoaded = false;
          renderTrends(null);
        });
    }

    function openPanel() {
      panelEl.hidden = false;
      ensureTrendsLoaded();
    }

    function closePanel() {
      panelEl.hidden = true;
    }

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

    tickerEl.addEventListener('click', openPanel);

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

    function showTickerCurrent(keywords) {
      tickerRankEl.textContent = String(tickerIndex + 1);
      tickerKeywordEl.textContent = keywords[tickerIndex];
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
  });
})();
