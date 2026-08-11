# 001-policy-rag — 정책 문서 RAG 검색 인터페이스 (로그)

## Attempt 1 — 2026-08-11

- 시도: `docs/dev/ongoing/ai-policy-rag.md` 계획대로 정책 문서 RAG "검색(retrieval)까지"를 구현했다. 결정한 것들:
  - **의존성**: `spring-ai-starter-model-openai`에는 `SimpleVectorStore`/`VectorStore`가 없다(디컴파일 확인 —
    `org.springframework.ai.vectorstore.*`는 별도 아티팩트 `org.springframework.ai:spring-ai-vector-store`에
    있음, `AiProductSuggestionService` design.md의 Spring AI 버전 이슈처럼 미리 jar 내용을 까서 확인 후
    `build.gradle`에 최소 한 줄만 추가). BOM(2.0.0)이 버전을 관리하므로 버전 명시 없이 `implementation
    'org.springframework.ai:spring-ai-vector-store'`만 추가.
  - **정책 문서 반입**: `docs/policy/refund-trigger.md`, `team-success-criteria.md`를
    `src/main/resources/policy/`에 그대로 복사(클래스패스 리소스). `caching.md`는 계획대로 제외.
    원본과 사본이 어긋날 수 있는 리스크는 계획서에 이미 명시돼 있어 별도 조치 안 함(수동 동기화 필요).
  - **벡터스토어 빈**: `config/PolicyRagVectorStoreConfig` — `SimpleVectorStore.builder(embeddingModel).build()`.
    `EmbeddingModel`은 `spring-ai-starter-model-openai`가 자동구성하는 기존 빈을 그대로 재사용(추가 설정 없음).
  - **색인 컴포넌트**: `config/PolicyDocumentIndexer`(`ApplicationRunner`) — 정책 문서 2개를 마크다운
    `"## "` 섹션(규칙/근거·배경/적용 대상) 단위로 정규식 분할해 `Document`로 만들고 `vectorStore.add(...)`.
    파일 단위 대신 섹션 단위를 택한 이유: "환불은 언제 되나요?"류 질문에 "규칙" 섹션만 매칭되게 해서 관련
    없는 배경 설명이 챗봇 답변에 섞일 여지를 줄이기 위함(계획서가 파일/섹션 둘 다 허용, 섹션을 선택).
    각 청크 텍스트 앞에 `# {문서 제목}`을 붙여 섹션만 봐서는 맥락이 끊기지 않게 했다.
  - **테스트 오염 방지(가장 중요한 함정)**: `PolicyDocumentIndexer`에
    `@ConditionalOnProperty(prefix = "policy-rag.indexing", name = "enabled", havingValue = "true",
    matchIfMissing = true)`를 붙여, `src/main/resources/application.yaml`은 `policy-rag.indexing.enabled: true`
    (기본 켜짐, 실기동 시 색인), `src/test/resources/application.yaml`은 `enabled: false`로 꺼서
    `@SpringBootTest` 컨텍스트 로딩 시 이 러너 자체가 빈으로 등록되지 않게 했다(기존 `cache: type: simple`
    토글과 같은 패턴). 이렇게 안 했으면 130여 개 기존 `@SpringBootTest`가 컨텍스트 로딩마다 더미 키로 실제
    OpenAI 임베딩 API를 호출해 401로 전부 깨졌을 것 — 실제로 코드 작성 전 이 리스크를 인지하고 먼저
    토글부터 넣었다.
  - **검색 인터페이스**: `service/PolicyRagService`(인터페이스, 메서드 `List<String>
    findRelevantSnippets(String query)`) + `service/PolicyRagServiceImpl`(구현체, 생성자 주입 `VectorStore`).
    `BuyerChatService.streamChat()`의 `.system(SYSTEM_PROMPT).messages(history).user(...).tools(tools)` 조립부에
    나중에 자연스럽게 꽂히도록, 반환 타입을 "정책 스니펫 텍스트 목록"으로 단순하게 잡았다(챗봇 쪽에서
    join해서 시스템 프롬프트 뒤에 붙이거나 필요에 맞게 가공하는 건 챗봇 담당자 몫으로 남김). REST
    컨트롤러는 계획대로 만들지 않음.
  - **topK=3, similarityThreshold=0.6**은 실측 근거 없는 초기값으로 코드 주석에 명시했다(`BuyerChatService`의
    `LLM_TIMEOUT=15초`와 같은 성격 — design.md 작성 시 로컬 실호출 결과로 재검토 필요).
  - **테스트**: `service/PolicyRagServiceImplTest`(`@SpringBootTest` + `@MockitoBean VectorStore`,
    `AiProductSuggestionServiceTest`와 같은 패턴) — (1) 벡터스토어가 반환한 문서 텍스트를 순서대로
    매핑하는지, (2) 무관 질의로 벡터스토어가 빈 리스트를 주면 빈 리스트를 반환하는지, (3) 질의 문자열·
    topK·threshold를 담은 `SearchRequest`로 검색을 위임하는지 3케이스. 실제 OpenAI 임베딩 호출은 자동
    테스트에 넣지 않음(테스트에서 `VectorStore`를 목으로 완전히 대체하므로 임베딩 API 자체가 안 불림).
  - **로컬 실호출 검증 미실시(이슈)**: 계획서 평가 기준에 "실제 OpenAI 임베딩 API 최소 1회 이상 실호출로
    확인"이 있으나, 이번 Generate 세션 환경에는 로컬 MySQL이 설치돼 있지 않아(`sc query`로 확인,
    MySQL 서비스 자체가 없음) `bootRun`을 띄워 실제 색인·검색을 확인하지 못했다. `AGENTS.md`에도
    "`./gradlew bootRun` — 로컬 MySQL 가동 필요"로 명시돼 있어 이건 이 세션의 환경 한계이지 코드 문제는
    아니라고 판단, Evaluate 단계(또는 로컬 MySQL이 있는 환경)에서 `bootRun` 후 로그(`정책 문서 RAG 색인
    완료: fileCount=2, chunkCount=6`)와 실제 질의 결과를 확인해야 한다.
  - **`./gradlew test` 자체도 이 세션에서 전체 실행 불가**: 같은 이유(로컬 MySQL 없음)로 `PolicyRagServiceImplTest`
    뿐 아니라 기존 `AiProductSuggestionServiceTest` 등도 전부 `HibernateException`(Dialect 조회 실패,
    DataSource 연결 불가)으로 실패하는 걸 직접 확인했다(내가 만든 코드와 무관하게 사전에 존재하던 환경
    제약임을 교차 확인). `./gradlew compileJava`, `./gradlew compileTestJava`는 모두 `BUILD SUCCESSFUL`.
