/**
 * header-notifications.js — 헤더 알림 벨(구매자/판매자 공용)
 *
 * - 백엔드에 GET/POST /api/{buyer,seller}/mypage/notifications*가 이미 있었는데(환불 완료 등 이벤트마다
 *   실제로 알림이 쌓이고 있었음) 이걸 보여줄 화면이 아예 없었다 — 그 갭을 채운다.
 * - 로그인 여부·역할은 js/header-auth.js가 발행하는 'gong9ri:auth-resolved' 이벤트로 받는다(다시
 *   /auth/me를 호출하지 않음, js/chat-widget.js와 같은 패턴). BUYER/SELLER만 노출 — 관리자는 이
 *   알림 시스템 자체의 대상이 아니다(백엔드에도 관리자용 엔드포인트가 없음).
 * - 벨 뱃지(안 읽은 개수)는 페이지 로드 시 바로 한 번 불러와서 채운다(검색창 옆 인기검색어 티커와
 *   같은 "클릭 전에도 상시 보이는" 원칙). 패널을 열 때마다 다시 불러오지 않고, 이미 불러온 목록을
 *   로컬에서 갱신(읽음 처리 시 그 항목만/전체만 isRead=true로 바꾸고 다시 그림)해서 불필요한 재호출을
 *   피한다 — js/header-search.js가 트렌드 데이터를 한 번만 불러와 재사용하는 것과 같은 원칙.
 */
(function () {
  document.addEventListener('gong9ri:includes-ready', function () {
    var wrapEl = document.getElementById('header-notifications');
    var bellBtn = document.getElementById('header-notifications-bell');
    var badgeEl = document.getElementById('header-notifications-badge');
    var panelEl = document.getElementById('header-notifications-panel');
    var listEl = document.getElementById('header-notifications-list');
    var markAllBtn = document.getElementById('header-notifications-mark-all');

    if (!wrapEl || !bellBtn || !badgeEl || !panelEl || !listEl || !markAllBtn || !window.Api) {
      return;
    }

    var basePath = null; // '/buyer/mypage' | '/seller/mypage' — 로그인한 역할에 따라 정해짐
    var notifications = [];

    function updateBadge() {
      var unreadCount = notifications.filter(function (n) { return !n.isRead; }).length;
      if (unreadCount > 0) {
        badgeEl.textContent = unreadCount > 9 ? '9+' : String(unreadCount);
        badgeEl.hidden = false;
      } else {
        badgeEl.hidden = true;
      }
      markAllBtn.hidden = unreadCount === 0;
    }

    function renderList() {
      listEl.innerHTML = '';

      if (!notifications.length) {
        var emptyEl = document.createElement('li');
        emptyEl.className = 'header-notifications-panel__empty';
        emptyEl.textContent = '알림이 없습니다.';
        listEl.appendChild(emptyEl);
        return;
      }

      notifications.forEach(function (notification) {
        var itemEl = document.createElement('li');
        itemEl.className = 'header-notifications-panel__item' + (notification.isRead ? '' : ' is-unread');

        var btnEl = document.createElement('button');
        btnEl.type = 'button';
        btnEl.className = 'header-notifications-panel__item-btn';

        // message는 서버가 고정 문구로 만들어 내려주는 값이지만(사용자 입력 아님), 다른 곳과 동일하게
        // textContent로만 대입하는 원칙을 그대로 지킨다.
        var messageEl = document.createElement('span');
        messageEl.className = 'header-notifications-panel__message';
        messageEl.textContent = notification.message;
        btnEl.appendChild(messageEl);

        var timeEl = document.createElement('span');
        timeEl.className = 'header-notifications-panel__time';
        timeEl.textContent = new Date(notification.createdAt).toLocaleString('ko-KR');
        btnEl.appendChild(timeEl);

        // 알림 종류가 8종으로 늘면서(2026-08-20) 각 알림이 "어디로 가야 하는지"(linkUrl)를 갖는다.
        // 이전엔 안 읽은 알림에만 클릭 핸들러를 달았는데, 이제는 이미 읽은 알림도 눌러서 이동할 수
        // 있어야 하므로 항상 단다. linkUrl이 없는 알림(이 필드가 생기기 전에 만들어진 기존 알림
        // 포함)은 읽음 처리만 하고 이동하지 않는다.
        var targetUrl = safeInternalPath(notification.linkUrl);
        btnEl.addEventListener('click', function () {
          if (notification.isRead) {
            navigateIfPossible(targetUrl);
            return;
          }
          // 읽음 처리 요청이 페이지 이동으로 취소되지 않도록, 응답을 받은 뒤에 이동한다.
          // 읽음 처리가 실패하더라도 이동 자체는 막지 않는다(사용자 입장에선 클릭이 먹통이면 더 나쁘다).
          markAsRead(notification.notificationId).then(function () {
            navigateIfPossible(targetUrl);
          });
        });

        itemEl.appendChild(btnEl);
        listEl.appendChild(itemEl);
      });
    }

    function loadNotifications() {
      return window.Api.get(basePath + '/notifications')
        .then(function (data) {
          notifications = data || [];
          renderList();
          updateBadge();
        })
        .catch(function (err) {
          console.error('[header-notifications.js] 알림 목록 조회 실패:', err);
        });
    }

    /**
     * 서버가 내려준 linkUrl을 그대로 믿고 이동하지 않고, "우리 사이트 내부 경로"인 것만 통과시킨다.
     * 지금은 서버가 고정 문자열로 만들어 내리므로 위험하지 않지만, 이 값이 언제든 데이터로 바뀔 수
     * 있는 자리라 열린 리다이렉트(//evil.com 같은 프로토콜 상대 URL 포함)를 구조적으로 막아둔다.
     */
    function safeInternalPath(linkUrl) {
      if (typeof linkUrl !== 'string' || linkUrl.charAt(0) !== '/' || linkUrl.charAt(1) === '/') {
        return null;
      }
      return linkUrl;
    }

    function navigateIfPossible(targetUrl) {
      if (targetUrl) {
        window.location.href = targetUrl;
      }
    }

    function markAsRead(notificationId) {
      return window.Api.post(basePath + '/notifications/' + notificationId + '/read')
        .then(function () {
          var target = notifications.find(function (n) { return n.notificationId === notificationId; });
          if (target) {
            target.isRead = true;
          }
          renderList();
          updateBadge();
        })
        .catch(function (err) {
          console.error('[header-notifications.js] 알림 읽음 처리 실패:', err);
        });
    }

    function markAllAsRead() {
      window.Api.post(basePath + '/notifications/read-all')
        .then(function () {
          notifications.forEach(function (n) { n.isRead = true; });
          renderList();
          updateBadge();
        })
        .catch(function (err) {
          console.error('[header-notifications.js] 알림 모두 읽음 처리 실패:', err);
        });
    }

    function openPanel() {
      panelEl.hidden = false;
    }

    function closePanel() {
      panelEl.hidden = true;
    }

    bellBtn.addEventListener('click', function () {
      if (panelEl.hidden) {
        openPanel();
      } else {
        closePanel();
      }
    });

    markAllBtn.addEventListener('click', markAllAsRead);

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

    document.addEventListener('gong9ri:auth-resolved', function (event) {
      var detail = event.detail || {};
      var role = detail.loggedIn && detail.member ? detail.member.role : null;

      if (role !== 'BUYER' && role !== 'SELLER') {
        wrapEl.hidden = true;
        return;
      }

      basePath = role === 'BUYER' ? '/buyer/mypage' : '/seller/mypage';
      wrapEl.hidden = false;
      loadNotifications();
    });
  });
})();
