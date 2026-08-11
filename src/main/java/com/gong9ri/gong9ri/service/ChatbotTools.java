package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.dto.BuyerTeamResponse;
import com.gong9ri.gong9ri.dto.ProductSearchResult;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.TeamParticipationRepository;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 구매자 챗봇의 Tool Calling 대상(발제 AI 필수2). 스프링 빈이 아니라 {@link BuyerChatService}가 요청마다
 * 로그인한 구매자 ID를 캡처해서 직접 {@code new}로 만든다 — 싱글톤 빈으로 두면 동시 요청 간 buyerId가
 * 섞이므로 반드시 요청 스코프로 생성한다.
 */
public class ChatbotTools {

    private final Long buyerId;
    private final ProductRepository productRepository;
    private final TeamParticipationRepository teamParticipationRepository;

    public ChatbotTools(Long buyerId, ProductRepository productRepository,
            TeamParticipationRepository teamParticipationRepository) {
        this.buyerId = buyerId;
        this.productRepository = productRepository;
        this.teamParticipationRepository = teamParticipationRepository;
    }

    @Tool(description = "상품명 키워드로 GONG9RI에 등록된 상품을 검색한다. 결과가 없으면 빈 목록을 반환한다.")
    public List<ProductSearchResult> searchProducts(
            @ToolParam(description = "검색할 상품명 키워드") String keyword) {
        return productRepository.findTop10ByNameContainingIgnoreCase(keyword).stream()
                .map(ProductSearchResult::from)
                .toList();
    }

    @Tool(description = "현재 대화 중인 구매자가 참여 중이거나 완료한 공구팀 목록을 조회한다(참여 이력이 없으면 빈 목록).")
    public List<BuyerTeamResponse> getMyTeamParticipations() {
        return teamParticipationRepository.findAllByMemberIdWithTeamAndProduct(buyerId).stream()
                .map(BuyerTeamResponse::from)
                .toList();
    }
}
