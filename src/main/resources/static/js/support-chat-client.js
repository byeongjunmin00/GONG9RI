/**
 * support-chat-client.js — 상담 WebSocket(STOMP) 공용 클라이언트 (support/chat)
 *
 * 사용자 위젯(support-widget.js)과 관리자 화면(admin-support.js)이 함께 쓴다.
 *
 * - 연결은 /ws-support로 한다. 이 경로는 SecurityConfig의 permitAll 목록에 없어 **핸드셰이크부터
 *   로그인을 요구**하므로, 비로그인이면 연결 자체가 실패한다(그게 정상이다).
 * - 구독 권한은 서버(SupportChatChannelInterceptor)가 판정한다. 프론트가 막는 게 아니라,
 *   서버가 거절하면 메시지가 안 올 뿐이다 — 프론트 검증은 UX 보조이지 보안 장치가 아니다.
 * - STOMP 라이브러리는 CDN에서 온다. **못 불러오면 실시간만 죽고 나머지는 살아야 한다** —
 *   지난 대화는 REST로 이미 받아둔 상태라, 연결 실패 시 "새로고침하면 보인다"로 낮춰 동작한다.
 */
(function () {
  var ENDPOINT = '/ws-support';

  function isAvailable() {
    return typeof window.StompJs !== 'undefined' && window.StompJs.Client;
  }

  /**
   * @param {Object} options
   *   roomId      구독할 상담방
   *   onMessage   서버가 보낸 메시지 payload를 받는다
   *   onTyping    상대가 입력 중일 때
   *   onRead      상대가 내 메시지를 읽었을 때(읽음 표시 갱신용)
   *   onStatus    'connected' | 'disconnected' | 'unavailable'
   */
  function connect(options) {
    if (!isAvailable()) {
      options.onStatus && options.onStatus('unavailable');
      return null;
    }

    var protocol = window.location.protocol === 'https:' ? 'wss://' : 'ws://';
    var client = new window.StompJs.Client({
      brokerURL: protocol + window.location.host + ENDPOINT,
      // 서버 스레드풀이 작게 고정돼 있어(WebSocketConfig, OOM 대응) 하트비트를 촘촘히 두지 않는다.
      heartbeatIncoming: 20000,
      heartbeatOutgoing: 20000,
      reconnectDelay: 5000,
      onConnect: function () {
        client.subscribe('/topic/support/' + options.roomId, function (frame) {
          var payload = JSON.parse(frame.body);
          // 입력 중 신호와 실제 메시지를 구분한다 — 타이핑 신호는 저장되지 않는 임시 신호다.
          if (payload && payload.type === 'READ') {
            // 상대가 읽었다는 신호 — 저장되는 메시지가 아니라 화면 표시만 바꾼다.
            options.onRead && options.onRead(payload);
            return;
          }
          if (payload && payload.type === 'TYPING') {
            options.onTyping && options.onTyping(payload);
            return;
          }
          options.onMessage && options.onMessage(payload);
        });
        options.onStatus && options.onStatus('connected');
      },
      onWebSocketClose: function () {
        options.onStatus && options.onStatus('disconnected');
      },
      onStompError: function (frame) {
        console.error('[support-chat-client.js] STOMP error:', frame && frame.headers);
        options.onStatus && options.onStatus('disconnected');
      },
    });
    client.activate();
    return client;
  }

  function send(client, roomId, content) {
    if (!client || !client.connected) {
      return false;
    }
    client.publish({
      destination: '/app/support/' + roomId + '/send',
      body: JSON.stringify({ content: content }),
    });
    return true;
  }

  function sendTyping(client, roomId) {
    if (!client || !client.connected) {
      return;
    }
    client.publish({ destination: '/app/support/' + roomId + '/typing', body: '{}' });
  }

  /**
   * 토픽 하나만 구독하는 **저수준** 연결. 관리자 화면처럼 여러 토픽을 한 연결로 붙였다 뗐다 해야 할 때
   * 쓴다 — 방마다 새 WebSocket을 여는 대신 구독만 갈아끼운다.
   */
  function connectRaw(options) {
    if (!isAvailable()) {
      options.onStatus && options.onStatus('unavailable');
      return null;
    }
    var protocol = window.location.protocol === 'https:' ? 'wss://' : 'ws://';
    var client = new window.StompJs.Client({
      brokerURL: protocol + window.location.host + ENDPOINT,
      heartbeatIncoming: 20000,
      heartbeatOutgoing: 20000,
      reconnectDelay: 5000,
      onConnect: function () {
        options.onConnect && options.onConnect(client);
        options.onStatus && options.onStatus('connected');
      },
      onWebSocketClose: function () {
        options.onStatus && options.onStatus('disconnected');
      },
      onStompError: function (frame) {
        console.error('[support-chat-client.js] STOMP error:', frame && frame.headers);
        options.onStatus && options.onStatus('disconnected');
      },
    });
    client.activate();
    return client;
  }

  /** 구독 하나. 반환값의 unsubscribe()로 끊는다. 구독이 거절되면 메시지가 안 올 뿐이다(서버가 판정). */
  function subscribe(client, topic, handler) {
    if (!client || !client.connected) {
      return null;
    }
    return client.subscribe(topic, function (frame) {
      var payload = null;
      try {
        payload = JSON.parse(frame.body);
      } catch (e) {
        return;
      }
      handler(payload);
    });
  }

  window.SupportChatClient = {
    isAvailable: isAvailable,
    connect: connect,
    connectRaw: connectRaw,
    subscribe: subscribe,
    send: send,
    sendTyping: sendTyping,
  };
})();
