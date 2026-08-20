# AI 상품등록 도우미 (ai/product-suggestion) — Design

## 개요

판매자(SELLER)가 상품을 대충 설명하는 텍스트를 입력하면, LLM(OpenAI `gpt-4o-mini`)이 상품명·설명·기본가·최대인원을 **구조화된 형태**로 제안한다. 발제 AI 필수 항목 중 "구조화 출력 + 프롬프트 엔지니어링"에 대응한다.

**AI는 상품을 직접 등록하지 않는다** — 제안만 반환하고, 판매자가 검토·수정한 뒤 기존 `POST /api/products`로 직접 등록해야 한다("AI 결과물을 비판적으로 검토" 원칙 — 사람이 최종 확인 없이 AI 출력이 그대로 DB에 반영되는 경로를 만들지 않았다).

## API / 인터페이스

- `POST /api/seller/products/ai-suggest` — 상세: `docs/api/product.md`

## 데이터 모델

- `prompt_template`(category, content, version, updatedAt) — 프롬프트를 자바 코드/클래스패스 리소스가 아니라 **DB에 저장**한다. 수정은 `UPDATE prompt_template SET content=... WHERE category=...` 한 줄로 가능(재배포 불필요). `category`는 유니크 제약(`uk_category`).
- `ai_suggestion_log`(seller, category, inputText, suggested*, prompt/completion/totalTokens, latencyMs, success, errorMessage, createdAt) — 모든 호출(성공/실패)을 기록. 비용 인식(토큰 사용량 추적)과 실패 원인 추적 목적.

## 프롬프트 템플릿 설계

- **카테고리 2개로 분기**: `FOOD`(신선식품 — 유통기한·보관방법·중량 강조) / `GENERAL`(그 외 — 소재·사이즈·용도 강조). 판매자가 요청 시 카테고리를 선택해서 보낸다.
- 서버 기동 시 `PromptTemplateSeeder`(`CommandLineRunner`)가 두 카테고리 행이 없으면만 시드 삽입 — **있으면 절대 덮어쓰지 않는다**(DB에서 직접 수정한 내용을 기동할 때마다 지워버리면 "재배포 없이 수정 가능" 요구사항이 무의미해짐).
- **모델**: `gpt-4o-mini` — 저렴하고(비용 인식 요구사항과 맞음) JSON 구조화 출력을 공식 지원, Spring AI 문서·예제가 풍부.
- **`temperature=0.3`**: 구조화된 일관된 출력이 목적이라 창의성보다 일관성 우선 — 낮게 설정.
- **`max_tokens=500`**: 상품명·설명·가격 몇 개 필드짜리 짧은 JSON 응답이라 충분 — 실측 결과(`docs/logs/ai/product-suggestion/001-product-suggestion.md`) `completionTokens`가 77~95 수준으로 여유 있게 안 남음을 확인.
- **구조화 출력**: Spring AI `BeanOutputConverter<ProductAiSuggestion>` — `.getFormat()`으로 만든 JSON 스키마 지시문을 프롬프트 끝에 붙이고, LLM 원문 응답을 `.convert(rawContent)`로 자바 record에 직접 파싱. "LLM 응답을 JSON Schema로 파싱해서 받는다"는 요구사항을 이 컨버터로 충족.
- **가격 파싱 프롬프트 개선**(실측 후 반영, `docs/logs/ai/product-suggestion/001-product-suggestion.md` Attempt 2~3 참고): 판매자가 "이만오천원"처럼 한글 단위(만/천)로 가격을 쓰면 모델이 앞자리만 보고 잘못 환산하는 문제를 실제로 재현·확인함. 프롬프트에 "만=10000, 천=1000, 이만오천원=2*10000+5*1000=25000" 식 구체적 산술 예시를 넣어야 정확히 환산됨(단순히 "정확히 환산해라"는 지시만으로는 부족했음).

## 호출·저장 흐름

`AiProductSuggestionService.suggest()`:

