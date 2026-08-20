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
  var widget = document.getElementById('support-widget');
  var toggleBtn = document.getElementById('support-widget-toggle');
  var panel = document.getElementById('support-widget-panel');
  var closeBtn = document.getElementById('support-widget-close');
  var statusEl = document.getElementById('support-widget-status');
  var listEl = document.getElementById('support-widget-messages');
  var typingEl = document.getElementById('support-widget-typing');
  var formEl = document.getElementById('support-widget-form');
  var inputEl = document.getElementById('support-widget-input');
  var badgeEl = document.getElementById('support-widget-badge');

  if (!widget || !toggleBtn || !panel || !formEl || !inputEl || !window.Api) {
    return;
  }

  var state = { roomId: null, client: null, opened: false, typingTimer: null, lastTypingSent: 0 };

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

  // 로그인 상태·역할이 확정된 뒤에 노출을 결정한다(chat-widget.js와 같은 패턴).
  document.addEventListener('gong9ri:auth-resolved', function (event) {
    var detail = event.detail || {};
    var member = detail.member || {};
    // 관리자는 전용 화면(admin/support.html)에서 상담을 받으므로 이 위젯을 띄우지 않는다.
    var show = !!detail.loggedIn && member.role !== 'ADMIN';
    widget.hidden = !show;
    if (show) {
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
  });
})();
