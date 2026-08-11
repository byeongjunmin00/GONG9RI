# AI 상품등록 도우미 (ai/product-suggestion)

대상: ai/product-suggestion
담당: 민병준

## 배경 / 요구

발제 AI 필수 3개(구조화출력·프롬프트엔지니어링 / Tool Calling / 장애격리·비용인식) 중 첫 번째 구현. 팀 역할 분담: 전용운이 백엔드 도전과제(비동기 이벤트, 완료) + 프론트엔드를 맡았고, AI 필수3+도전2는 민병준이 2개 기능으로 나눠 담당 — 이 기능은 그중 **판매자용 상품 등록 도우미**(구조화출력+프롬프트엔지니어링). 나머지(Tool Calling, 장애격리·비용인식, SSE 스트리밍, RAG)는 두 번째 기능(구매자 챗봇)에서 별도로 다룬다.

LLM은 팀원이 발급받은 OpenAI API 키를 빌려 쓴다. 키는 로컬 `.env`(gitignore됨)에만 두고 값을 공유하지 않음.

## 설계

- Spring AI BOM + `spring-ai-starter-model-openai`, 모델 `gpt-4o-mini`
- 프롬프트 템플릿을 DB(`prompt_template`)에 저장, FOOD/GENERAL 2개 카테고리로 분기, 재배포 없이 수정 가능
- `BeanOutputConverter<ProductAiSuggestion>`로 구조화 출력 파싱
- 호출·결과를 `ai_suggestion_log`에 성공/실패 모두 기록(토큰 사용량·지연시간 포함)
- 참고: `docs/dev/ai/product-suggestion/design.md`

## 태스크

- [x] Spring AI 의존성 추가 및 실빌드로 버전 호환성 확인
- [x] `PromptCategory`, `PromptTemplate`, `AiSuggestionLog` 엔티티
- [x] `PromptTemplateRepository`, `AiSuggestionLogRepository`
- [x] `ProductAiSuggestionRequest`, `ProductAiSuggestion`(구조화 출력 대상) DTO
- [x] `AiProductSuggestionService`, `AiSuggestionLogRecorder`(REQUIRES_NEW 분리)
- [x] `ProductAiController` — `POST /api/seller/products/ai-suggest`
- [x] `PromptTemplateSeeder` — 카테고리별 시드(있으면 덮어쓰지 않음)
- [x] `ErrorCode`에 `AI_SUGGESTION_FAILED` 추가
- [x] `DotenvEnvironmentPostProcessor` — 로컬 `.env` 자동 로딩
- [x] 목 기반 테스트(`AiProductSuggestionServiceTest`, 4케이스)
- [x] 실제 API 호출로 최소 3회 프롬프트 개선 이력 기록(`docs/logs/ai/product-suggestion/001-product-suggestion.md`)
- [x] `docs/api/product.md`에 신규 엔드포인트 추가

## 평가(통과) 기준

- 판매자 계정으로 `POST /api/seller/products/ai-suggest` 호출 시 구조화된 제안(이름/설명/기본가/최대인원) 반환
- 구매자 계정으로 호출 시 `403 FORBIDDEN`
- LLM 호출 실패 시 `503 AI_SUGGESTION_FAILED` + `ai_suggestion_log`에 실패 기록
- 카테고리(FOOD/GENERAL)별로 실제로 다른 프롬프트가 구성됨(목 테스트로 검증)
- `./gradlew build` 전체 통과(회귀 없음)
- 실제 OpenAI 호출로 두 카테고리 모두 최소 1회씩 정상 응답·토큰 사용량 확인