1. 판매자(SELLER) 권한 확인 — 구매자면 `403 FORBIDDEN`
2. 카테고리로 `PromptTemplate` DB 조회(없으면 시드 데이터 누락 — `IllegalStateException`)
3. 판매자 입력 텍스트를 템플릿의 `{input}`에 채우고, `BeanOutputConverter.getFormat()` 지시문을 덧붙여 프롬프트 완성
4. Spring AI `ChatClient`로 호출(model/temperature/max_tokens 명시)
5. 구조화 응답 파싱(`BeanOutputConverter.convert`)
6. `AiSuggestionLogRecorder`(별도 빈, `REQUIRES_NEW`)를 통해 `AiSuggestionLog`에 성공/실패 기록
7. `ProductAiSuggestion` DTO로 응답

## 비용 인식 — 요청 제한 (2026-08-20 추가)

호출 한 번마다 실제 OpenAI 비용이 발생하는데, 오랫동안 방어선이 **판매자 로그인 게이트 하나뿐**이었다. `RateLimitFilter`에 규칙을 추가해 같은 클라이언트(IP)가 **1분에 5회**를 넘기면 429로 거절한다.

- **챗봇보다 한도가 낮은(10회 → 5회) 이유**: 이건 상품 하나를 등록하기 전에 초안을 받아보는 용도라 연속 호출할 일이 챗봇 대화보다 적고, 한 번의 호출이 더 긴 프롬프트(카테고리별 템플릿 + 사용자 입력)를 태워 단가가 높다.
- 인증·DB 접근보다 **앞단 필터**에서 막으므로 남용 요청이 OpenAI까지 도달하지 않는다. Redis 장애 시엔 다른 규칙과 동일하게 fail-open(통과)이다.
- **이 갭은 챗봇 rate limit(`ai/buyer-chatbot`, 2026-08-19)을 추가할 때 "여기도 같다"고 확인해놓고 그때 함께 처리하지 않아 남아 있던 것**이다. 그 사이 AI 호출 경로 두 곳 중 한 곳만 막혀 있었다.
- 검증: `AiSuggestRateLimitFilterTest` — 임계값 이내 통과 / 초과 시 429 / 접두사가 같은 이웃 엔드포인트(`POST /api/seller/products`)는 대상이 아님(과잉 차단 방지). 규칙을 제거하면 실제로 실패하는 것까지 확인했다.

## 에러 처리 / 트랜잭션 격리

- LLM 호출 실패(타임아웃/5xx 등)는 `try/catch`로 잡아 `AI_SUGGESTION_FAILED`(503)로 명확히 응답하고, `AiSuggestionLog`에도 실패로 기록(`success=false`, `errorMessage`). 완전한 다중 Fallback/서킷브레이커 설계는 이 기능 스코프 밖(AI 필수 "장애격리·비용인식" 요구사항은 두 번째 기능인 구매자 챗봇에서 정식으로 다룸) — 이 도우미는 어차피 제안만 하고 실제 상품 등록은 기존 API가 그대로 담당하므로, 실패해도 핵심 서비스(상품 등록 자체)에는 영향이 없다.
- `suggest()`는 `@Transactional(readOnly = true)`만 걸려 있다(프롬프트 템플릿 조회만 읽기 전용 트랜잭션 필요) — LLM 호출처럼 느린 외부 HTTP 호출을 트랜잭션(=DB 커넥션 점유) 안에 가두지 않기 위해서다.
- **로그 저장은 별도 빈(`AiSuggestionLogRecorder`)의 `REQUIRES_NEW` 트랜잭션**으로 분리했다 — `suggest()`가 실패를 잡아서 로그를 저장한 뒤 예외를 다시 던지는 흐름인데, 만약 로그 저장이 같은 트랜잭션 안에 있었다면 재던진 예외가 트랜잭션을 롤백 대상으로 표시해서 방금 저장한 실패 로그 자체가 사라진다(실제로 테스트로 재현·확인한 버그 — `NotificationService`에서 팀원이 이미 겪은 것과 같은 종류의 문제, `docs/dev/notification/refund-alert/design.md` 참고). 같은 클래스의 private 메서드로 분리하지 않고 **별도 스프링 빈**으로 뺀 이유도 self-invocation이 프록시를 우회해서 `@Transactional`이 조용히 무시되는 걸 피하기 위함이다.

