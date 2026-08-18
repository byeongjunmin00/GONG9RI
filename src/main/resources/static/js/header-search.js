/**
 * header-search.js — 헤더 전역 검색바 + 실시간 인기 검색어 팝업
 *
 * - partials/header.html에 있는 #header-search-form은 모든 페이지에 공통으로 뜨는 검색 진입점이다.
 *   제출하면 `/index.html?keyword=`로 이동한다 — 실제 검색/목록 필터링(js/main.js,
 *   product/list-search)은 건드리지 않고, 그 로직이 이미 페이지 로드 시 `keyword` 쿼리파라미터를
 *   읽어 처리하는 걸 그대로 재사용한다.
 * - 입력창을 포커스/클릭하면 `GET /api/products/search-trends?limit=20`로 실시간 인기 검색어를
 *   불러와 팝업 목록으로 보여준다(product/search-trends, Redis 집계). 항목을 클릭하면 그 키워드로
 *   바로 이동한다.
 * - js/include.js가 헤더를 삽입한 뒤 발행하는 'gong9ri:includes-ready' 이벤트를 구독해서 초기화한다
 *   (js/header-auth.js와 같은 패턴).
 */
(function () {
  document.addEventListener('gong9ri:includes-ready', function () {
    var formEl = document.getElementById('header-search-form');
    var inputEl = document.getElementById('header-search-input');
    var panelEl = document.getElementById('header-search-trends-panel');
    var listEl = document.getElementById('header-search-trends-list');
    var wrapEl = document.querySelector('.site-header__search');

    if (!formEl || !inputEl || !panelEl || !listEl || !wrapEl || !window.Api) {
      return;
    }

    var trendsLoaded = false;

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

    function openPanel() {
      panelEl.hidden = false;
      if (trendsLoaded) {
        return;
      }
      trendsLoaded = true;
      window.Api.get('/products/search-trends?limit=20')
        .then(function (data) {
          renderTrends(data && data.keywords);
        })
        .catch(function () {
          trendsLoaded = false;
          renderTrends(null);
        });
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
  });
})();
