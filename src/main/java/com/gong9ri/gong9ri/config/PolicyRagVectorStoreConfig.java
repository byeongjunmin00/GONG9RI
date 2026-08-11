package com.gong9ri.gong9ri.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 정책 문서 RAG 검색(docs/dev/ongoing/ai-policy-rag.md)용 벡터스토어. 색인 대상이 정책 문서 2개뿐이라
 * 별도 벡터 DB(pgvector 등) 없이 Spring AI 내장 {@link SimpleVectorStore}(인메모리)로 충분하다고 판단했다
 * — 서버 재시작마다 재색인이 필요하지만 문서가 적어 부담이 되지 않는다. 임베딩은 이미 의존성에 있는
 * {@code spring-ai-starter-model-openai}가 자동구성하는 {@link EmbeddingModel} 빈을 그대로 재사용한다.
 */
@Configuration
public class PolicyRagVectorStoreConfig {

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