## `.env` 로컬 개발 환경 (인프라 참고)

로컬에서 OpenAI API 키(`OPENAI_API_KEY`)를 `.env`(레포 루트, gitignore됨)로 관리한다. 처음엔 `spring-dotenv` 라이브러리를 썼는데, 이 프로젝트의 Spring Boot 4.1.0 조합에서 실제로 로딩이 안 되는 걸 발견함(직접 `export`한 OS 환경변수는 정상 작동, 라이브러리 경유만 401 Unauthorized로 실패 — 재현·확인함). 원인은 `spring-dotenv`가 `META-INF/spring.factories`로 `SpringApplicationRunListener`를 등록하는데 이게 이 Boot 버전에서 안 먹힘. 라이브러리 교체 대신 커스텀 `EnvironmentPostProcessor`(`config/DotenvEnvironmentPostProcessor.java`)로 직접 구현했다.

- **등록 방식 주의**: `EnvironmentPostProcessor`는 Boot 4.1.0에서도 여전히 `META-INF/spring.factories`(`org.springframework.boot.EnvironmentPostProcessor=...` 키)로 등록해야 한다 — `META-INF/spring/*.imports` 방식(다른 최신 SPI, 예: `AutoConfiguration.imports`에서 쓰는 방식)이 아니다. 처음에 `.imports`로 등록했다가 `postProcessEnvironment`가 아예 호출되지 않는 문제로 한참 헤맴(진단용 `System.err.println`을 넣어봐도 한 번도 안 찍힘 → 등록 파일 자체를 의심 → Boot jar를 직접 까봐서 `spring.factories`가 맞다는 걸 확인).
- `.env`가 없으면 조용히 무시된다 — 배포 환경(Railway)엔 이 파일이 없고 플랫폼이 실제 환경변수를 직접 주입하므로 이 클래스는 그 경로에서 아무 일도 하지 않는다.

## 관련 코드 위치

- `entity/{PromptCategory,PromptTemplate,AiSuggestionLog}.java`
- `repository/{PromptTemplateRepository,AiSuggestionLogRepository}.java`
- `dto/{ProductAiSuggestionRequest,ProductAiSuggestion}.java` — `ProductAiSuggestion`이 구조화 출력 대상
- `service/{AiProductSuggestionService,AiSuggestionLogRecorder}.java`
- `controller/ProductAiController.java`
- `config/{PromptTemplateSeeder,DotenvEnvironmentPostProcessor}.java`
- `common/exception/ErrorCode.java` — `AI_SUGGESTION_FAILED` 추가
- `build.gradle` — Spring AI 2.0.0 BOM(`spring-ai-starter-model-openai`) — **1.0.1은 이 프로젝트의 Spring Boot 4.1.0과 호환 안 됨**(`RestClientAutoConfiguration` 클래스 경로가 Boot 4.1.0에서 이동해서 `ClassNotFoundException` 발생, 실제로 재현·확인 후 2.0.0으로 확정)
- `src/main/resources/META-INF/spring.factories` — `DotenvEnvironmentPostProcessor` 등록
- `src/{main,test}/resources/application.yaml` — `spring.ai.openai.*` 설정(테스트는 실제 호출 없이 더미 키만 둠)
- 테스트: `service/AiProductSuggestionServiceTest.java` — `@MockitoBean ChatClient.Builder`로 실제 OpenAI 호출 없이 카테고리별 프롬프트 분기/구조화 파싱/DB 저장/실패시 에러코드+로그 저장을 검증(4케이스)
