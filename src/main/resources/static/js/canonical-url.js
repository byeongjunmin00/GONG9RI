/**
 * canonical-url.js — canonical·og:url·og:image를 현재 접속 주소로 채운다.
 *
 * 정적 HTML이라 서버 설정값(app.base-url)을 넣을 수 없어서, 접속한 주소(location.origin)를 그대로 쓴다.
 * 하드코딩해두면 호스팅을 옮겼을 때 **화면에는 아무 증상도 없이** 검색엔진과 공유 카드만 옛 주소를
 * 가리키게 된다 — 눈에 안 보이는 종류의 고장이라 특히 놓치기 쉽다(2026-08-21).
 *
 * 크롤러는 자바스크립트를 실행하지 않는 경우도 있어 완전한 해법은 아니다. 다만 하드코딩된 옛 주소보다는
 * 낫다("틀린 주소"보다 "상대 경로"가 안전하다) — 정석은 서버가 HTML을 렌더링하며 넣는 것이고, 그건
 * 정적 파일 구조를 바꿔야 해서 스코프 밖으로 둔다.
 */
(function () {
  var origin = window.location.origin;

  var canonical = document.getElementById('canonical-url');
  if (canonical) {
    canonical.setAttribute('href', origin + '/');
  }

  var ogUrl = document.getElementById('og-url');
  if (ogUrl) {
    ogUrl.setAttribute('content', origin + '/');
  }

  var ogImage = document.getElementById('og-image');
  if (ogImage) {
    ogImage.setAttribute('content', origin + '/images/logo-icon.png');
  }
})();
