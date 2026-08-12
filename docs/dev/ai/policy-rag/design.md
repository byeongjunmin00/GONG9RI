# 정책 문서 RAG 검색 (ai/policy-rag) — Design

## 개요

구매자가 "환불은 언제 되나요?" 같은 정책 관련 질문을 챗봇에 했을 때 답할 근거 문맥을 가져오는 RAG(검색) 컴포넌트다. 정책 문서(`docs/policy/refund-trigger.md`, `team-success-criteria.md`)를 Spring AI 내장 `SimpleVectorStore`(인메모리)에 임베딩해두고, 자연어 질의를 넣으면 유사도 상위 K개 스니펫을 반환한다.

발제 AI 도전과제 "RAG"에 대응하며, 발제 AI 필수2·3·도전(Tool Calling/장애격리·비용인식/SSE)을 담당한 `ai/buyer-chatbot`(민병준)과 역할을 분담한다 — 이 기능은 검색을 담당하고, `BuyerChatService`가 이 인터페이스를 생성자 주입받아 RAG+Tool Calling을 결합해 실제로 사용한다(구현 완료, `docs/dev/ai/buyer-chatbot/design.md`의 "RAG 결합" 섹션 참고).

**중요한 설계 제약**: 이 컴포넌트는 "질문과 무관한 내용을 걸러낸다"는 걸 보장하지 않는다. 유사도 threshold로 관련/무관을 가르는 방식은 실측 결과 이 코퍼스 규모(정책 문서 2개, 6청크)에서 신뢰할 수 없다고 확인됐다(`docs/logs/ai/policy-rag/001-policy-rag.md` Attempt 1~2) — 항상 상위 K개를 그대로 반환하며, 반환된 문맥이 질문과 무관할 수 있다는 전제하에 최종 관련성 판단은 **호출하는 쪽(챗봇의 시스템 프롬프트)**의 책임이다.

## API / 인터페이스

REST 엔드포인트는 없다 — 같은 스프링 컨텍스트 안에서 생성자 주입으로만 연결하는 내부 Java 인터페이스다.

```java
public interface PolicyRagService {
    List<String> findRelevantSnippets(String query);
}
```

- 입력: 사용자의 자연어 질의 원문
- 출력: 유사도 상위 K개(`TOP_K=3`) 정책 스니펫 텍스트. **빈 리스트가 아니라면 항상 관련 있다고 가정하면 안 된다.**
- 실제 소비(구현 완료): `BuyerChatService.streamChat()`이 매 턴 `retrieveRagContext(request.content())`를 통해 `findRelevantSnippets()`를 호출하고, 그 결과를 시스템 프롬프트 뒤에 덧붙이면서 "제공된 문맥이 질문과 무관하면 참고하지 말고 무시하라"는 지시를 함께 준다(`BuyerChatService.java`의 `retrieveRagContext()`/`buildSystemPrompt()`). 검색 자체가 실패해도(임베딩 API 장애 등) 챗봇 턴 전체를 실패시키지 않고 컨텍스트 없이 진행한다.

## 데이터 모델

새 DB 테이블 없음 — `VectorStore`는 인메모리(`SimpleVectorStore`)이고 앱 재시작마다 재색인한다(정책 문서가 2개뿐이라 부담 없음).

- **색인 대상**: `src/main/resources/policy/{refund-trigger,team-success-criteria}.md` — `docs/policy/`의 같은 이름 파일을 그대로 복사한 클래스패스 반입 사본. `caching.md`(서버 내부 성능 정책)는 의도적으로 제외했다(고객 질문과 무관 + 내부 구현 용어 노출 위험).
- **원본과 반입 사본이 어긋날 수 있는 리스크**: `docs/policy/*.md`가 수정돼도 이 사본은 자동 갱신되지 않는다 — 정책 문서를 고칠 때는 이 사본도 함께 수동으로 갱신해야 한다.
- **청크 단위**: 마크다운 `## ` 섹션(규칙 / 근거·배경 / 적용 대상) 단위. 파일 전체를 한 덩어리로 넣는 것보다 관련 없는 배경 설명이 답변에 섞일 여지가 적다.
- **패러프레이즈 보강**: 각 청크 텍스트 끝에 그 문서 주제로 자주 쓰일 법한 자연스러운 질문 예시("제 돈은 언제 돌려받을 수 있나요?" 등, `PolicyDocumentIndexer.EXAMPLE_QUESTIONS_BY_PATH`)를 "## 이런 질문에도 해당" 블록으로 덧붙여 함께 임베딩한다. **원본·반입 사본 파일 자체는 건드리지 않고, 색인 컴포넌트가 임베딩 직전에만 코드로 덧붙인다.** 미리 예상한 흔한 표현만 보완하는 경량 완화책이며, 예상 못 한 표현은 여전히 놓칠 수 있다(그 경우의 최종 안전망은 챗봇 프롬프트).
- **출처표시용 "표시용 출처명"**: 청크 맨 앞의 `# 문서 제목`(예: "공구팀 실패(미성사) 및 환불 트리거")은 개발팀 내부 문서 관리용 이름이라 "미성사"·"트리거" 같은 표현을 챗봇이 그대로 구매자에게 인용하면 어색하다(`ai/buyer-chatbot`이 RAG 출처표시 기능을 붙이면서 실제로 이 문제를 겪음, `docs/logs/ai/buyer-chatbot/001-buyer-chatbot.md` Attempt 4). 그래서 내부 제목 줄은 그대로 두고(임베딩 문맥용), `PolicyDocumentIndexer.DISPLAY_SOURCE_NAME_BY_PATH`에 고객 응대용 이름("환불 정책", "공구 성사 기준")을 매핑해 "표시용 출처명: {이름}" 줄로 함께 임베딩한다. 챗봇의 출처표시 지시는 이 줄만 인용하도록 만들어졌다.

