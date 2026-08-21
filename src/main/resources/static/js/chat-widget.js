/**
 * chat-widget.js — 구매자 챗봇 플로팅 위젯(partials/chat-widget.html) 전용 스크립트
 *
 * 흐름:
 * 1. js/header-auth.js가 발행하는 'gong9ri:auth-resolved' 이벤트를 구독해(중복으로
 *    /auth/me를 다시 호출하지 않는다) 로그인 상태가 BUYER로 확인될 때만 위젯(#chat-widget)의
 *    hidden을 해제한다. 그 외(비로그인/SELLER)는 계속 숨김 상태로 둔다.
 * 2. 토글 버튼 클릭 시 대화 패널을 열고 닫는다. 패널을 처음 열 때 sessionStorage에 저장된
 *    세션ID가 있으면 GET /api/buyer/chat/sessions/{id}/messages로 이전 대화를 불러와
 *    렌더링한다 — 실패(만료/본인 것 아님 등)해도 에러를 보여주지 않고 조용히 새 대화로 넘어간다.
 * 3. 메시지 전송은 POST /api/buyer/chat/messages(SSE 스트리밍) — 네이티브 EventSource는
 *    POST 바디를 못 보내므로 fetch + response.body.getReader()로 직접 SSE 텍스트를 파싱한다
 *    (docs/api/chat.md의 정확한 포맷: `event:이름` 줄 다음 `data:내용` 줄, 빈 줄로 구분).
 *    - `session` 이벤트: 세션ID 갱신 + sessionStorage 저장.
 *    - `message` 이벤트: 어시스턴트 말풍선에 텍스트를 이어붙임(스트리밍처럼 보이게).
 *    - `done` 이벤트: 입력창/전송 버튼 재활성화.
 * 4. 사용자 입력·서버 응답 텍스트는 전부 textContent로만 DOM에 반영한다(innerHTML 미사용, XSS 방지).
 *
 * 이 파일은 window.header-auth.js보다 뒤에 로드되어야 한다(HTML에서 스크립트 순서로 보장).
 */
