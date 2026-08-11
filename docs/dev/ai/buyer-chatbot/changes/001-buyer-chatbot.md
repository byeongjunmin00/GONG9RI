# 구매자 챗봇 (ai/buyer-chatbot)

대상: ai/buyer-chatbot
담당: 민병준

## 배경 / 요구

AI 필수 3개 중 1개(구조화 출력+프롬프트 엔지니어링 — 판매자 상품등록 도우미)는 완료. 이 작업은 나머지 전부(AI 필수2 Tool Calling, 필수3 장애격리·비용인식, 도전 SSE 스트리밍+멀티턴)를 구매자용 챗봇 하나로 묶어 구현한다. AI 도전 나머지 하나(RAG)는 전용운이 별도로 진행.

## 설계

- Tool Calling: `@Tool` 2개(상품 검색, 내 공구 참여 조회), 요청 스코프 `ChatbotTools` POJO
- 장애격리: 타임아웃/API장애 Fallback 2가지, 환각 방어(시스템 프롬프트 grounding), 핵심 서비스 무영향(읽기 전용 Tool)
- 비용인식: `chat_interaction_log`에 토큰/지연/에러 기록, 모델별·세션별 대시보드 API
- SSE+멀티턴: `chat_session`/`chat_message` 엔티티, 최근 10개 메시지 윈도우, 30분 세션 만료, `SseEmitter` 스트리밍
- 참고: `docs/dev/ai/buyer-chatbot/design.md`

## 태스크

- [x] `ChatSession`, `ChatMessage`, `ChatRole`, `ChatInteractionLog`, `ChatErrorType` 엔티티
- [x] `ChatSessionRepository`, `ChatMessageRepository`, `ChatInteractionLogRepository`, `ProductRepository.findTop10ByNameContainingIgnoreCase`
- [x] `ChatbotTools`(Tool 2개), `ChatLogRecorder`(REQUIRES_NEW), `BuyerChatService`(스트리밍+폴백+대시보드)
- [x] `BuyerChatController` — 4개 엔드포인트
- [x] `ErrorCode`에 `CHAT_SESSION_NOT_FOUND` 추가
- [x] 목 기반 테스트(Tool 단위, 세션 생애주기, 스트리밍/폴백/윈도잉, 컨트롤러 권한)
- [x] 실제 OpenAI 호출로 Tool Calling·환각방어·멀티턴 검증(`docs/logs/ai/buyer-chatbot/001-buyer-chatbot.md`)
- [x] `docs/api/chat.md` 신규 작성

## 평가(통과) 기준

- 자연어 질문에 실제 백엔드 데이터(상품/내 참여 현황)로 답변(Tool Calling 실호출 확인)
- Tool로 확인 안 되는 정보·서비스 무관 질문에는 거절/모른다고 답변(환각 방어)
- LLM 타임아웃/API장애 시 SSE로 자연어 폴백 안내, `chat_interaction_log`에 실패 기록
- 챗봇 실패가 핵심 서비스(상품 등록 등)에 영향 없음
- 동일 세션에서 이전 턴 맥락을 유지(멀티턴), 타 구매자 세션 접근 시 403
- `./gradlew build` 전체 통과(회귀 없음)
