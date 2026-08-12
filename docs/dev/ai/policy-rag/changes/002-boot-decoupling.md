# 정책 RAG 색인의 부팅 필수 관문 제거 (배포 502 장애 후속조치)

대상: ai/policy-rag                 <!-- 완료 시 이 기능의 changes/로 이동 -->
담당: 전용운

## 배경 / 요구

`feat(ai/policy-rag): 정책 문서 RAG 검색 인터페이스 추가` 배포 직후 프로덕션(Railway) 전체가 502로 다운됐다. 원인은 다음과 같다:

- `PolicyDocumentIndexer`(`src/main/java/com/gong9ri/gong9ri/config/PolicyDocumentIndexer.java`)가 `ApplicationRunner`로 구현돼 있어, `policy-rag.indexing.enabled`가 `true`(운영 기본값)인 한 **앱 기동 도중 무조건 OpenAI 임베딩 API를 1회 호출**한다.
- Railway 환경변수에 `OPENAI_API_KEY`가 없어 이 호출이 401로 실패했다.
- 이 예외가 `ApplicationRunner` 실행 중 발생하면서 Spring `ApplicationContext` 초기화 자체가 실패해, Tomcat조차 뜨지 못하고 **로그인·상품·결제 등 이 기능과 전혀 무관한 나머지 서비스 전체**가 함께 죽었다.
- 현재는 사용자가 Railway에 `OPENAI_API_KEY`를 직접 추가해 임시로 복구된 상태다. 하지만 근본 구조(색인 실패 = 앱 기동 실패)는 그대로 남아 있어, 키가 있어도 OpenAI 쪽 장애·레이트리밋 등 다른 이유로 같은 호출이 실패하면 동일한 전체 다운이 재발할 수 있다.
- 이 RAG 기능은 실제로는 `BuyerChatService`(구매자 챗봇)에 이미 주입/연결돼 있어 매 턴 호출된다(`BuyerChatService.java`의 `retrieveRagContext()`) — 처음엔 `docs/dev/ai/policy-rag/design.md`의 "후속 작업"에 "아직 연결 안 됨"이라고 적혀 있어 그렇게 파악했으나, 실제 코드 확인 결과 틀린 서술이었다(해당 문서는 이후 별도 감사 작업에서 정정함). 즉 이건 아무도 안 쓰는 기능이 아니라 **매 턴 실사용되는 기능**이고, 그런 기능 하나의 초기화 실패가 무관한 전체 서비스의 가용성을 인질로 잡는 구조였다는 게 이번 사고의 핵심 문제다 — 처음 판단보다 사고의 심각도가 더 높다.

이 작업의 목표: **색인/임베딩 호출 실패가 앱 부팅(그리고 이 기능과 무관한 나머지 서비스) 자체를 막지 않도록 한다.** 색인 호출을 부팅의 "필수 관문"에서 빼고, 실제로 이 기능이 쓰이는 시점(첫 검색 시점 등) 또는 최소한 부팅을 차단하지 않는 시점으로 옮기는 방향으로 접근한다.

## 설계

- **바꾸는 것**: 색인(임베딩 API 호출)이 **언제/어떤 조건에서 실행되고, 그 실패가 어디까지 전파되는가**만 바꾼다. 구체적으로 어떤 메커니즘(예: 실행 시점을 부팅 완료 이후로 옮기는 방식, 실패를 격리해 전파하지 않는 방식, 최초 사용 시점까지 지연시키는 방식 등)을 쓸지는 Generate 단계에서 정한다 — 이 계획은 방식을 지정하지 않는다.
- **바꾸지 않는 것**: 색인의 "내용"(어떤 문서를 어떤 단위로 청크화하는지, 패러프레이즈 보강 여부, `VectorStore`/`EmbeddingModel` 선택 등)은 이번 작업 범위가 아니다. `PolicyRagService`/`PolicyRagServiceImpl`의 계약(`findRelevantSnippets`)도 바꾸지 않는다.
- **유지해야 할 기존 보장**: `policy-rag.indexing.enabled` 토글은 테스트 프로파일(`src/test/resources/application.yaml`)에서 `false`로 꺼져 있어 `@SpringBootTest` 컨텍스트 로딩 시 실제 OpenAI 호출이 나가지 않게 막고 있다 — 이번 변경으로 이 보장이 깨지면 안 된다.
- **영향 계층**: `config`(`PolicyDocumentIndexer`, 관련 빈 등록/생명주기) 위주. 색인 트리거 지점을 옮기는 방식에 따라 `service`(`PolicyRagService`/`PolicyRagServiceImpl`) 쪽에도 손이 갈 수 있으나, 정확한 배치는 Generate가 결정한다.
- **관찰 가능성 리스크**: 색인이 부팅을 막지 않게 되면, 색인이 실패해도 앱은 정상적으로 뜬다는 것이 곧 "실패가 아무도 모르게 조용히 묻힌다"로 이어지면 안 된다 — 실패 시 운영자가 인지할 수 있는 흔적(로그 등)이 남아야 한다는 요구사항만 명시한다. 구체적으로 어떤 로그 레벨/형식/알림으로 남길지는 Generate가 정한다.
- **design.md 반영**: 색인 실행 시점·실패 격리 방식이 바뀌었다는 사실과 이번 사고 경위를 `docs/dev/ai/policy-rag/design.md`에 반영한다(`BuyerChatService` 연결 상태 자체는 이미 실제와 맞게 정정돼 있음 — 별도 문서 감사 작업에서 처리됨, 이 항목에서 다시 손댈 필요 없음).
- 새 REST 엔드포인트나 DB 테이블 변경 없음 → `docs/api/`, `docs/db/` 신규 작성 불필요.

