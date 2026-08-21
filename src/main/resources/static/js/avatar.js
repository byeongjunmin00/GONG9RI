/**
 * 회원 아바타 공통 렌더러 (member/profile-image 노출, 2026-08-21).
 *
 * 이름이 뜨는 자리가 화면 곳곳(상품 카드·상세, 리뷰, 문의, 상담, 관리자, 마이페이지)에 흩어져 있는데,
 * 각자 <img> 태그를 만들면 "사진 없는 회원 처리"와 "이미지 로드 실패 처리"가 자리마다 달라진다.
 * 그래서 만드는 곳을 여기 하나로 모은다.
 *
 * - 사진이 있으면 그 이미지를, 없으면 **이름 첫 글자 동그라미**를 그린다.
 * - 첫 글자 배경색은 이름을 해싱해 고정 팔레트에서 고른다 — 같은 사람은 화면이 달라도 늘 같은 색이라
 *   목록에서 사람을 구분하는 신호가 된다(랜덤이면 새로고침마다 바뀌어 오히려 방해된다).
 * - 이미지 URL이 깨진 경우(파일이 지워졌거나 볼륨이 비어 있는 경우) onerror로 첫 글자 동그라미로
 *   되돌린다 — 깨진 이미지 아이콘이 뜨는 것보다 낫다.
 */
(function () {
  'use strict';

  // 웜톤 기본 팔레트와 부딪히지 않으면서 서로 구분되는 색들. 채도를 낮게 잡아 이름이 잘 읽히게 한다.
  var PALETTE = [
    '#E8846B', '#C97BA8', '#7B93C9', '#5FA8A0',
    '#D19B4F', '#9B86C4', '#69A56E', '#CC7A7A'
  ];

  function pickColor(name) {
    var key = name || '';
    var hash = 0;
    for (var i = 0; i < key.length; i += 1) {
      hash = (hash * 31 + key.charCodeAt(i)) % 100000;
    }
    return PALETTE[hash % PALETTE.length];
  }

  /** 표시할 첫 글자. 이름이 비어 있으면 물음표(익명/탈퇴 회원). */
  function initialOf(name) {
    var trimmed = (name || '').trim();
    if (!trimmed) return '?';
    return trimmed.charAt(0).toUpperCase();
  }

  function renderFallback(el, name) {
    el.textContent = initialOf(name);
    el.style.background = pickColor(name);
    el.style.color = '#fff';
  }

  /**
   * 아바타 엘리먼트를 만들어 돌려준다.
   *
   * @param {string} name 회원 이름 (없으면 물음표)
   * @param {string} imageUrl 프로필 사진 URL (없으면 첫 글자 동그라미)
   * @param {string} size 'xs'(18px) | 'sm'(24px) | 'md'(32px) | 'lg'(44px), 기본 'sm'
   */
  function create(name, imageUrl, size) {
    var el = document.createElement('span');
    el.className = 'avatar avatar--' + (size || 'sm');
    el.setAttribute('aria-hidden', 'true');

    if (imageUrl) {
      var img = document.createElement('img');
      img.src = imageUrl;
      img.alt = '';
      img.loading = 'lazy';
      img.addEventListener('error', function () {
        // 사진이 사라진 경우 — 깨진 아이콘 대신 첫 글자로 되돌린다.
        if (img.parentNode === el) el.removeChild(img);
        renderFallback(el, name);
      });
      el.appendChild(img);
    } else {
      renderFallback(el, name);
    }
    return el;
  }

  /**
   * 아바타 + 이름을 한 줄로 묶어 돌려준다. 이름 자리에 그대로 갈아끼울 수 있는 형태라
   * 호출하는 쪽이 정렬·간격을 신경 쓰지 않아도 된다.
   */
  function withName(name, imageUrl, size, nameClassName) {
    var wrap = document.createElement('span');
    wrap.className = 'avatar-name';
    wrap.appendChild(create(name, imageUrl, size));

    var nameEl = document.createElement('span');
    if (nameClassName) nameEl.className = nameClassName;
    nameEl.textContent = name || '알 수 없음';
    wrap.appendChild(nameEl);
    return wrap;
  }

  /**
   * 이미 크기·모양이 잡혀 있는 컨테이너(마이페이지 프로필 동그라미 등)의 내용을 아바타로 채운다.
   * 새 동그라미를 안에 겹쳐 넣으면 이중 원이 되므로, 그런 자리에서는 create() 대신 이걸 쓴다.
   */
  function fill(el, name, imageUrl) {
    if (!el) return;
    while (el.firstChild) el.removeChild(el.firstChild);
    if (imageUrl) {
      var img = document.createElement('img');
      img.src = imageUrl;
      img.alt = '';
      img.style.width = '100%';
      img.style.height = '100%';
      img.style.objectFit = 'cover';
      img.addEventListener('error', function () {
        if (img.parentNode === el) el.removeChild(img);
        el.textContent = initialOf(name);
      });
      el.appendChild(img);
      el.style.overflow = 'hidden';
    } else {
      // 컨테이너가 이미 배경색(브랜드 그라디언트)을 갖고 있으므로 글자만 채운다.
      el.textContent = initialOf(name);
      el.style.fontWeight = '700';
    }
  }

  window.Avatar = { create: create, withName: withName, fill: fill };
})();
