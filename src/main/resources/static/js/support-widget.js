/**
 * support-widget.js — 고객센터 상담 위젯 (support/chat)
 *
 * 로그인했고 **관리자가 아닐 때만** 노출한다(관리자는 전용 화면에서 상담을 받는다).
 * 구매자·판매자 모두 쓸 수 있다 — 플랫폼 문의라 역할을 가리지 않는다.
 *
 * 흐름: 열기 → POST /api/support/rooms(이미 열린 방이 있으면 그 방) → 지난 대화 표시 →
 * WebSocket 연결 → 실시간 송수신. WebSocket이 안 되면 지난 대화는 그대로 보이고 실시간만 죽는다.
 */
(function () {
  // 위젯 마크업은 include.js가 partial을 fetch해 넣어주므로, 스크립트 로드 시점에는 아직 없다.
  // 그때 getElementById를 하면 전부 null이라 위젯이 통째로 안 뜬다(2026-08-21 실제로 겪음).
  // 그래서 chat-widget.js와 동일하게 'gong9ri:includes-ready' 이후에 바인딩한다.
  var widget, toggleBtn, panel, closeBtn, statusEl, listEl, typingEl, formEl, inputEl, badgeEl;

  var state = {
    roomId: null, client: null, opened: false,
    typingTimer: null, lastTypingSent: 0,
    loggedIn: false, authResolved: false,
  };

  function showStatus(text) {
    statusEl.hidden = !text;
    statusEl.textContent = text || '';
  }

  function formatTime(iso) {
    if (!iso) {
      return '';
    }
    var d = new Date(iso);
    return isNaN(d.getTime()) ? '' : d.toLocaleString('ko-KR');
  }

  function appendMessage(message) {
    var li = document.createElement('li');
    li.className = 'support-message' + (message.sentByAdmin ? ' support-message--admin' : ' support-message--mine');

    var body = document.createElement('p');
    body.className = 'support-message__body';
    body.textContent = message.content;
    li.appendChild(body);

    var meta = document.createElement('span');
    meta.className = 'support-message__meta';
    meta.textContent = (message.sentByAdmin ? '상담원' : '나') + ' · ' + formatTime(message.createdAt);
    li.appendChild(meta);

    listEl.appendChild(li);
    listEl.scrollTop = listEl.scrollHeight;
  }

  function renderMessages(messages) {
    listEl.innerHTML = '';
    (messages || []).forEach(appendMessage);
  }

  function connectRealtime() {
    state.client = window.SupportChatClient.connect({
      roomId: state.roomId,
      onMessage: function (message) {
        appendMessage(message);
        // 열려 있는 동안 받은 건 바로 읽은 것으로 처리한다.
        window.Api.post('/support/rooms/' + state.roomId + '/read').catch(function () {});
      },
      onTyping: function () {
        typingEl.hidden = false;
        clearTimeout(state.typingTimer);
        state.typingTimer = setTimeout(function () { typingEl.hidden = true; }, 2500);
      },
      onStatus: function (status) {
        if (status === 'connected') {
          showStatus('');
        } else if (status === 'unavailable') {
          showStatus('실시간 연결을 쓸 수 없어요. 보낸 메시지는 저장되니 새로고침하면 보여요.');
        } else {
          showStatus('연결이 끊겼어요. 다시 연결하는 중...');
        }
      },
    });
  }

  function openRoom() {
    if (!state.loggedIn) {
      // 상담 API는 로그인을 요구한다. 버튼은 누구에게나 보이되(고객센터가 있다는 걸 알려야 하니까),
      // 열었을 때 로그인으로 안내한다.
      showStatus('상담은 로그인 후 이용할 수 있어요.');
      listEl.innerHTML = '';
      formEl.hidden = true;
      var link = document.createElement('a');
      link.className = 'btn btn-primary btn-sm';
      link.href = '/login.html?redirect=' + encodeURIComponent(window.location.pathname + window.location.search);
      link.textContent = '로그인하러 가기';
      var li = document.createElement('li');
      li.appendChild(link);
      listEl.appendChild(li);
      state.opened = false; // 로그인하고 돌아오면 다시 시도할 수 있게
      return;
    }
    formEl.hidden = false;
    showStatus('상담을 불러오는 중입니다...');
    window.Api.post('/support/rooms')
      .then(function (room) {
        state.roomId = room.roomId;
        renderMessages(room.messages);
        badgeEl.hidden = true;
        showStatus(room.messages && room.messages.length ? '' : '문의 내용을 남겨주시면 상담원이 답변드려요.');
        window.Api.post('/support/rooms/' + state.roomId + '/read').catch(function () {});
        connectRealtime();
      })
      .catch(function (err) {
        console.error('[support-widget.js] open room failed:', err);
        showStatus((err && err.message) || '상담을 열지 못했어요.');
      });
  }

  function bindEvents() {
    toggleBtn.addEventListener('click', function () {
      var willOpen = panel.hidden;
      panel.hidden = !willOpen;
      if (willOpen && !state.opened) {
        state.opened = true;
        openRoom();
      }
    });

    closeBtn.addEventListener('click', function () {
      panel.hidden = true;
    });

    inputEl.addEventListener('input', function () {
      // 입력 중 신호는 초당 한 번으로 제한한다 — 글자마다 보내면 서버 채널만 시끄러워진다.
      var now = Date.now();
      if (state.roomId && now - state.lastTypingSent > 1000) {
        state.lastTypingSent = now;
        window.SupportChatClient.sendTyping(state.client, state.roomId);
      }
    });

    formEl.addEventListener('submit', function (event) {
      event.preventDefault();
      var content = inputEl.value.trim();
      if (!content || !state.roomId) {
        return;
      }
      var sent = window.SupportChatClient.send(state.client, state.roomId, content);
      if (!sent) {
        showStatus('연결이 끊겨 메시지를 보내지 못했어요. 잠시 후 다시 시도해주세요.');
        return;
      }
      inputEl.value = '';
    });
  }

  /**
   * 노출 규칙: **관리자만 숨긴다.** 비로그인·구매자·판매자 모두 보여야 한다 — 고객센터는 로그인
   * 전에도 "여기서 문의할 수 있다"는 걸 알려야 하는 창구다(2026-08-21 사용자 결정).
   * 관리자는 전용 화면(admin/support.html)에서 상담을 받으므로 이 위젯이 필요 없다.
   */
  function applyAuth(detail) {
    var member = (detail && detail.member) || {};
    state.loggedIn = !!(detail && detail.loggedIn);
    state.authResolved = true;
    widget.hidden = member.role === 'ADMIN';

    if (!state.loggedIn) {
      return;
    }
    // 안 읽은 답변이 있으면 열기 전에도 뱃지로 알린다.
    window.Api.get('/support/rooms/me')
      .then(function (room) {
        if (room && room.unreadForMember > 0) {
          badgeEl.hidden = false;
          badgeEl.textContent = room.unreadForMember;
        }
      })
      .catch(function () {});
  }

  function init() {
    widget = document.getElementById('support-widget');
    toggleBtn = document.getElementById('support-widget-toggle');
    panel = document.getElementById('support-widget-panel');
    closeBtn = document.getElementById('support-widget-close');
    statusEl = document.getElementById('support-widget-status');
    listEl = document.getElementById('support-widget-messages');
    typingEl = document.getElementById('support-widget-typing');
    formEl = document.getElementById('support-widget-form');
    inputEl = document.getElementById('support-widget-input');
    badgeEl = document.getElementById('support-widget-badge');

    if (!widget || !toggleBtn || !panel || !formEl || !inputEl || !window.Api) {
      return;
    }
    bindEvents();

    // auth-resolved가 이미 지나갔을 수 있다(include보다 먼저 끝나는 경우). 그때를 대비해
    // 이벤트를 기다리기만 하지 않고, 아직 확정 전이면 직접 한 번 물어본다.
    document.addEventListener('gong9ri:auth-resolved', function (event) {
      applyAuth(event.detail);
    });
    if (!state.authResolved) {
      window.Api.get('/auth/me')
        .then(function (member) { applyAuth({ loggedIn: true, member: member }); })
        .catch(function () { applyAuth({ loggedIn: false }); });
    }
  }

  document.addEventListener('gong9ri:includes-ready', init);
})();
