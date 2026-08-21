/**
 * header-notifications.js — 헤더 알림 벨(구매자/판매자/관리자 공용)
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
    var moreBtn = document.getElementById('header-notifications-more');

    if (!wrapEl || !bellBtn || !badgeEl || !panelEl || !listEl || !markAllBtn || !moreBtn || !window.Api) {
      return;
    }

    var PAGE_SIZE = 20;

    var basePath = null; // '/buyer/mypage' | '/seller/mypage' — 로그인한 역할에 따라 정해짐
    var notifications = [];
    // 안 읽은 개수는 목록에서 세지 않고 서버가 준 값을 쓴다 — 목록이 잘려 오기 때문에 여기서 세면
    // 틀린다(안읽음 30개인데 20개만 받으면 20으로 세고, 그 20개를 읽으면 0이 되지만 실제론 10개 남음).
    var unreadCount = 0;
    var loadedPage = -1;
    var hasNext = false;
    var loading = false;

    function updateBadge() {
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
        moreBtn.hidden = true;
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

      moreBtn.hidden = !hasNext;
    }

    /**
     * 알림 목록을 한 페이지 불러온다. reset이면 처음부터 다시, 아니면 다음 페이지를 이어붙인다.
     * "더 보기"로 이어보는 이유 — 오래된 알림을 볼 곳이 이 드롭다운밖에 없어서(마이페이지에 알림
     * 화면이 없다) 그냥 잘라내면 이전 알림에 접근할 방법이 사라진다.
     */
    function loadNotifications(reset) {
      if (loading) {
        return Promise.resolve();
      }
      loading = true;
      var nextPage = reset ? 0 : loadedPage + 1;

      return window.Api.get(basePath + '/notifications?page=' + nextPage + '&size=' + PAGE_SIZE)
        .then(function (data) {
          var payload = data || {};
          var page = payload.notifications || [];
          notifications = reset ? page : notifications.concat(page);
          unreadCount = payload.unreadCount || 0;
          hasNext = !!payload.hasNext;
          loadedPage = nextPage;
          renderList();
          updateBadge();
        })
        .catch(function (err) {
          console.error('[header-notifications.js] 알림 목록 조회 실패:', err);
        })
        .then(function () {
          loading = false;
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
          if (target && !target.isRead) {
            target.isRead = true;
            // 서버가 준 개수를 로컬에서 같이 줄인다(목록을 다시 불러오지 않는 기존 원칙 유지).
            unreadCount = Math.max(0, unreadCount - 1);
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
          // 서버는 이 회원의 안 읽은 알림을 전부 처리했으므로, 아직 안 불러온 페이지의 것까지 0이 된다.
          unreadCount = 0;
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

    moreBtn.addEventListener('click', function () {
      loadNotifications(false);
    });

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

      // 관리자도 알림을 받는다(고객센터 상담이 오면). 예전엔 조회 경로가 구매자·판매자용뿐이라
      // 관리자는 알림벨 자체가 안 떴고, 상담이 와도 대시보드에 직접 들어가야만 알 수 있었다
      // (2026-08-21 사용자 리포트). 경로만 하나 더 갈라주면 나머지 로직은 그대로 쓸 수 있다.
      var BASE_PATH_BY_ROLE = {
        BUYER: '/buyer/mypage',
        SELLER: '/seller/mypage',
        ADMIN: '/admin/mypage',
      };
      if (!BASE_PATH_BY_ROLE[role]) {
        wrapEl.hidden = true;
        return;
      }

      basePath = BASE_PATH_BY_ROLE[role];
      wrapEl.hidden = false;
      loadNotifications(true);
    });
  });
})();
