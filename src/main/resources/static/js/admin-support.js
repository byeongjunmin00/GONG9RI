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

  var state = { roomId: null, client: null, roomSub: null, typingTimer: null, lastTypingSent: 0, myMemberId: null };

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

  // 화면에 떠 있는 내(상담원) 메시지들의 "읽음" 자리 — 사용자가 읽으면 한꺼번에 채운다.
  var myReadMarks = [];

  function markAllMineAsRead() {
    myReadMarks.forEach(function (el) { el.textContent = ' · 읽음'; });
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
    if (message.sentByAdmin) {
      meta.textContent = '나(상담원) · ' + formatTime(message.createdAt);
      // 상담원이 보낸 것에만 읽음 표시 — 아직 안 읽혔으면 아무것도 쓰지 않는다("안읽음"을 명시하면
      // 자리를 비운 것뿐인데 무시당한 것처럼 읽힌다).
      var readEl = document.createElement('span');
      readEl.className = 'support-message__read';
      readEl.textContent = message.readByCounterpart ? ' · 읽음' : '';
      meta.appendChild(readEl);
      myReadMarks.push(readEl);
    } else {
      // 상대방 메시지에만 사진을 붙인다 — 내 메시지에 내 사진을 붙이는 건 구분에 도움이 안 된다.
      meta.appendChild(window.Avatar.withName(
          message.senderName || '', message.senderProfileImageUrl, 'xs'));
      var timeEl = document.createElement('span');
      timeEl.textContent = ' · ' + formatTime(message.createdAt);
      meta.appendChild(timeEl);
    }
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
      // 카드(li)가 테두리를 갖고, 그 안에 "선택" 버튼과 "삭제" 버튼이 함께 들어간다 —
      // 카드 자체가 <button>이면 삭제 버튼을 안에 넣을 수 없다(버튼 중첩 불가).
      var li = document.createElement('li');
      li.className = 'support-admin-room-card' + (room.roomId === state.roomId ? ' is-active' : '');
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'support-admin-room';

      var name = document.createElement('span');
      name.className = 'support-admin-room__name';
      name.appendChild(window.Avatar.withName(
          room.memberName || '', room.memberProfileImageUrl, 'sm'));
      if (room.unreadForAdmin > 0) {
        var unreadEl = document.createElement('span');
        unreadEl.textContent = ' (' + room.unreadForAdmin + ')';
        name.appendChild(unreadEl);
      }
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
      // support-admin-room-card__actions가 카드 기준 절대 위치로 메타 줄 우측에 작게 배치한다
      // (구분선으로 나눈 별도 줄이 아니다 — components.css 참고).
      var delBtn = document.createElement('button');
      delBtn.type = 'button';
      delBtn.className = 'btn btn-ghost btn-sm support-admin-room-card__actions';
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
              // 연결은 유지한다(관리자 목록 갱신 신호를 계속 받아야 한다).
              unsubscribeRoom();
              state.roomId = null;
              threadListEl.innerHTML = '';
              myReadMarks = [];
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
    // 방을 바꾸면 이전 **구독만** 끊는다. 연결까지 끊으면 관리자 목록 갱신 신호도 같이 끊긴다
    // (연결 하나로 관리자 토픽 + 선택한 방을 함께 구독한다).
    unsubscribeRoom();
    state.roomId = room.roomId;
    while (threadTitleEl.firstChild) threadTitleEl.removeChild(threadTitleEl.firstChild);
    threadTitleEl.appendChild(window.Avatar.create(room.memberName, room.memberProfileImageUrl, 'md'));
    var titleTextEl = document.createElement('span');
    titleTextEl.textContent = room.memberName + ' 님과의 상담';
    threadTitleEl.appendChild(titleTextEl);
    threadTitleEl.classList.add('avatar-name');
    threadListEl.innerHTML = '';
    // 방을 바꾸면 이전 방의 읽음 표시 엘리먼트를 계속 들고 있지 않도록 비운다.
    myReadMarks = [];
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
        subscribeRoom();
      })
      .catch(function (err) {
        console.error('[admin-support.js] load thread failed:', err);
        threadStatusEl.hidden = false;
        threadStatusEl.textContent = (err && err.message) || '대화를 불러오지 못했습니다.';
      });
  }

  function unsubscribeRoom() {
    if (state.roomSub) {
      state.roomSub.unsubscribe();
      state.roomSub = null;
    }
  }

  /** 선택한 방을 구독한다. 연결은 페이지 로드 시 만든 것을 재사용한다. */
  function subscribeRoom() {
    unsubscribeRoom();
    if (!state.client || !state.roomId) {
      return;
    }
    state.roomSub = window.SupportChatClient.subscribe(
      state.client, '/topic/support/' + state.roomId, function (payload) {
        if (payload && payload.type === 'READ') {
          // 사용자가 읽었을 때만 내 답변의 읽음 표시를 켠다(관리자인 내가 읽은 신호는 무시).
          if (!payload.readByAdmin) {
            markAllMineAsRead();
          }
          return;
        }
        if (payload && payload.type === 'TYPING') {
          // 내가 친 신호는 무시한다 — 안 그러면 답변을 쓰는 동안 "고객이 입력 중"이 뜬다.
          if (state.myMemberId != null && payload.senderId === state.myMemberId) {
            return;
          }
          threadTypingEl.hidden = false;
          clearTimeout(state.typingTimer);
          state.typingTimer = setTimeout(function () { threadTypingEl.hidden = true; }, 2500);
          return;
        }
        appendMessage(payload);
        window.Api.post('/support/rooms/' + state.roomId + '/read').catch(function () {});
      });
  }

  /**
   * 연결은 페이지당 하나. 관리자 토픽(/topic/admin/support)을 상시 구독해 **다른 방에 온 메시지나
   * 새 상담도 목록에 바로 뜨게** 한다 — 예전엔 선택한 방만 구독해서 새로고침해야만 알 수 있었다
   * (2026-08-21 사용자 리포트).
   */
  function connectRealtime() {
    state.client = window.SupportChatClient.connectRaw({
      onConnect: function (client) {
        window.SupportChatClient.subscribe(client, '/topic/admin/support', function () {
          // 신호에는 내용이 없다 — 목록을 다시 불러오는 게 전부다(내용은 권한 검사를 거친 조회로).
          loadRooms();
        });
        subscribeRoom();
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

  // 내가 보낸 입력 신호를 걸러내려면 내 회원 id가 필요하다.
  window.Api.get('/auth/me')
    .then(function (member) { state.myMemberId = member.memberId; })
    .catch(function () {});

  loadRooms();
  connectRealtime();
})();
