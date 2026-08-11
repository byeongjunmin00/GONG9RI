package com.gong9ri.gong9ri.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

/**
 * {@link PolicyRagService} 구현체 — {@link VectorStore}(인메모리 {@code SimpleVectorStore},
 * {@code PolicyRagVectorStoreConfig})에 코사인 유사도 검색을 위임한다. 색인 자체는
 * {@code PolicyDocumentIndexer}(기동 시 1회)가 담당하고, 이 클래스는 검색만 한다.
 *
 * <p><b>유사도 threshold로 "관련 없으면 걸러내기"를 하지 않는다.</b> Attempt 1~2
 * ({@code docs/logs/ai/policy-rag/001-policy-rag.md})에서 이 코퍼스 규모(정책 문서 2개, 6청크)로는
 * 어떤 threshold 값을 골라도 안정적으로 관련/무관을 가를 수 없음을 실측으로 확인했다 — 임베딩 모델을
 * {@code text-embedding-3-small}로 바꿔도, "제 돈은 언제 돌려받을 수 있나요?"처럼 정책 문서와 어휘가
 * 겹치지 않는 자연스러운 패러프레이즈의 점수가 "점심 메뉴 추천해줘" 같은 완전 무관한 질문보다 오히려
 * 낮게 나오는 경우가 실제로 있었다. 이에 따라({@code docs/dev/ongoing/ai-policy-rag.md} "설계 변경"
 * 섹션, 2026-08-11 재승인) threshold는 {@link SearchRequest#SIMILARITY_THRESHOLD_ACCEPT_ALL}로 두고
 * 항상 상위 {@code TOP_K}개를 그대로 반환한다 — 빈 리스트를 반환하는 "관련 없음 판정"은 이 클래스가
 * 하지 않는다.
 *
 * <p><b>관련성 최종 판단은 호출하는 쪽(챗봇)의 책임이다.</b> 반환된 스니펫이 질문과 무관할 수 있다는
 * 전제하에, "제공된 문맥이 질문과 무관하면 참고하지 말고 무시하라"는 판단은 이 인터페이스를 호출하는
 * {@code BuyerChatService}의 시스템 프롬프트가 맡는다({@link PolicyRagService} 계약 참고).
 *
 * <p>{@code TOP_K}는 실측 근거 없는 초기값이다(design.md에 남길 것 — {@code BuyerChatService}의
 * {@code LLM_TIMEOUT}과 같은 성격). 정책 문서가 6개 청크뿐이라 넉넉히 크게 잡을 필요는 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyRagServiceImpl implements PolicyRagService {

    private static final int TOP_K = 3;

    private final VectorStore vectorStore;

    @Override
    public List<String> findRelevantSnippets(String query) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(TOP_K)
                .similarityThreshold(SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL)
                .build();

        List<Document> results = vectorStore.similaritySearch(searchRequest);
        log.debug("정책 RAG 검색: query={}, hitCount={}", query, results.size());
        return results.stream().map(Document::getText).toList();
    }
}