- 결과: ❌ FAIL — 계획서 평가 기준 중 "무관한 질의 또는 캐싱·서버 내부 관련 질의에는 관련 없는 내용이 섞이지 않음"을 충족하지 못함. 나머지(`./gradlew build` 전체 통과, 실제 임베딩 API 실호출, 정책 질의 → 관련 스니펫 반환)는 통과.
- 원인: `SIMILARITY_THRESHOLD = 0.6`이 사실상 아무것도 걸러내지 못한다. `policy-rag.indexing.enabled=true` + 실제 `OPENAI_API_KEY`로 로컬 MySQL/Redis(`docker compose up -d mysql redis`) 띄운 뒤, 임시 검증 테스트(`@TestPropertySource`로 실제 키·색인 활성화)로 5개 질의를 실제 호출했다. "환불은 언제 되나요?", "공구팀은 언제 성사되나요?", "공구 기한이 지나면 어떻게 되나요?" 같은 정책 관련 질의뿐 아니라, **"오늘 서울 날씨 어때?"(무관)와 "캐시 TTL이 얼마나 되나요?"(의도적으로 제외한 캐싱 주제)까지 전부 `hitCount=3`(=topK)으로 동일하게 반환**됐다 — 즉 threshold가 사실상 전체 6개 청크 중 상위 3개를 무조건 반환하는 것과 다르지 않았다. `text-embedding` 계열 모델은 문서 6개뿐인 이 작은 코퍼스에서 무관한 질의라도 코사인 유사도가 0.6을 가볍게 넘는 것으로 추정된다(짧은 한국어 문장 임베딩의 베이스라인 유사도가 생각보다 높음 — 실측으로 처음 확인).
- 증거: `docker compose up -d mysql redis`로 인프라 기동 → `./gradlew build` BUILD SUCCESSFUL(134케이스, 기존 131 + 신규 3, 회귀 없음, `build/test-results/test/*.xml` 집계로 확인) → 임시 `PolicyRagManualVerificationIT`(`@TestPropertySource`로 `policy-rag.indexing.enabled=true` + 실제 `${OPENAI_API_KEY}`) 1회 실행, 로그(`build/test-results/test/TEST-...PolicyRagManualVerificationIT.xml`)에서 `SimpleVectorStore`가 실제로 `Calling EmbeddingModel for document id=...`를 6회(청크 수만큼) 호출하고 `PolicyDocumentIndexer`가 "정책 문서 RAG 색인 완료: fileCount=2, chunkCount=6"을 로그로 남긴 것 확인. 5개 질의 전부 `hitCount=3`(무관·캐싱 질의 포함)으로 동일 — 위 원인 그대로 재현. 검증 후 이 임시 테스트 파일은 삭제함(자동 테스트로 남기지 않기로 한 계획대로).
- 다음: **같은 접근(threshold 튜닝)으로 재시도** — Plan 변경 불필요. Generate로 돌아가 실제 관련/무관 질의의 유사도 점수 차이를 로그로 찍어보고 경험적으로 threshold를 재설정하거나(현재값이 전혀 안 걸러지므로 상당히 올려야 할 수 있음), threshold 방식 자체가 이 코퍼스 규모(6청크)에서 신뢰할 수 없다면 대안(예: topK만으로 상위 N개 반환하고 필터링은 하지 않는 대신 챗봇 프롬프트에서 "관련 없으면 무시하라"고 맡기는 방식 등)도 검토 대상.

