/**
 * include.js — 공통 헤더/푸터 partial 삽입 유틸
 *
 * 사용법: 페이지 어딘가에 아래처럼 컨테이너를 두면
 *   <div data-include="header"></div>
 *   <div data-include="footer"></div>
 * DOMContentLoaded 시점에 /partials/{name}.html 을 fetch로 가져와 삽입한다.
 *
 * 정적 리소스이므로 반드시 같은 오리진(http://localhost:8080/...)으로 접속해야 fetch가 동작한다
 * (file://로 직접 열면 CORS/파일 프로토콜 제약으로 실패할 수 있다).
 */
(function () {
  var PARTIALS_BASE = '/partials';

  function loadInclude(el) {
    var name = el.getAttribute('data-include');
    if (!name) {
      return Promise.resolve();
    }

    return fetch(PARTIALS_BASE + '/' + name + '.html')
      .then(function (res) {
        if (!res.ok) {
          throw new Error('partial not found: ' + name + ' (status ' + res.status + ')');
        }
        return res.text();
      })
      .then(function (html) {
        el.innerHTML = html;
      })
      .catch(function (err) {
        console.error('[include.js] failed to load partial "' + name + '":', err);
      });
  }

  function includeAll() {
    var targets = document.querySelectorAll('[data-include]');
    return Promise.all(Array.prototype.map.call(targets, loadInclude));
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', includeAll);
  } else {
    includeAll();
  }

  window.Gong9riInclude = {
    includeAll: includeAll,
  };
})();