## 규칙 / 검증

- 관련 정책: `docs/policy/refund-trigger.md`, `docs/policy/team-success-criteria.md`.
- 임베딩: `spring-ai-starter-model-openai`가 자동구성하는 기본 `EmbeddingModel`(`text-embedding-ada-002`, 명시 설정 없음)을 그대로 사용. 벡터스토어: `spring-ai-vector-store`의 `SimpleVectorStore`(BOM 2.0.0이 버전 관리, `spring-ai-starter-model-openai`엔 포함 안 돼 있어 별도 추가 필요했음).
- **색인은 기동 시 1회**, `PolicyDocumentIndexer`(`ApplicationRunner`)가 담당. **`policy-rag.indexing.enabled` 토글**(운영 기본 `true`, 테스트 프로파일에서는 `false`)로 실행 여부를 제어한다 — 이게 없으면 `@SpringBootTest` 컨텍스트 로딩마다 실제 OpenAI 임베딩 API를 더미 키로 호출해 401로 기존 테스트 전체(130여 개)가 깨진다. `cache: type: simple`(테스트 프로파일에서 Redis 대신 인메모리 캐시 쓰는 것)과 같은 성격의 패턴이다.
- **threshold 필터링을 포기한 경위**(가장 중요한 설계 결정): 유사도 threshold로 관련/무관을 가르는 접근을 2차례 실측했으나(기본 임베딩 모델, `text-embedding-3-small`로 교체 둘 다) 이 코퍼스 규모에서 안정적으로 작동하지 않았다 — 정책 문서 어휘를 안 쓴 자연스러운 패러프레이즈("제 돈은 언제 돌려받을 수 있나요?")의 점수가 완전 무관한 질문보다 낮게 나오는 경우가 실제로 있었다. 상세 실측 근거는 `docs/logs/ai/policy-rag/001-policy-rag.md` Attempt 1~2 참고.
- **`TOP_K=3`**은 실측 근거 없는 초기값이다(정책 문서가 6개 청크뿐이라 크게 잡을 필요 없음 — `BuyerChatService`의 `LLM_TIMEOUT=15초`와 같은 성격, 실사용 데이터로 재검토 여지 있음).

## 관련 코드 위치

- `service/{PolicyRagService,PolicyRagServiceImpl}.java`
- `config/{PolicyRagVectorStoreConfig,PolicyDocumentIndexer}.java`
- `src/main/resources/policy/{refund-trigger,team-success-criteria}.md` — 정책 문서 반입 사본
- `build.gradle` — `spring-ai-vector-store` 의존성 추가(BOM이 버전 관리)
- `src/main/resources/application.yaml`, `src/test/resources/application.yaml` — `policy-rag.indexing.enabled` 토글
- 테스트: `service/PolicyRagServiceImplTest.java` — `@MockitoBean VectorStore`로 실제 임베딩 호출 없이 검색 결과 매핑·`SearchRequest` 구성(query/topK/`SIMILARITY_THRESHOLD_ACCEPT_ALL`)을 검증(3케이스). `config/PolicyDocumentIndexerTest.java` — 스프링 컨텍스트 없는 순수 단위 테스트로 청크 텍스트에 내부 제목과 표시용 출처명이 함께 포함되는지 검증. 실제 임베딩 API로 색인·검색·패러프레이즈 개선·출처표시 여부를 확인한 기록은 `docs/logs/ai/policy-rag/001-policy-rag.md`(자동 테스트에는 실제 호출을 넣지 않음).

## 후속 작업 — 완료됨

이 섹션은 원래 "`BuyerChatService`에 미연결" 상태를 전제로 남겨둔 후속 작업 목록이었으나, 두 항목 모두 이미 완료됐다:

- `BuyerChatService`에 이 인터페이스가 실제로 주입돼 RAG+Tool Calling이 결합됐다(`BuyerChatService.java:76,155,257`).
- 챗봇 시스템 프롬프트에 "제공된 정책 문맥이 질문과 무관하면 무시하라"는 지시가 추가됐다(`BuyerChatService.java:269`, `buildSystemPrompt()`).

프로덕션 502 장애(2026-08-12) 후속조치로 색인 시점을 부팅 필수 관문에서 분리하는 별도 작업이 `docs/dev/ongoing/policy-rag-boot-decoupling.md`에서 진행 중이다 — 색인 실패가 이제는 이 이미 연결된 실사용 경로(구매자 챗봇)까지 포함해 전체 서비스를 막지 않게 하는 것이 목표다.