## Attempt 2 — 2026-08-11  ❌ FAIL (접근 자체 재검토 필요)

- 시도: Attempt 1의 지시대로 옵션 A(threshold 재조정)와 옵션 B(임베딩 모델 명시)를 실측 기반으로 순서대로 검증했다. 로컬 인프라(`docker compose up -d mysql redis`, 이미 기동돼 있었음)와 실제 `OPENAI_API_KEY`(`.env`)를 이용해 임시 검증 테스트(`PolicyRagManualVerificationIT`, `@SpringBootTest` + `@TestPropertySource`로 `policy-rag.indexing.enabled=true`, `spring.ai.openai.api-key=${OPENAI_API_KEY}`)를 만들어, `VectorStore.similaritySearch(topK=6, similarityThreshold=0.0)`로 6개 청크 전체에 대한 **원시(raw) 유사도 점수**(`Document.getScore()` — Spring AI 2.0.0 `Document`가 `score` 필드를 직접 가짐, `DocumentMetadata.DISTANCE`가 아니라 이 필드를 씀)를 질의별로 찍어서 비교했다.
  1. **기본 임베딩 모델 확인**: `spring-ai-openai-2.0.0.jar`를 디컴파일해 `OpenAiEmbeddingOptions`의 기본값이 `com.openai.models.embeddings.EmbeddingModel.TEXT_EMBEDDING_ADA_002`(구형 `text-embedding-ada-002`)임을 바이트코드 상수 문자열로 확인했다(설정에 명시 안 하면 이 모델이 쓰인다).
  2. **옵션 A만 실측(기본 모델 그대로, threshold 없이 원시 점수만 비교)**: 환불/성사/마감 등 정책 관련 질의는 0.70~0.80대, 무관 질의("오늘 서울 날씨 어때?")는 0.68~0.74대, 제외 대상 질의("캐시 TTL이 얼마나 되나요?")는 0.70~0.77대로 **전부 겹친다** — 날씨 질의의 최고점(0.743)이 마감 질의의 최저점(0.714)보다 높고, 캐시 질의의 최고점(0.771)이 마감 질의의 최고점(0.770)보다도 높다. Attempt 1이 발견한 문제를 그대로 재확인했고, 어떤 threshold 값도 이 둘을 갈라낼 수 없다는 게 원시 점수로 명확해졌다 — 옵션 A(같은 모델로 threshold만 조정)는 기각.
  3. **옵션 B(임베딩 모델을 `text-embedding-3-small`로 명시)**: 처음에 `src/main/resources/application.yaml`에 `spring.ai.openai.embedding.options.model: text-embedding-3-small`을 추가하고 재검증했는데 **점수가 거의 그대로**였다 — 원인을 팠더니 `src/test/resources/application.yaml`이 클래스패스에서 `src/main/resources/application.yaml`을 완전히 **가려서(shadow)** `@SpringBootTest`가 메인 설정을 아예 안 읽는다는 걸 발견했다(테스트 클래스패스에 `classpath:/application.yaml`이 둘 존재하면 첫 번째로 발견되는 것 하나만 로드됨 — Spring Boot의 단일 리소스 로딩 특성). 또한 Gradle이 테스트 워커 프로세스를 재사용해 Spring `@SpringBootTest`의 정적 컨텍스트 캐시가 별도의 `./gradlew test` 실행 사이에도 살아남는 현상도 같이 확인했다(`@DirtiesContext` 없이 `--stop`으로 데몬을 죽여도 재현). 이후 `@TestPropertySource(properties = "spring.ai.openai.embedding.options.model=text-embedding-3-small")`로 직접 주입하고 `@DirtiesContext`를 추가해 확실히 새 컨텍스트로 재검증했다.
     - **키워드가 겹치는 질의**(문서 제목과 표현이 유사)에서는 변별력이 뚜렷이 개선됐다: 환불(0.284/0.237/0.209), 성사(0.468/0.387/0.344), 마감(0.369/0.351/0.345) vs 날씨(최고 0.094), 캐시(최고 0.186) — 이 5개 질의만 보면 threshold 0.2 부근(예: 0.20)으로 완전히 갈라진다(관련 질의 top3 전부 > 0.2, 무관/제외 질의 전부 < 0.2).
     - 그런데 **자연스러운 구어체 패러프레이즈**를 추가로 넣어보니 무너졌다: "제 돈은 언제 돌려받을 수 있어요?"(환불의 패러프레이즈, "환불"이라는 단어를 안 씀)의 최고 점수가 **0.177**로, 오히려 무관 질의인 "점심 메뉴 추천해줘"(0.251), "재미있는 농담 하나 해줘"(0.218), 제외 대상 질의 "캐시 TTL이 얼마나 되나요?"(0.186)보다도 **낮다**. 반면 "구매 취소하면 돈 다시 받을 수 있나요?"(같은 환불 주제의 다른 패러프레이즈)는 0.391로 잘 잡히고, "인원이 다 차면 바로 구매 확정되는 거예요?"(성사 패러프레이즈)는 0.261로 애매하게 캐시/점심 질의 근처에 걸린다. 즉 **같은 threshold로 관련 패러프레이즈를 통과시키면서 무관 질의를 걸러내는 값이 존재하지 않는다** — 어떤 값을 골라도 "제 돈은 언제..."류 관련 질의를 놓치거나 "점심 메뉴"류 무관 질의를 통과시키는 쪽 중 하나는 반드시 깨진다(실측: threshold=0.15로는 캐시/점심/농담이 새어 들어오고, threshold=0.2로는 "제 돈은..." 관련 질의가 거의 다 걸러진다).
  4. 위 근거로 옵션 A/B 모두 이 코퍼스(정책 문서 2개, 6청크)·짧은 자연어 질의 조합에서 **안정적인 threshold를 찾지 못했다**고 판단했다. 지시대로 숫자를 억지로 맞추지 않고, `src/main/resources/application.yaml`의 임베딩 모델 설정 추가를 **되돌렸고**(diff 없음 확인), `PolicyRagServiceImpl`의 `TOP_K`/`SIMILARITY_THRESHOLD`도 Attempt 1 그대로 **손대지 않았다**(값을 바꿔서 억지로 통과시키지 않음 — `text-embedding-3-small`로 바꾸고 threshold는 그대로 0.6으로 두면 모든 점수가 0.6 미만이라 관련 질의조차 항상 빈 리스트가 되는, 지금보다 더 나쁜 회귀가 생겼을 것이라 이것도 피했다). 검증에 쓴 `PolicyRagManualVerificationIT`는 확인 후 삭제했다(자동 테스트로 남기지 않는다는 원칙).