## 태스크

- [ ] `PolicyDocumentIndexer`의 실행 시점 또는 실패 전파 경로를 변경해, 색인 실패(OpenAI 키 부재·API 장애 등)가 `ApplicationContext` 초기화 실패로 이어지지 않게 한다 (구체 메커니즘은 Generate 결정).
- [ ] `policy-rag.indexing.enabled` 토글과 테스트 프로파일(`enabled: false`)의 기존 보장(테스트 시 실호출 없음)이 이번 변경 이후에도 유지되는지 확인한다.
- [ ] 색인 실패 시 운영자가 인지 가능한 로그가 남는지 확인한다.
- [ ] `docs/dev/ai/policy-rag/design.md` 갱신 — 색인 시점/실패 격리 방식 변경과 이번 사고 경위를 기술한다(`BuyerChatService` 연결 상태 서술은 이미 정정돼 있어 다시 손댈 필요 없음).
- [ ] 이번 장애·조치 경위를 `docs/logs/ai/policy-rag/`에 기록한다(`docs/logs-guide.md` 준수).

## 평가(통과) 기준

- **핵심 회귀 검증**: `OPENAI_API_KEY`가 없거나 OpenAI 임베딩 API 호출이 실패하는 환경에서도 애플리케이션이 정상 기동하고, 이 기능과 무관한 나머지 API(예: `/api/products`, 로그인 등)가 정상 응답해야 한다.
- `./gradlew test` 전체 통과 — 기존 회귀 없음, 특히 테스트 프로파일에서 색인이 여전히 비활성화되어 실제 임베딩 호출이 나가지 않는다는 기존 보장이 유지됨을 확인.
- 정상적으로 `OPENAI_API_KEY`가 유효한 환경에서는 색인이 (시점은 바뀌더라도) 결국 이루어져 기존처럼 정책 스니펫 검색이 동작함을 확인 — 시점만 바뀌었을 뿐 기능 자체가 없어지지 않았음을 검증.
- (수동/실측) `OPENAI_API_KEY`를 제거하거나 무효화한 상태로 로컬 기동(`./gradlew bootRun`) 시 앱이 정상적으로 뜨고, 색인 실패는 로그로만 남으며 다른 API가 정상 동작함을 확인.
- **(신규)** 색인이 끝나기 전에 구매자 챗봇 요청이 들어와도 에러 없이(빈 RAG 문맥으로) 정상 응답하는지 확인한다 — `PolicyRagService`가 실제로 매 턴 호출되는 실사용 인터페이스이므로, 색인 시점을 옮겼을 때 생기는 경합 상황에서의 동작도 통과 기준에 포함한다.

## 리스크 / 전제

- 이 기능은 이미 `BuyerChatService`에 연결돼 매 턴 실사용되고 있다(처음엔 미연결로 파악했으나 코드 확인 후 정정) — 즉 "아무도 안 쓰는 기능"이 아니라 **활발히 쓰이는 기능** 하나의 초기화 실패가 전체 서비스 가용성을 결정짓는 구조였다는 것이 이번 장애의 근본 원인이다.
- 운영 환경(Railway)에 `OPENAI_API_KEY`가 항상 존재한다는 보장이 없다(이번 사고에서 실제로 없었음) — 이 작업은 "키가 반드시 있어야 한다"를 강제하는 대신, 키가 없거나 호출이 실패해도 무관한 서비스가 죽지 않는 방향으로 리스크를 낮춘다. 운영 환경변수 자체를 관리하는 조치(키를 항상 설정해두는 것 등)는 이 계획의 범위 밖이다.
- **색인 실행 시점을 옮기면, 색인이 아직 끝나기 전에 검색이 호출되는 경합 상황이 실제로 지금 존재하는 리스크다**(이 인터페이스가 이미 `BuyerChatService`에 연동돼 매 턴 호출되고 있으므로, 더 이상 이론상 미래 얘기가 아니다). `PolicyRagServiceImpl`/`BuyerChatService.retrieveRagContext()`가 예외를 삼키게 돼 있어 크래시로는 안 이어지고 "이번엔 RAG 문맥 없이 답변" 정도로 완화될 가능성이 코드상 보이나, 실측 확인은 안 됐다 — 이 완화가 실제로 그렇게 동작하는지 확인하고 필요하면 보완하는 것은 Generate/Evaluate의 몫이며, 해결 방식은 이 계획에서 정하지 않는다.
- 되돌리기 어려운 DB 변경이나 실행 설정 변경은 없다. 다만 이 변경은 프로덕션 장애의 직접적인 후속조치이므로, 배포 시점·방식은 사용자와 확인이 필요할 수 있다.

## 문서 산출물

- 이 계획 문서: `docs/dev/ongoing/policy-rag-boot-decoupling.md`
- 신규 API/DB 명세 없음.
- Evaluate 통과 시 `docs/dev/ai/policy-rag/design.md` 갱신 + 이 ongoing 문서를 `docs/dev/ai/policy-rag/changes/002-*.md`로 채번 이동.
