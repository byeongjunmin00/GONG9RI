# 001-product-suggestion — AI 상품등록 도우미 (로그)

## Attempt 1 — 2026-08-11 ✅ PASS (인프라 이슈 포함)

- 시도: Spring AI 의존성 추가(처음엔 1.0.1) → `@SpringBootTest` 컨텍스트 로딩 실패(`ClassNotFoundException: RestClientAutoConfiguration`, Boot 4.1.0에서 클래스 경로 이동). **2.0.0으로 교체 후 정상 로딩 확인.** `PromptTemplate`/`AiSuggestionLog` 엔티티, 리포지토리, DTO, `AiProductSuggestionService`, `ProductAiController`, `PromptTemplateSeeder` 구현.
- 목 기반 테스트(`AiProductSuggestionServiceTest`, `@MockitoBean ChatClient.Builder`) 작성 중 트랜잭션 롤백 버그 발견: `suggest()`가 실패를 잡아 `AiSuggestionLog`를 저장하고 예외를 다시 던지는데, 같은 트랜잭션 안에서 저장했더니 재던진 예외가 트랜잭션을 롤백 대상으로 표시해서 방금 저장한 실패 로그가 사라짐(`expected: <1> but was: <0>`). `AiSuggestionLogRecorder`(별도 빈, `REQUIRES_NEW`)로 분리해서 해결. `NotificationService`에서 팀원이 이미 겪은 것과 같은 종류의 버그.
- **로컬 `.env` 로딩 인프라 문제**: `spring-dotenv` 라이브러리가 이 Boot 4.1.0 조합에서 실제로 안 먹힘(직접 `export`한 환경변수는 정상, 라이브러리 경유만 401). `spring-dotenv`가 `spring.factories`로 등록하는 `SpringApplicationRunListener`가 이 버전에서 발동을 안 하는 게 원인으로 추정 — 라이브러리 대신 커스텀 `DotenvEnvironmentPostProcessor` 직접 작성.
  - 1차: `org.springframework.boot.env.EnvironmentPostProcessor`(옛 패키지) 구현 + `META-INF/spring/*.imports` 등록 → 여전히 401.
  - 2차: `javap`/`unzip -l`로 실제 `spring-boot-4.1.0.jar`를 까봐서 새 패키지(`org.springframework.boot.EnvironmentPostProcessor`, 메서드 시그니처 동일)로 옮김, `.imports` 파일명도 새 패키지에 맞게 수정 → **그래도 진단용 `System.err.println`이 한 번도 안 찍힘** → `postProcessEnvironment` 자체가 호출 안 되는 상태.
  - 3차(원인 확정): `spring-boot-4.1.0.jar` 내부 `META-INF/spring.factories`를 직접 열어보니, Boot 자신의 내장 `EnvironmentPostProcessor`(`ConfigDataEnvironmentPostProcessor` 등)도 전부 `spring.factories`(`org.springframework.boot.EnvironmentPostProcessor=...` 키)로 등록돼 있었음 — **`.imports` 방식은 이 SPI에 해당 안 됨**(다른 최신 SPI인 `AutoConfiguration.imports`와 혼동했던 것). `.imports` 파일을 지우고 `META-INF/spring.factories`로 재등록하니 진단용 로그가 즉시 찍히고, `.env` 값이 정상적으로 `Environment`에 얹힘을 확인.
- 첫 실제 OpenAI 호출(수동 `export` 시절, `.env` 자동 로딩 확정 전): FOOD 카테고리, 입력 "제주 감귤 5kg인데 하나에 만원쯤 받고싶어요" → `{"suggestedName":"제주 감귤 5kg","suggestedDescription":"신선한 제주 감귤 5kg, 유통기한은 2주입니다. 냉장 보관을 권장합니다.","suggestedBasePrice":10000,"suggestedMaxParticipants":10}`, `promptTokens=455, completionTokens=77, totalTokens=532, latencyMs=2822`. 가격(만원→10000)·중량(5kg) 정확히 반영, 신선식품 특유의 유통기한/보관방법도 포함됨 — 프롬프트 설계 의도대로 동작.
- 결과: `./gradlew build` 전체 108케이스(기존 104 + 신규 4) 통과, 회귀 없음. 진단용 `System.err.println`은 등록 방식 수정을 검증한 뒤 제거.

## Attempt 2 — 2026-08-11 ✅ PASS (`.env` 자동 로딩 검증 + GENERAL 카테고리 + 품질 이슈 발견)