- 결과: ❌ FAIL — 계획서 평가 기준("무관한 질의에는 관련 없는 내용이 섞이지 않음")을 여전히 충족하지 못한다. 다만 이번 시도로 "왜 안 되는지"와 "임베딩 모델을 바꿔도 왜 여전히 안 되는지"를 실측으로 명확히 규명했다. 코드는 Attempt 1 상태 그대로 유지(회귀 없음, `./gradlew build` — 기존 134케이스 전부 통과, `TEST-*.xml` 합계로 확인).
- 원인: (1) 기본 임베딩 모델(`text-embedding-ada-002`)은 6개 문서·짧은 한국어 문장 규모에서 관련/무관 질의의 코사인 유사도 분포가 거의 겹친다(0.68~0.80 범위에 다 몰림). (2) `text-embedding-3-small`로 바꾸면 점수 분포 자체는 훨씬 넓어지고(0.0~0.47) **키워드가 겹치는 질의**에 한해서는 임계값으로 잘 갈리지만, **정책 문서의 표현과 다른 어휘를 쓰는 자연스러운 패러프레이즈**(예: "환불"이라는 단어 없이 "돈을 돌려받다"라고 묻는 경우)는 오히려 무관한 질의보다도 유사도가 낮게 나올 수 있다 — 6청크뿐인 이 코퍼스에서 질의별 노이즈(우연한 어휘 중첩)가 실제 의미적 관련성보다 점수에 더 크게 작용하는 것으로 보인다. 즉 코퍼스 규모가 threshold 기반 필터링이 안정적으로 작동하기엔 너무 작다.
- 증거: 위 3단계 실측 점수 전부(원시 `Document.getScore()` 값, `TEST-com.gong9ri.gong9ri.service.PolicyRagManualVerificationIT.xml` 로그로 확인 — 검증 완료 후 파일 삭제). `./gradlew --stop` 후 `./gradlew test --tests "*PolicyRagManualVerificationIT"` 3회(기본 모델 1회, `text-embedding-3-small` `@TestPropertySource` 주입 + `@DirtiesContext` 2회 — 질의 5개 세트, 이후 질의 7개 세트) 전부 실제 OpenAI 임베딩 API 호출로 확인(`SimpleVectorStore : Calling EmbeddingModel for document id=...` 6회/색인 1회당). 최종 `./gradlew build` BUILD SUCCESSFUL(134케이스, `build/test-results/test/*.xml` 합계 재확인).
- 다음: **같은 접근(threshold 숫자 조정)으로는 더 이상 재시도하지 않는 게 맞다고 판단** — 이 코퍼스 규모(6청크)에서는 임베딩 모델을 바꿔도 관련 패러프레이즈와 무관 질의의 점수 분포가 안정적으로 분리되지 않는다는 게 두 모델 다 확인됐다. **Plan 재검토가 필요할 수 있다.** 검토해볼 대안(코드로 강행하지 않고 제안만 남김):
  1. **threshold 필터링을 포기하고 topK만 사용** — 항상 상위 K개 스니펫을 반환하되, 무관 질의 처리는 챗봇(`BuyerChatService`) 쪽 시스템 프롬프트에서 "제공된 정책 문맥이 질문과 무관하면 참고하지 말고 무시하라"고 LLM에 위임하는 방식. 이러면 RAG 검색 인터페이스 자체는 필터링 책임을 안 지고, 최종 답변 품질 보증은 LLM의 지시 따르기 능력에 맡기게 된다 — 챗봇 담당(민병준)과 협의 필요.
  2. **코퍼스를 늘린다** — 6청크는 임베딩 기반 검색이 통계적으로 유의미하게 작동하기엔 너무 작을 수 있다. 정책 문서를 더 잘게/많이 쪼개거나(예: FAQ 형태로 여러 패러프레이즈 예시를 명시적으로 포함) 관련 질문-답변 쌍을 추가로 색인하면 분포가 개선될 가능성이 있다(단, 검증 안 함 — 가설).
  3. **하이브리드(키워드+벡터) 검색**을 고려 — 순수 코사인 유사도 대신 BM25 같은 키워드 매칭을 같이 써서 "환불"류 핵심 단어가 겹치는지도 신호로 삼는 방식. 다만 이 프로젝트 규모에 과한 인프라일 수 있음.
  이 중 어느 방향으로 갈지는 사람 승인(Plan 재검토, 휴먼 게이트)이 필요하다고 판단해 이번 Generate에서는 코드를 변경하지 않았다.

