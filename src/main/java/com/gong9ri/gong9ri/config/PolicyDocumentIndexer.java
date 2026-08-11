package com.gong9ri.gong9ri.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * 정책 문서 RAG 색인 — 기동 시 1회, {@code docs/policy/refund-trigger.md}·{@code team-success-criteria.md}의
 * 사본({@code src/main/resources/policy/}, 앱이 참조 가능하도록 클래스패스로 반입)을 임베딩해 벡터스토어에
 * 넣는다. {@code caching.md}(서버 내부 성능 정책)는 의도적으로 제외한다(docs/dev/ongoing/ai-policy-rag.md)
 * — 벡터 검색 특성상 애매한 질문에 잘못 매칭되면 TTL·Redis 등 내부 구현 용어가 구매자 챗봇 답변에 노출될
 * 위험이 있기 때문이다.
 *
 * <p><b>{@code policy-rag.indexing.enabled}로 토글</b>(기본 true, {@code src/test/resources/application.yaml}
 * 에서는 false)한다. 이게 없으면 {@code @SpringBootTest} 컨텍스트가 뜰 때마다 실제 OpenAI 임베딩 API를
 * 호출하게 되어(테스트 환경은 더미 키라 401), 기존 130여 개 {@code @SpringBootTest}가 전부 깨진다 —
 * {@code AiProductSuggestionServiceTest}/{@code BuyerChatServiceTest}가 이미 지키는 "자동 테스트에 실제
 * LLM/임베딩 호출을 넣지 않는다" 원칙과 동일하게, 색인은 실제 기동(bootRun) 시에만 켠다.
 *
 * <p>청크는 마크다운 {@code "## "} 섹션(규칙 / 근거·배경 / 적용 대상) 단위로 나눈다 — 파일 전체를 한
 * 덩어리로 넣는 것보다, "환불은 언제 되나요?" 같은 질문에 "규칙" 섹션만 매칭되게 해서 관련 없는 배경 설명이
 * 답변에 섞일 여지를 줄인다. 문서가 2개뿐이라 스플리터 라이브러리(TextSplitter 등) 없이 정규식으로 충분하다.
 *
 * <p><b>패러프레이즈 보강</b>: 각 청크 텍스트 끝에 자주 쓰일 법한 자연스러운 질문 예시를 "이런 질문에도
 * 해당" 섹션으로 덧붙여서 함께 임베딩한다(docs/dev/ongoing/ai-policy-rag.md "설계 변경" 섹션, 2026-08-11
 * 재승인). Attempt 1~2에서 threshold 필터링을 포기한 뒤에도, "제 돈은 언제 돌려받을 수 있나요?"처럼
 * 정책 문서와 어휘가 겹치지 않는 자연스러운 패러프레이즈는 여전히 코사인 유사도 순위 자체가 낮게 나와
 * topK에 안 들어올 수 있다는 게 실측으로 확인됐다({@code docs/logs/ai/policy-rag/001-policy-rag.md}
 * Attempt 2 — 해당 질의 최고 점수 0.177로 무관 질의보다도 낮았음). 예시 문구를 청크에 같이 임베딩해 두면
 * 그런 패러프레이즈와 청크 사이의 어휘적 거리가 줄어 순위가 개선될 가능성이 있다 — 다만 이건 완전한
 * 해결책이 아니라 **미리 예상한 흔한 표현만 보완**하는 경량 완화책이고, 예상 못 한 표현은 여전히 놓칠 수
 * 있다(그 경우의 안전망은 {@code BuyerChatService} 프롬프트 몫 — {@link com.gong9ri.gong9ri.service.PolicyRagService}
 * 계약 참고). **원본 {@code docs/policy/*.md}와 반입 사본({@code src/main/resources/policy/*.md})은
 * 건드리지 않는다** — 예시 문구는 이 컴포넌트가 청크 텍스트를 만드는 시점(임베딩 직전)에만 코드로 덧붙인다.
 *
 * <p><b>출처표시용 "표시용 출처명" — 내부 문서 제목과 분리</b>: 청크 맨 앞의 {@code # 문서 제목}(예:
 * "공구팀 실패(미성사) 및 환불 트리거")은 개발팀이 내부 문서 관리용으로 지은 이름이라 "미성사", "트리거"
 * 같은 표현이 구매자에게 그대로 노출되면 어색하다({@code docs/dev/ai/buyer-chatbot/design.md}의 RAG
 * 출처표시 기능이 처음엔 이 내부 제목을 그대로 인용했음). 그래서 내부 제목 줄은 임베딩 문맥용으로 그대로
 * 두고({@code docs/policy/*.md} 원본도 안 바꿈), 별도로 {@code DISPLAY_SOURCE_NAME_BY_PATH}에 고객
 * 응대에 어울리는 이름("환불 정책" 등)을 매핑해 청크 텍스트에 "표시용 출처명: {이름}" 줄로 함께 임베딩한다.
 * {@code BuyerChatService}의 출처표시 지시는 이 줄만 인용하도록 바뀌었다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "policy-rag.indexing", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PolicyDocumentIndexer implements ApplicationRunner {

    private static final List<String> POLICY_RESOURCE_PATHS = List.of(
            "policy/refund-trigger.md",
            "policy/team-success-criteria.md");

    private static final Pattern TITLE_PATTERN = Pattern.compile("(?m)^# (.+)$");
    // "## " 헤딩 기준으로 섹션을 나눈다(줄 맨 앞에서 시작하는 경우만 헤딩으로 인식).
    private static final Pattern SECTION_SPLIT_PATTERN = Pattern.compile("(?m)^## ");

    // 파일별 패러프레이즈 보강 문구. 문서가 짧아(파일당 섹션 3개) 섹션별로 따로 관리하지 않고, 같은 파일의
    // 모든 섹션 청크에 동일하게 붙인다 — 어차피 한 파일의 섹션들은 전부 같은 주제(환불/성사)라 서로 대체
    // 문맥으로 쓰여도 무방하다. "제 돈은 언제 돌려받을 수 있나요?"는 Attempt 2에서 실제로 놓쳤던 표현이라
    // 반드시 포함한다(docs/logs/ai/policy-rag/001-policy-rag.md).
    private static final Map<String, List<String>> EXAMPLE_QUESTIONS_BY_PATH = Map.of(
            "policy/refund-trigger.md", List.of(
                    "제 돈은 언제 돌려받을 수 있나요?",
                    "구매 취소하면 돈 다시 받을 수 있나요?",
                    "공구 기한이 지나면 어떻게 되나요?",
                    "환불은 자동으로 처리되나요?"),
            "policy/team-success-criteria.md", List.of(
                    "공구팀은 언제 성사되나요?",
                    "인원이 다 차면 바로 구매 확정되는 거예요?",
                    "정원이 다 차면 가격이 바로 확정되나요?"));

    // 챗봇이 출처를 인용할 때 쓸 고객 응대용 이름. 내부 문서 제목(# ...)은 개발 편의상 지은 이름이라
    // 그대로 노출하면 어색해서 별도로 관리한다(위 클래스 Javadoc "출처표시용 표시용 출처명" 참고).
    private static final Map<String, String> DISPLAY_SOURCE_NAME_BY_PATH = Map.of(
            "policy/refund-trigger.md", "환불 정책",
            "policy/team-success-criteria.md", "공구 성사 기준");

    private final VectorStore vectorStore;

    @Override
    public void run(ApplicationArguments args) {
        List<Document> documents = new ArrayList<>();
        for (String path : POLICY_RESOURCE_PATHS) {
            documents.addAll(splitIntoSections(path));
        }
        vectorStore.add(documents);
        log.info("정책 문서 RAG 색인 완료: fileCount={}, chunkCount={}", POLICY_RESOURCE_PATHS.size(), documents.size());
    }

    private List<Document> splitIntoSections(String classpathPath) {
        String content = readResource(classpathPath);
        String title = extractTitle(content);

        List<Document> chunks = new ArrayList<>();
        String[] parts = SECTION_SPLIT_PATTERN.split(content);
        // parts[0]은 첫 "## " 이전 부분(H1 제목 줄)이라 섹션 청크 대상에서 제외한다.
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            int newlineIndex = part.indexOf('\n');
            String sectionTitle = (newlineIndex == -1 ? part : part.substring(0, newlineIndex)).trim();
            String sectionBody = (newlineIndex == -1 ? "" : part.substring(newlineIndex + 1)).trim();

            String text = "# " + title + "\n\n표시용 출처명: " + displaySourceName(classpathPath)
                    + "\n\n## " + sectionTitle + "\n\n" + sectionBody
                    + buildExampleQuestionsBlock(classpathPath);
            chunks.add(Document.builder()
                    .text(text)
                    .metadata("source", classpathPath)
                    .metadata("section", sectionTitle)
                    .build());
        }
        return chunks;
    }

    /**
     * 패러프레이즈 보강 블록("이런 질문에도 해당")을 만든다. 예시 문구가 없는 파일이면 빈 문자열(블록 생략).
     */
    private String buildExampleQuestionsBlock(String classpathPath) {
        List<String> examples = EXAMPLE_QUESTIONS_BY_PATH.getOrDefault(classpathPath, List.of());
        if (examples.isEmpty()) {
            return "";
        }
        StringBuilder block = new StringBuilder("\n\n## 이런 질문에도 해당\n\n");
        for (String example : examples) {
            block.append("- ").append(example).append('\n');
        }
        return block.toString();
    }

    private String displaySourceName(String classpathPath) {
        return DISPLAY_SOURCE_NAME_BY_PATH.getOrDefault(classpathPath, classpathPath);
    }

    private String extractTitle(String content) {
        Matcher matcher = TITLE_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private String readResource(String classpathPath) {
        try {
            return StreamUtils.copyToString(
                    new ClassPathResource(classpathPath).getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("정책 문서 리소스를 읽을 수 없습니다: " + classpathPath, e);
        }
    }
}