- 목적: 등록 방식을 고친 `DotenvEnvironmentPostProcessor`로 **수동 `export` 없이** 실제 OpenAI 호출이 되는지 end-to-end로 확인. 동시에 아직 실제 호출로 검증 안 한 GENERAL 카테고리도 테스트.
- FOOD, 입력 "강원도 감자 10kg인데 하나에 만오천원쯤 받고싶어요" → `{"suggestedName":"강원도 신선 감자 10kg","suggestedDescription":"신선한 강원도 감자 10kg, 유통기한은 2주입니다. 서늘한 곳에 보관하세요.","suggestedBasePrice":15000,"suggestedMaxParticipants":10}`, `promptTokens=458, completionTokens=79, totalTokens=537, latencyMs=3442`. **`.env` 자동 로딩만으로 정상 동작 확인** — 인프라 문제 완전히 해결됨.
- GENERAL, 입력 "핸드메이드 도자기 머그컵인데 하나에 이만오천원 정도로 팔고싶어요" → `{"suggestedName":"핸드메이드 도자기 머그컵","suggestedDescription":"핸드메이드 도자기로 제작된 머그컵입니다...","suggestedBasePrice":15000,"suggestedMaxParticipants":20}`, `promptTokens=458, completionTokens=95, totalTokens=553, latencyMs=1856`. GENERAL 프롬프트가 의도대로 소재/사이즈/용도를 설명에 포함시킴(FOOD의 유통기한/보관방법 대신) — 카테고리 분기가 실제 출력 품질에서도 차이를 만든다는 걸 확인.
- **품질 이슈 발견**: 입력에 "이만오천원"(=25000원)이라고 명시했는데 `suggestedBasePrice`가 `15000`으로 잘못 나옴. 당시 프롬프트엔 "판매자가 언급한 가격이 있으면 그걸 원 단위 정수로"라는 지시만 있었고, 한글 단위(만/천) 표기를 어떻게 환산하라는 구체적 지시가 없었음 — 모델이 "이만오천"의 앞자리("이")만 보고 넘겨짚은 것으로 추정.
- 1차 개선 시도: 프롬프트에 `"만원", "이만오천원"처럼 한글 단위(만/천)로 쓴 금액도 정확히 숫자로 환산해라(예: 이만오천원=25000)` 문구 추가(`version=2`) → **같은 입력으로 재호출해도 여전히 15000** (`promptTokens=505, completionTokens=84, totalTokens=589, latencyMs=2069`, `id=27`). 단순 예시 하나 붙이는 걸로는 부족함을 확인.

## Attempt 3 — 2026-08-11 ✅ PASS (가격 파싱 프롬프트 재개선)

- 목적: Attempt 2에서 발견한 한글 단위 가격 파싱 오류를 실제로 고치기.
- 변경: 지시문을 구체적 산술 예시를 포함한 형태로 강화 — `한글 단위(만/천)로 쓴 금액은 자릿수를 나눠서 정확히 환산해라. 만=10000, 천=1000이므로 "이만오천원"은 2*10000+5*1000=25000, "만오천원"은 10000+5000=15000이다. 절대 앞자리 숫자만 보고 넘겨짚지 마라.`(`version=3`, FOOD/GENERAL 둘 다 반영, `PromptTemplateSeeder`의 소스 코드에도 동일하게 반영).
- 동일 입력("핸드메이드 도자기 머그컵인데 하나에 이만오천원 정도로 팔고싶어요")으로 재호출 → `{"suggestedName":"핸드메이드 도자기 머그컵","suggestedDescription":"핸드메이드 도자기로 제작된 머그컵입니다. 사이즈는 일반적인 300ml로, 커피나 차를 즐기기에 적합합니다.","suggestedBasePrice":25000,"suggestedMaxParticipants":20}` — **정확히 25000으로 환산됨.** `promptTokens=555, completionTokens=86, totalTokens=641, latencyMs=1748`.
- 결론: 프롬프트에 "정확히 환산해라"는 추상적 지시만으로는 LLM의 한글 단위 숫자 파싱 오류를 못 고쳤고, **구체적인 산술 전개(자릿수 분해 + 계산 과정)를 예시로 보여줘야** 실제로 개선됨 — 프롬프트 엔지니어링에서 "무엇을 하라"보다 "어떻게 하는지 보여주기"가 효과적이었던 실측 사례.
- 토큰 변화: v1→v3 지시문이 길어지면서 `promptTokens`가 455~458 → 555로 약 100 증가(가격 파싱 지시문 길이 증가분), `completionTokens`는 77~95로 큰 변화 없음(응답 형식 자체는 그대로라서). 응답 품질(가격 정확도) 개선 대비 토큰 증가폭은 크지 않다고 판단해 반영 확정.
- 다음 개선 방향(스코프 밖, 참고용): 숫자 표기가 더 다양해지면(예: "2만5천원", "25,000원" 등 혼합 표기) 프롬프트만으로 100% 신뢰하기보다, 응답값을 후처리로 한 번 더 검증하는 방식(정규식으로 입력에서 가격 패턴을 뽑아 대조)도 고려할 수 있음 — 이번 스코프에서는 프롬프트만으로 해결.

## 참고 — API 크레딧 사용

이번 로그의 실제 OpenAI 호출은 총 6회(성공 4회, 등록 방식 버그로 인한 401 실패 2회 — 실패는 과금 없음). 팀원 API 키를 빌려 쓰는 만큼 실제 호출을 필요한 검증(카테고리별 1회 이상 + 발견한 버그의 재현·수정 확인)으로만 제한함.