## Attempt 3 — 2026-08-11  ✅ PASS

- 시도: `docs/dev/ongoing/ai-policy-rag.md`의 재승인된 "설계 변경" 섹션(2026-08-11)대로, Attempt 2가 제안한 대안 1(threshold 필터링 포기 + 패러프레이즈 보강)을 그대로 구현했다.
  1. **`PolicyRagServiceImpl`**: `SIMILARITY_THRESHOLD` 상수를 없애고, `SearchRequest.similarityThreshold(SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL)`(라이브러리가 제공하는 0.0 상수, `spring-ai-vector-store` 디컴파일로 존재 확인)로 고정했다 — 항상 `TOP_K=3`개를 그대로 반환하고, 빈 리스트를 반환하는 "관련 없음 판정"은 더 이상 하지 않는다. 클래스 Javadoc에 왜 threshold 필터링을 포기했는지(Attempt 1~2 실측 근거, 로그 링크)와 "관련성 최종 판단은 호출하는 쪽(챗봇)의 책임"이라는 계약을 명시했다.
  2. **`PolicyRagService` 인터페이스**: Javadoc에 "반환된 스니펫이 항상 질문과 관련 있다고 가정하면 안 된다"는 계약 변경을 반영했다 — 호출자(향후 `BuyerChatService`)가 이 전제를 알고 프롬프트에서 무관 문맥을 무시하도록 지시해야 함을 명시.
  3. **`PolicyDocumentIndexer`**: 정책 문서 2개(`refund-trigger.md`, `team-success-criteria.md`) 각각에 대해 파일 단위로 자연스러운 질문 예시 목록(`EXAMPLE_QUESTIONS_BY_PATH`)을 정의하고, 섹션 청크 텍스트를 만들 때(임베딩 직전) "## 이런 질문에도 해당" 블록으로 덧붙이도록 `buildExampleQuestionsBlock()`을 추가했다. `docs/policy/*.md` 원본과 `src/main/resources/policy/*.md` 반입 사본은 전혀 건드리지 않음(코드 diff만 확인, 두 파일 모두 무변경 — `git status`로 확인). Attempt 2에서 실패했던 "제 돈은 언제 돌려받을 수 있나요?" 계열 표현을 refund-trigger 파일의 예시 목록에 반드시 포함시켰고, team-success-criteria에는 "인원이 다 차면 바로 구매 확정되는 거예요?" 계열을 포함시켰다. 한 파일의 섹션(규칙/근거·배경/적용 대상) 3개 모두에 동일한 예시를 붙이는 방식을 택했다 — 같은 파일의 섹션들은 어차피 같은 주제라 서로 대체 문맥으로 쓰여도 무방하다고 판단.
  4. **`PolicyRagServiceImplTest`**: 계약 변경에 맞춰 "무관 질의면 빈 리스트 반환"(threshold 필터링 전제) 테스트를 "벡터스토어가 빈 결과를 주면 그대로 빈 리스트를 반환한다"(순수 매핑 검증)로 바꿨다. `SearchRequest` 구성 검증 테스트도 `similarityThreshold > 0.0` 대신 `SIMILARITY_THRESHOLD_ACCEPT_ALL`로 고정됐는지 확인하도록 바꿨다.
  5. **실측 재검증**: 로컬 인프라(`docker compose ps`로 mysql·redis 둘 다 `Up ... (healthy)` 확인 — 이미 떠 있었음, 새로 안 띄움)와 실제 `.env`의 `OPENAI_API_KEY`로 임시 `PolicyRagManualVerificationIT`(`@SpringBootTest` + `@TestPropertySource(policy-rag.indexing.enabled=true, spring.ai.openai.api-key=${OPENAI_API_KEY})` + `@DirtiesContext`, Attempt 1~2와 같은 패턴)를 다시 만들어 `./gradlew test --tests "*PolicyRagManualVerificationIT"`로 1회 실행했다. 임베딩 모델은 기본값(`text-embedding-ada-002`, Attempt 2가 되돌린 대로 명시 설정 없음) 그대로 두고, 패러프레이즈 보강만으로 개선되는지를 확인하는 게 목적이었다. 검증 완료 후 이 임시 테스트 파일은 삭제했다(`git status`로 미추적 삭제 확인, 자동 테스트로 안 남김).
