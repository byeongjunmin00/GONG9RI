package com.gong9ri.gong9ri.config;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * {@code PolicyDocumentIndexer}는 생성자 주입 {@code VectorStore} 하나뿐인 순수 POJO라 스프링 컨텍스트 없이도
 * 단위 테스트로 청크 텍스트 구성을 검증할 수 있다. 내부 문서 제목("# ...")과 고객 응대용 "표시용 출처명"이
 * 분리돼 함께 임베딩되는지가 이 테스트의 핵심(챗봇이 출처를 인용할 때 내부 제목이 아니라 이 이름을 쓰도록
 * 유도하는 장치 — {@code docs/dev/ai/buyer-chatbot/design.md} RAG 출처표시 참고).
 */
class PolicyDocumentIndexerTest {

    @Test
    @DisplayName("각 청크는 내부 문서 제목과 별도로 고객 응대용 표시용 출처명을 함께 포함한다")
    void run_indexesChunksWithDisplaySourceNameSeparateFromInternalTitle() {
        VectorStore vectorStore = mock(VectorStore.class);
        PolicyDocumentIndexer indexer = new PolicyDocumentIndexer(vectorStore);

        indexer.run(null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        List<Document> documents = captor.getValue();

        List<Document> refundChunks = documents.stream()
                .filter(d -> "policy/refund-trigger.md".equals(d.getMetadata().get("source")))
                .toList();
        assertTrue(!refundChunks.isEmpty());
        for (Document doc : refundChunks) {
            assertTrue(doc.getText().contains("표시용 출처명: 환불 정책"));
            assertTrue(doc.getText().contains("# 공구팀 실패(미성사) 및 환불 트리거"));
        }

        List<Document> successChunks = documents.stream()
                .filter(d -> "policy/team-success-criteria.md".equals(d.getMetadata().get("source")))
                .toList();
        assertTrue(!successChunks.isEmpty());
        for (Document doc : successChunks) {
            assertTrue(doc.getText().contains("표시용 출처명: 공구 성사 기준"));
        }
    }
}