(function () {
  var STORAGE_KEY = 'gong9ri_chat_session_id';
  var FALLBACK_ERROR_MESSAGE = '메시지를 보내지 못했습니다. 잠시 후 다시 시도해주세요.';

  var widgetEl;
  var toggleBtn;
  var panelEl;
  var closeBtn;
  var messagesEl;
  var formEl;
  var inputEl;
  var sendBtn;

  var currentSessionId = null;
  var historyLoaded = false;
  var sending = false;

  function bindElements() {
    widgetEl = document.getElementById('chat-widget');
    toggleBtn = document.getElementById('chat-widget-toggle');
    panelEl = document.getElementById('chat-widget-panel');
    closeBtn = document.getElementById('chat-widget-close');
    messagesEl = document.getElementById('chat-widget-messages');
    formEl = document.getElementById('chat-widget-form');
    inputEl = document.getElementById('chat-widget-input');
    sendBtn = document.getElementById('chat-widget-send');

    return !!(
      widgetEl && toggleBtn && panelEl && closeBtn && messagesEl && formEl && inputEl && sendBtn
    );
  }

  /**
   * @param {string} role  'user' | 'assistant' | 'system'
   * @param {string} text
   * @returns {HTMLElement} 생성된 말풍선 엘리먼트 (스트리밍 이어붙이기용)
   */
  function appendMessage(role, text) {
    var el = document.createElement('div');
    el.className = 'chat-widget__message chat-widget__message--' + role;
    el.textContent = text;
    messagesEl.appendChild(el);
    messagesEl.scrollTop = messagesEl.scrollHeight;
    return el;
  }

  function setSending(value) {
    sending = value;
    inputEl.disabled = value;
    sendBtn.disabled = value;
  }

  function saveSessionId(sessionId) {
    currentSessionId = sessionId;
    try {
      sessionStorage.setItem(STORAGE_KEY, sessionId);
    } catch (e) {
      // sessionStorage 사용 불가(프라이빗 모드 등) — 대화는 그대로 진행, 새로고침 시 이력만 안 남는다.
    }
  }

  function forgetSessionId() {
    currentSessionId = null;
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch (e) {
      // no-op
    }
  }

  /**
   * 대화 이력 복원. 실패(만료된 세션, 본인 것이 아님 등)해도 에러를 보여주지 않고
   * 조용히 sessionStorage를 지운 뒤 새 대화로 취급한다(설계 문서 명시).
   */
  function loadHistory(sessionId) {
    return fetch('/api/buyer/chat/sessions/' + sessionId + '/messages', { credentials: 'same-origin' })
      .then(function (res) {
        if (!res.ok) {
          throw new Error('chat history fetch failed: ' + res.status);
        }
        return res.json();
      })
      .then(function (json) {
        var list = json && json.data;
        if (!Array.isArray(list)) {
          throw new Error('unexpected chat history payload');
        }
        list.forEach(function (message) {
          var role = message && message.role === 'ASSISTANT' ? 'assistant' : 'user';
          appendMessage(role, (message && message.content) || '');
        });
      })
      .catch(function (err) {
        console.error('[chat-widget.js] failed to load chat history, starting fresh:', err);
        forgetSessionId();
      });
  }

  /**
   * fetch + getReader()로 받은 SSE 응답 본문을 event:/data: 두 줄 + 빈 줄 구분 포맷으로 파싱한다.
   */
  function readStream(response) {
    var reader = response.body.getReader();
    var decoder = new TextDecoder('utf-8');
    var buffer = '';
    var assistantEl = null;

    function processEvent(block) {
      var lines = block.split('\n');
      var eventName = '';
      var dataLines = [];

      lines.forEach(function (line) {
        if (line.indexOf('event:') === 0) {
          eventName = line.slice('event:'.length).trim();
        } else if (line.indexOf('data:') === 0) {
          dataLines.push(line.slice('data:'.length));
        }
      });

      var data = dataLines.join('\n');

      if (eventName === 'session') {
        saveSessionId(data);
      } else if (eventName === 'message') {
        if (!assistantEl) {
          assistantEl = appendMessage('assistant', '');
        }
        assistantEl.textContent += data;
        messagesEl.scrollTop = messagesEl.scrollHeight;
      } else if (eventName === 'done') {
        setSending(false);
      }
    }

    function consumeBuffer(finalFlush) {
      var parts = buffer.split('\n\n');
      buffer = finalFlush ? '' : parts.pop();
      parts.forEach(function (block) {
        if (block.trim()) {
          processEvent(block);
        }
      });
    }

    function pump() {
      return reader.read().then(function (result) {
        if (result.done) {
          buffer += decoder.decode();
          consumeBuffer(true);
          setSending(false); // 안전망: done 이벤트를 못 받고 스트림이 끝난 경우 대비
          return;
        }

        buffer += decoder.decode(result.value, { stream: true });
        consumeBuffer(false);
        return pump();
      });
    }

    return pump();
  }

  function handleSubmit(e) {
    e.preventDefault();

    if (sending) {
      return;
    }

    var text = inputEl.value.trim();
    if (!text) {
      return;
    }

    appendMessage('user', text);
    inputEl.value = '';
    setSending(true);

    var body = { content: text };
    if (currentSessionId !== null) {
      body.sessionId = Number(currentSessionId);
    }

    fetch('/api/buyer/chat/messages', {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
      .then(function (res) {
        if (!res.ok) {
          return res
            .json()
            .catch(function () {
              return null;
            })
            .then(function (json) {
              var message = (json && json.message) || FALLBACK_ERROR_MESSAGE;
              appendMessage('system', message);
              setSending(false);
            });
        }
        return readStream(res);
      })
      .catch(function (err) {
        console.error('[chat-widget.js] chat request failed:', err);
        appendMessage('system', FALLBACK_ERROR_MESSAGE);
        setSending(false);
      });
  }

  function openPanel() {
    panelEl.hidden = false;

    if (!historyLoaded) {
      historyLoaded = true;
      var storedSessionId = null;
      try {
        storedSessionId = sessionStorage.getItem(STORAGE_KEY);
      } catch (e) {
        storedSessionId = null;
      }

      if (storedSessionId) {
        currentSessionId = storedSessionId;
        loadHistory(storedSessionId);
      }
    }

    inputEl.focus();
  }

  function closePanel() {
    panelEl.hidden = true;
  }

  function handleAuthResolved(e) {
    var detail = (e && e.detail) || {};
    var isBuyer = !!(detail.loggedIn && detail.member && detail.member.role === 'BUYER');
    widgetEl.hidden = !isBuyer;
  }

  function init() {
    if (!bindElements()) {
      return;
    }

    document.addEventListener('gong9ri:auth-resolved', handleAuthResolved);

    toggleBtn.addEventListener('click', function () {
      if (panelEl.hidden) {
        // 두 상담 패널이 동시에 열리면 화면에서 서로 겹친다(2026-08-21 사용자 리포트).
        // 여는 쪽이 신호를 보내고, 다른 위젯이 스스로 닫는다 — 서로를 직접 참조하지 않아
        // 한쪽만 있는 페이지(판매자·비로그인)에서도 그대로 동작한다.
        document.dispatchEvent(new CustomEvent('gong9ri:widget-open', { detail: { widget: 'chat' } }));
        openPanel();
      } else {
        closePanel();
      }
    });

    document.addEventListener('gong9ri:widget-open', function (event) {
      if (event.detail && event.detail.widget !== 'chat') {
        closePanel();
      }
    });

    closeBtn.addEventListener('click', closePanel);
    formEl.addEventListener('submit', handleSubmit);
  }

  document.addEventListener('gong9ri:includes-ready', init);
})();
