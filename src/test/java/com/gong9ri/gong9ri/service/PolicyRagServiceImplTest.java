package com.gong9ri.gong9ri.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * {@code PolicyRagServiceImpl}을 실제 임베딩 API 호출 없이 검증한다 — {@code VectorStore}를
 * {@code @MockitoBean}으로 대체해 검색 결과 매핑과 검색 조건(질의/topK) 구성을 확인한다
 * (docs/dev/ongoing/ai-policy-rag.md). {@code PolicyDocumentIndexer}는 테스트 프로파일에서
 * {@code policy-rag.indexing.enabled=false}로 꺼져 있어 컨텍스트 로딩 시 실제 색인이 일어나지 않는다.
 * 실제 정책 문서 색인·검색 정확도는 로컬 실호출로 별도 확인한다(자동 테스트에는 넣지 않음).
 *
 * <p>설계 변경(docs/dev/ongoing/ai-policy-rag.md "설계 변경" 섹션, 2026-08-11 재승인)으로 유사도
 * threshold 필터링을 포기했다 — 이 서비스는 항상 벡터스토어가 반환한 topK개를 그대로 돌려주고, "관련
 * 없으면 빈 리스트"는 이 계층의 책임이 아니다. 그래서 "무관 질의라 빈 목록을 반환한다" 같은 케이스는 더 이상
 * 성립하지 않는다(벡터스토어가 빈 리스트를 준 경우 그대로 빈 리스트를 반환하는 매핑 동작만 남아 있다 —
 * 아래 테스트는 이 매핑 자체가 무너지지 않았는지만 확인한다).
 */
@SpringBootTest
class PolicyRagServiceImplTest {

    @Autowired
    private PolicyRagService policyRagService;

    @MockitoBean
    private VectorStore vectorStore;

    @Test
    @DisplayName("정책 관련 질의는 벡터스토어가 찾은 스니펫 텍스트를 관련도 순서 그대로 반환한다")
    void findRelevantSnippets_returnsSnippetTextsInOrder() {
        Document refundSnippet = Document.builder()
                .text("# 공구팀 실패(미성사) 및 환불 트리거\n\n## 규칙\n스케줄러가 마감이 지난 팀을 FAILED로 전환하고 환불한다.")
                .build();
        Document successSnippet = Document.builder()
                .text("# 공구팀 성사 판정 기준\n\n## 규칙\n정원이 차면 즉시 SUCCESS로 전환한다.")
                .build();
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(refundSnippet, successSnippet));

        List<String> snippets = policyRagService.findRelevantSnippets("환불은 언제 되나요?");

        assertEquals(2, snippets.size());
        assertTrue(snippets.get(0).contains("환불 트리거"));
        assertTrue(snippets.get(1).contains("성사 판정 기준"));
    }

    @Test
    @DisplayName("벡터스토어가 빈 결과를 주면(예: 색인이 비어있는 경우) 빈 목록을 그대로 반환한다")
    void findRelevantSnippets_vectorStoreReturnsEmpty_returnsEmptyList() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        List<String> snippets = policyRagService.findRelevantSnippets("오늘 날씨 어때?");

        assertTrue(snippets.isEmpty());
    }

    @Test
    @DisplayName("질의 문자열과 topK를 담은 SearchRequest로 검색을 위임하고, 유사도 threshold는 필터링하지 않도록 accept-all로 둔다")
    void findRelevantSnippets_buildsSearchRequestWithQueryAndTopKWithoutFiltering() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        policyRagService.findRelevantSnippets("공구 기한이 지나면 어떻게 되나요?");

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        SearchRequest captured = captor.getValue();
        assertEquals("공구 기한이 지나면 어떻게 되나요?", captured.getQuery());
        assertTrue(captured.getTopK() > 0);
        assertEquals(SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL, captured.getSimilarityThreshold());
    }
}
