/**
 * search-popup.js — 헤더 검색 팝업 패널 열기/닫기 토글 (design-system-sharp-ui, 2단계)
 *
 * js/include.js가 헤더 partial 삽입을 끝낸 뒤 document에 발행하는 'gong9ri:includes-ready'
 * 이벤트를 구독해, 헤더 DOM(검색 버튼/패널)이 준비된 뒤에만 동작한다
 * (js/header-auth.js, js/chat-widget.js와 동일한 패턴).
 *
 * 이 스크립트는 열기/닫기 토글만 담당한다 — 제출 핸들러, 실제 검색 실행, fetch 호출은 없다
 * (백엔드 검색 API가 없어 패널 안에는 검색 입력창 하나만 있다. "최근 검색어" 등 콘텐츠 없음).
 */
(function () {
  var toggleBtn;
  var panelEl;
  var closeBtn;
  var fieldEl;

  function bindElements() {
    toggleBtn = document.getElementById('search-toggle-btn');
    panelEl = document.getElementById('search-popup-panel');
    closeBtn = document.getElementById('search-popup-close');
    fieldEl = document.getElementById('search-popup-field');

    return !!(toggleBtn && panelEl && closeBtn);
  }

  function openPanel() {
    panelEl.hidden = false;
    toggleBtn.setAttribute('aria-expanded', 'true');
    toggleBtn.classList.add('is-active');
    if (fieldEl) {
      fieldEl.focus();
    }
  }

  function closePanel() {
    panelEl.hidden = true;
    toggleBtn.setAttribute('aria-expanded', 'false');
    toggleBtn.classList.remove('is-active');
  }

  function togglePanel() {
    if (panelEl.hidden) {
      openPanel();
    } else {
      closePanel();
    }
  }

  function init() {
    if (!bindElements()) {
      return;
    }

    toggleBtn.addEventListener('click', togglePanel);
    closeBtn.addEventListener('click', closePanel);
  }

  document.addEventListener('gong9ri:includes-ready', init);
})();