- 결과: ✅ PASS — 계획서의 갱신된 평가 기준을 모두 충족했다.
  - **패러프레이즈 보강 확인(가장 중요)**: "제 돈은 언제 돌려받을 수 있나요?"(Attempt 2에서 raw score 0.177로, 무관 질의보다도 낮아 완전히 놓쳤던 표현) — 이번엔 topK=3 중 **top1을 포함해 2/3**이 `refund-trigger.md` 섹션(`근거 / 배경` 0.786, `적용 대상` 0.783)으로 채워졌다. 같은 주제의 다른 패러프레이즈 "구매 취소하면 돈 다시 받을 수 있나요?"는 top3 **전부**(0.822/0.811/0.801) `refund-trigger.md`. 성사 패러프레이즈 "인원이 다 차면 바로 구매 확정되는 거예요?"도 top3 중 2/3(0.817/0.804)이 `team-success-criteria.md`.
  - **원래 되던 질의 회귀 없음**: "환불은 언제 되나요?"(top3 전부 refund-trigger, 0.831/0.824/0.803), "공구팀은 언제 성사되나요?"(top3 전부 team-success, 0.811/0.809/0.795), "공구 기한이 지나면 어떻게 되나요?"(top3 중 2/3 refund-trigger, 0.782/0.781/0.770) 모두 여전히 잘 잡힘.
  - **무관 질의도 topK개 반환**: "오늘 서울 날씨 어때?" → `hitCount=3`, 예외 없음(의도된 동작, 더 이상 실패 조건 아님).
  - `./gradlew build` 전체 BUILD SUCCESSFUL, `build/test-results/test/*.xml` 집계로 `tests=134 skipped=0 failures=0 errors=0`(기존 그대로, 회귀 없음).
  - `SimpleVectorStore : Calling EmbeddingModel for document id=...` 6회(청크 수만큼, `PolicyDocumentIndexer`가 "정책 문서 RAG 색인 완료: fileCount=2, chunkCount=6" 로그) + 질의 7개(패러프레이즈 2 + 원래 질의 3 + 성사 패러프레이즈 1 + 무관 1)마다 검색용 임베딩 호출까지 전부 실제 OpenAI API 실호출로 확인(`TEST-com.gong9ri.gong9ri.service.PolicyRagManualVerificationIT.xml`에 기록된 원시 로그로 확인, 파일은 검증 후 삭제).
