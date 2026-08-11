package com.gong9ri.gong9ri.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gong9ri.gong9ri.dto.BuyerTeamResponse;
import com.gong9ri.gong9ri.dto.ProductSearchResult;
import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.entity.TeamParticipation;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.TeamParticipationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * {@code ChatbotTools}의 {@code @Tool} 메서드 자체를 직접 단위 테스트한다. {@code ChatClient}를 목으로
 * 대체하면 Spring AI의 실제 Tool 실행 메커니즘을 타지 않으므로(docs/dev/ai/buyer-chatbot/design.md),
 * Tool 호출 정확성은 이 레벨에서 검증하고 실제 LLM이 Tool을 잘 골라 부르는지는 로컬 실호출로 검증한다.
 */
@SpringBootTest
class ChatbotToolsTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private GroupBuyTeamRepository groupBuyTeamRepository;

    @Autowired
    private TeamParticipationRepository teamParticipationRepository;

    private Member seller;
    private Member buyerA;
    private Member buyerB;
    private Product product;
    private GroupBuyTeam team;

    @BeforeEach
    void setUp() {
        seller = memberRepository.save(new Member("chatToolSeller1", "pw", "판매자", "chatToolSeller1@test.com",
                Role.SELLER));
        buyerA = memberRepository.save(new Member("chatToolBuyerA", "pw", "구매자A", "chatToolBuyerA@test.com",
                Role.BUYER));
        buyerB = memberRepository.save(new Member("chatToolBuyerB", "pw", "구매자B", "chatToolBuyerB@test.com",
                Role.BUYER));
        product = productRepository.save(
                new Product(seller, "제주 감귤 세트", "설명", 10000, 10, null));
        team = groupBuyTeamRepository.save(
                new GroupBuyTeam(product, buyerA, 10, LocalDateTime.now().plusDays(7)));
        teamParticipationRepository.save(new TeamParticipation(team, buyerA));
    }

    @AfterEach
    void cleanUp() {
        teamParticipationRepository.deleteByTeamId(team.getId());
        groupBuyTeamRepository.deleteById(team.getId());
        productRepository.deleteById(product.getId());
        memberRepository.deleteById(buyerB.getId());
        memberRepository.deleteById(buyerA.getId());
        memberRepository.deleteById(seller.getId());
    }

    @Test
    @DisplayName("키워드로 상품을 검색하면 이름에 포함된 상품이 나온다")
    void searchProducts_returnsMatchingProducts() {
        ChatbotTools tools = new ChatbotTools(buyerA.getId(), productRepository, teamParticipationRepository);

        List<ProductSearchResult> results = tools.searchProducts("감귤");

        assertTrue(results.stream().anyMatch(r -> r.productId().equals(product.getId())));
    }

    @Test
    @DisplayName("일치하는 상품이 없으면 빈 목록을 반환한다")
    void searchProducts_noMatch_returnsEmptyList() {
        ChatbotTools tools = new ChatbotTools(buyerA.getId(), productRepository, teamParticipationRepository);

        List<ProductSearchResult> results = tools.searchProducts("존재하지않는키워드XYZ");

        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("본인이 참여한 공구팀만 조회되고 다른 구매자 것은 안 섞인다")
    void getMyTeamParticipations_returnsOnlyThisBuyersTeams() {
        ChatbotTools toolsForA = new ChatbotTools(buyerA.getId(), productRepository, teamParticipationRepository);
        ChatbotTools toolsForB = new ChatbotTools(buyerB.getId(), productRepository, teamParticipationRepository);

        List<BuyerTeamResponse> resultA = toolsForA.getMyTeamParticipations();
        List<BuyerTeamResponse> resultB = toolsForB.getMyTeamParticipations();

        assertEquals(1, resultA.size());
        assertEquals(team.getId(), resultA.get(0).teamId());
        assertTrue(resultB.isEmpty());
    }
}
