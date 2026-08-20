# 관리자 1:1 실시간 상담 (support/chat) — Design

## 개요

사용자(구매자·판매자)와 관리자가 **실시간으로 1:1 대화**한다. AI 챗봇(`buyer-chatbot`)과는 별개다 — 챗봇은 구매자 전용이고 OpenAI가 답하지만, 이건 **사람(관리자)이 직접 답한다.**

진입점도 나눠 뒀다. 챗봇 위젯 안에 "상담원 연결"을 넣는 방식도 검토했으나, **챗봇은 구매자에게만 보이는데 상담은 판매자도 써야 해서** 별도 위젯으로 갈랐다(2026-08-21 사용자 결정).

## 이 기능의 본체는 권한 검사다

**STOMP 구독은 `SecurityConfig`의 HTTP 인가 규칙을 타지 않는다.** 기존 WebSocket 채널(`/topic/products/{id}/teams`)은 공구팀 정원이라는 **공개 정보**를 브로드캐스트해서 인증이 필요 없었고, `/ws-team`도 `permitAll`이다.

그 구조를 그대로 상담에 쓰면 **아무나 `/topic/support/{roomId}`를 구독해 남의 대화를 실시간으로 훔쳐볼 수 있다.** 그래서 세 겹으로 막는다.

| 지점 | 막는 방법 |
|---|---|
| 연결(핸드셰이크) | `/ws-support`를 `SecurityConfig`의 permitAll 목록에 **일부러 넣지 않는다** → `anyRequest().authenticated()`에 걸려 비로그인은 연결 자체가 안 된다 |
| 구독 | `SupportChatChannelInterceptor`가 `SUBSCRIBE`를 가로채 방 당사자/관리자만 통과 |
| 발행 | `@MessageMapping`에서 같은 판정을 다시 한다 — 구독만 막으면 **남의 방에 메시지를 밀어 넣을 수 있다** |

> REST·구독·발행이 **모두 같은 메서드**(`SupportChatService.requireParticipant`)를 쓴다. 경로마다 따로 검사하면 한쪽만 빠뜨린다.

## 데이터 모델

| 테이블 | 담는 것 |
|---|---|
| `support_room` | 상담을 연 회원, 상태(OPEN/CLOSED), 마지막 대화 시각, **양쪽 미읽음 개수** |
| `support_message` | 방, 보낸 사람, `sentByAdmin`, 내용, 시각 |

- **`chat_session`(AI 챗봇)을 재사용하지 않는다.** 참여자 구조(구매자 1명 vs 사용자+관리자)와 수명주기(대화가 끝나면 닫힘)가 달라, 섞으면 양쪽 다 지저분해진다.
- **한 회원당 열린 방은 1개.** 무한 생성을 막고 "내 상담"이 하나로 특정돼야 화면이 단순해진다. 닫힌 방은 지우지 않고 이력으로 남긴다 — 닫힌 방이 여러 개일 수 있어 DB 유니크 제약으로는 못 걸고 서비스에서 지킨다.
- **미읽음을 양쪽으로 따로 센다.** 상담은 비대칭이다(사용자는 방 하나, 관리자는 여러 방). 관리자 목록에서 "답을 기다리는 방"을 위로 올리려면 방 단위 미읽음이 필요하다.
- **`sentByAdmin`을 보낸 시점 기준으로 박아둔다.** `sender.role`로 알 수도 있지만, 나중에 그 회원의 역할이 바뀌면 과거 대화의 의미가 흔들린다.

## 규칙 / 검증

- **저장이 먼저, 브로드캐스트가 나중.** `@SendTo`로 반환값을 자동 전송하지 않고 직접 `convertAndSend`한다 — 저장이 실패하면 아무것도 나가면 안 된다. 화면에만 뜨고 DB엔 없는 메시지를 만들지 않는다.
- **관리자가 접속 중이 아니어도 메시지는 저장된다.** "실시간이면 좋고, 아니어도 유실 없음" — 관리자는 나중에 목록의 미읽음 배지로 확인한다.
- 메시지는 공백만이면 거절, 1000자 초과면 거절(서버가 자르지 않는다). WebSocket이라 Bean Validation이 안 걸려 서비스에서 검사한다.
- 종료된 상담에는 메시지를 보낼 수 없다(`SUPPORT_ROOM_CLOSED`, 409).
- 남의 방은 **404가 아니라 403**으로 막는다 — 방 id는 순번이라 존재 여부를 감출 실익이 없고, "권한 없음"이 사실에 더 가깝다.
- **입력 중(typing) 신호는 저장하지 않는다.** 사라져도 되는 신호라 DB에 남길 이유가 없다. 다만 남의 방에 신호를 보내는 것도 막아야 해서 권한 검사는 똑같이 한다. 프론트에서 초당 1회로 제한한다.

## 프론트

- 사용자 위젯은 **로그인했고 관리자가 아닐 때만** 노출한다(관리자는 전용 화면에서 받는다). 구매자·판매자 모두 쓴다.
- STOMP 클라이언트는 CDN에서 온다(카카오·PortOne SDK와 같은 방식). **못 불러와도 실시간만 죽고 나머지는 산다** — 지난 대화는 REST로 이미 받아둔 상태라 "새로고침하면 보인다"로 낮춰 동작한다.
- 관리자 화면에서 **방을 바꾸면 이전 구독을 끊는다.** 안 끊으면 안 보고 있는 방의 메시지까지 계속 받아 화면이 섞이고 연결도 쌓인다.
- 메시지는 전부 `textContent`로 채운다(`innerHTML` 미사용) — 상대가 보낸 문자열이 그대로 들어온다.

## 알려진 한계

- **인메모리 심플 브로커**라 다중 인스턴스로 늘리면 깨진다(인스턴스 간 메시지 전달 없음). 이미지 볼륨과 같은 성격의 제약이다.
- **WebSocket 연결은 메모리를 잡는다.** 이 프로젝트는 브로커 스레드풀 무제한 증식으로 프로덕션 OOM을 겪었고(`docs/logs/cd/deploy/003-oom-crash.md`), 지금은 스레드풀을 작은 고정 크기로 못박아 뒀다. 상담은 **연결이 오래 유지되는** 성격이라 기존 브로드캐스트보다 부담이 크다 — 배포 후 메모리 추이를 봐야 한다.
- 관리자 계정이 하나(`demo_admin`)라는 전제다. 여러 관리자 배정·이관은 스코프 밖.
- 파일·이미지 전송 없음. 볼륨 용량(434MB)을 상담 이미지가 갉아먹고, 업로드는 공격 표면이라 검증 지점이 또 늘어난다.

## 관련 코드 위치

- `entity/SupportRoom.java`, `SupportRoomStatus.java`, `SupportMessage.java`
- `repository/SupportRoomRepository.java`, `SupportMessageRepository.java`
- `service/SupportChatService.java` — **권한 판정(`requireParticipant`)이 여기 하나뿐이다**
- `config/SupportChatChannelInterceptor.java`, `config/WebSocketConfig.java`, `config/SecurityConfig.java`
- `controller/SupportChatController.java`(REST), `SupportChatWsController.java`(STOMP)
- `static/partials/support-widget.html`, `static/js/support-widget.js`, `support-chat-client.js`
- `static/admin/support.html`, `static/js/admin-support.js`
