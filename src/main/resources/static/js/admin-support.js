/**
 * admin-support.js — 관리자 상담 화면 (support/chat)
 *
 * 왼쪽에서 상담을 고르면 오른쪽에 대화가 뜨고, 그때 그 방을 실시간 구독한다.
 * **방을 바꾸면 이전 구독을 끊는다** — 안 끊으면 안 보고 있는 방의 메시지까지 계속 받아
 * 화면이 섞이고 연결도 쌓인다.
 *
 * 관리자가 접속 중이 아니어도 메시지는 서버에 저장되므로, 목록의 미읽음 배지로 나중에 확인한다
 * ("실시간이면 좋고, 아니어도 유실 없음").
 */
(function () {
  var pageAlertEl = document.getElementById('page-alert');
  var pageAlertTextEl = document.getElementById('page-alert-text');
  var roomsStatusEl = document.getElementById('rooms-status');
  var roomsListEl = document.getElementById('rooms-list');
  var threadTitleEl = document.getElementById('thread-title');
  var threadStatusEl = document.getElementById('thread-status');
  var threadListEl = document.getElementById('thread-messages');
  var threadTypingEl = document.getElementById('thread-typing');
  var threadFormEl = document.getElementById('thread-form');
  var threadInputEl = document.getElementById('thread-input');
  var closeRoomBtn = document.getElementById('close-room-btn');

  if (!roomsListEl || !threadListEl || !threadFormEl || !window.Api || !window.AdminGuard) {
    return;
  }

  var state = { roomId: null, client: null, typingTimer: null, lastTypingSent: 0 };

  function showError(text) {
    pageAlertEl.hidden = false;
    pageAlertTextEl.textContent = text;
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
    // 관리자 화면에서는 내가 보낸 것(sentByAdmin)이 오른쪽이다 — 사용자 위젯과 좌우가 반대다.
    li.className = 'support-message' + (message.sentByAdmin ? ' support-message--mine' : ' support-message--admin');

    var body = document.createElement('p');
    body.className = 'support-message__body';
    body.textContent = message.content;
    li.appendChild(body);

    var meta = document.createElement('span');
    meta.className = 'support-message__meta';
    meta.textContent = (message.sentByAdmin ? '나(상담원)' : message.senderName) + ' · ' + formatTime(message.createdAt);
    li.appendChild(meta);

    threadListEl.appendChild(li);
    threadListEl.scrollTop = threadListEl.scrollHeight;
  }

  function renderRooms(rooms) {
    roomsListEl.innerHTML = '';
    if (!rooms.length) {
      roomsStatusEl.hidden = false;
      roomsStatusEl.textContent = '들어온 상담이 없습니다.';
      return;
    }
    roomsStatusEl.hidden = true;
    rooms.forEach(function (room) {
      var li = document.createElement('li');
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'support-admin-room' + (room.roomId === state.roomId ? ' is-active' : '');

      var name = document.createElement('span');
      name.className = 'support-admin-room__name';
      name.textContent = room.memberName + (room.unreadForAdmin > 0 ? ' (' + room.unreadForAdmin + ')' : '');
      btn.appendChild(name);

      var meta = document.createElement('span');
      meta.className = 'support-admin-room__meta';
      meta.textContent = (room.status === 'OPEN' ? '진행중' : '종료') + ' · ' + formatTime(room.lastMessageAt);
      btn.appendChild(meta);

      btn.addEventListener('click', function () {
        selectRoom(room);
      });
      li.appendChild(btn);

      // 쓸데없는 상담 정리 — 종료(close)는 기록을 남기지만 이건 대화까지 지운다.
      var delBtn = document.createElement('button');
      delBtn.type = 'button';
      delBtn.className = 'btn btn-ghost btn-sm';
      delBtn.style.marginTop = 'var(--space-1)';
      delBtn.textContent = '상담 삭제';
      delBtn.addEventListener('click', function () {
        if (!window.confirm('"' + room.memberName + '" 님과의 상담을 삭제할까요?\n대화 내용까지 사라지며 되돌릴 수 없습니다.')) {
          return;
        }
        delBtn.disabled = true;
        window.Api.del('/admin/support/rooms/' + room.roomId)
          .then(function () {
            if (state.roomId === room.roomId) {
              // 보고 있던 방을 지웠으면 오른쪽 대화창도 비운다 — 안 그러면 없는 방을 보고 있게 된다.
              if (state.client) {
                state.client.deactivate();
                state.client = null;
              }
              state.roomId = null;
              threadListEl.innerHTML = '';
              threadTitleEl.textContent = '상담을 선택하세요';
              threadStatusEl.hidden = false;
              threadStatusEl.textContent = '왼쪽에서 상담을 고르면 대화가 보입니다.';
              threadFormEl.hidden = true;
              closeRoomBtn.hidden = true;
            }
            loadRooms();
          })
          .catch(function (err) {
            console.error('[admin-support.js] delete room failed:', err);
            window.alert((err && err.message) || '삭제에 실패했습니다.');
            delBtn.disabled = false;
          });
      });
      li.appendChild(delBtn);

      roomsListEl.appendChild(li);
    });
  }

  function loadRooms() {
    return window.Api.get('/admin/support/rooms?page=0&size=50')
      .then(function (page) {
        renderRooms(page.content || []);
      })
      .catch(function (err) {
        console.error('[admin-support.js] load rooms failed:', err);
        showError((err && err.message) || '상담 목록을 불러오지 못했습니다.');
      });
  }

  function selectRoom(room) {
    // 방을 바꾸면 이전 구독을 반드시 끊는다 — 안 그러면 다른 방 메시지가 현재 화면에 섞인다.
    if (state.client) {
      state.client.deactivate();
      state.client = null;
    }
    state.roomId = room.roomId;
    threadTitleEl.textContent = room.memberName + ' 님과의 상담';
    threadListEl.innerHTML = '';
    threadStatusEl.hidden = false;
    threadStatusEl.textContent = '대화를 불러오는 중...';
    threadFormEl.hidden = room.status !== 'OPEN';
    closeRoomBtn.hidden = room.status !== 'OPEN';

    window.Api.get('/support/rooms/' + room.roomId)
      .then(function (detail) {
        (detail.messages || []).forEach(appendMessage);
        threadStatusEl.hidden = true;
        return window.Api.post('/support/rooms/' + room.roomId + '/read');
      })
      .then(function () {
        loadRooms();
        connectRealtime();
      })
      .catch(function (err) {
        console.error('[admin-support.js] load thread failed:', err);
        threadStatusEl.hidden = false;
        threadStatusEl.textContent = (err && err.message) || '대화를 불러오지 못했습니다.';
      });
  }

  function connectRealtime() {
    state.client = window.SupportChatClient.connect({
      roomId: state.roomId,
      onMessage: function (message) {
        appendMessage(message);
        window.Api.post('/support/rooms/' + state.roomId + '/read').catch(function () {});
      },
      onTyping: function () {
        threadTypingEl.hidden = false;
        clearTimeout(state.typingTimer);
        state.typingTimer = setTimeout(function () { threadTypingEl.hidden = true; }, 2500);
      },
      onStatus: function (status) {
        if (status === 'unavailable') {
          threadStatusEl.hidden = false;
          threadStatusEl.textContent = '실시간 연결을 쓸 수 없어요. 새로고침하면 새 메시지가 보여요.';
        }
      },
    });
  }

  threadInputEl.addEventListener('input', function () {
    var now = Date.now();
    if (state.roomId && now - state.lastTypingSent > 1000) {
      state.lastTypingSent = now;
      window.SupportChatClient.sendTyping(state.client, state.roomId);
    }
  });

  threadFormEl.addEventListener('submit', function (event) {
    event.preventDefault();
    var content = threadInputEl.value.trim();
    if (!content || !state.roomId) {
      return;
    }
    if (!window.SupportChatClient.send(state.client, state.roomId, content)) {
      showError('연결이 끊겨 메시지를 보내지 못했습니다.');
      return;
    }
    threadInputEl.value = '';
  });

  closeRoomBtn.addEventListener('click', function () {
    if (!state.roomId || !window.confirm('이 상담을 종료할까요? 종료 후에는 메시지를 주고받을 수 없습니다.')) {
      return;
    }
    window.Api.post('/support/rooms/' + state.roomId + '/close')
      .then(function () {
        threadFormEl.hidden = true;
        closeRoomBtn.hidden = true;
        loadRooms();
      })
      .catch(function (err) {
        showError((err && err.message) || '상담 종료에 실패했습니다.');
      });
  });

  loadRooms();
})();