- 원인(참고, 실패 아님): 왜 개선됐는지 — 한 파일의 모든 섹션 청크에 그 파일의 패러프레이즈 예시를 동일하게 붙였기 때문에, "제 돈은 언제 돌려받을 수 있나요?" 같은 질의가 refund-trigger.md의 **모든** 섹션(규칙/근거·배경/적용 대상)과 어휘적으로 더 가까워졌다. 그 결과 관련 질의는 topK=3 안에서 항상 같은 파일의 섹션이 다수(2/3 이상)를 차지하게 됐다 — 이는 "무관 질의와 절대 점수로 구분"하는 게 아니라 "질의별 topK 안에서 정답 문서가 우세하게 나오는가"라는, 이번에 바뀐 평가 기준에 맞는 개선이다. threshold 자체는 여전히 관련/무관을 가르지 못한다(무관 질의 점수도 0.70~0.75대로 여전히 겹침 — Attempt 1~2와 동일 현상, 하지만 이제 이 인터페이스의 책임이 아니므로 통과 기준에서 제외됨).
- 증거: `docker compose ps`(mysql/redis `Up ... (healthy)`) → `./gradlew --stop` → `./gradlew test --tests "*PolicyRagManualVerificationIT"` 1회(질의 7개, 원시 점수·hitCount 전부 `TEST-*.xml`에서 확인, 검증 후 파일 삭제) → `./gradlew build` BUILD SUCCESSFUL(134케이스, `build/test-results/test/*.xml` 집계로 재확인, 실패/에러 0).
- 다음: 통과. `docs/dev/ongoing/ai-policy-rag.md`의 남은 태스크 체크박스 갱신 필요(테스트 검증 완료로 업데이트). `docs/dev/ai/policy-rag/design.md` 작성은 Evaluate 단계 몫으로 남겨둠(계획대로 여기서 만들지 않음).

## Evaluate 확인 (2026-08-11)

- Generator(Attempt 3)의 `./gradlew build` 결과를 별도 세션에서 **독립적으로 재검증**했다: `./gradlew cleanTest test`로 캐시 없이 강제 재실행 → BUILD SUCCESSFUL, `build/test-results/test/*.xml` 34개 파일 집계로 `tests=134 skipped=0 failures=0 errors=0` 재확인(회귀 없음). `PolicyRagServiceImplTest`만 별도로도 `tests=3 failures=0 errors=0` 확인.
- `git status`로 임시 검증 테스트 파일이 남아있지 않음을 확인(의도한 파일만 untracked로 남아있음).
- 계획서(`docs/dev/ongoing/ai-policy-rag.md`)의 갱신된 평가 기준 5개 모두 Attempt 3 기록·재검증으로 충족 확인. **최종 판정: PASS.**
