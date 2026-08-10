package com.gong9ri.gong9ri.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.entity.TeamParticipation;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.TeamParticipationRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BuyerMypageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private GroupBuyTeamRepository groupBuyTeamRepository;

    @Autowired
    private TeamParticipationRepository teamParticipationRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    private Member saveMember(String username, Role role) {
        Member member = new Member(username, "encoded-password", "테스트유저", username + "@test.com", role);
        return memberRepository.save(member);
    }

    private RequestPostProcessor asUser(Member member) {
        MemberUserDetails principal = new MemberUserDetails(member);
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return authentication(token);
    }

    private Product saveProduct(Member seller, int maxParticipants) {
        return productRepository.save(new Product(seller, "제주 감귤 5kg", "설명", 25000, maxParticipants, null));
    }

    @Test
    @DisplayName("구매 완료 목록 조회 성공")
    void purchases_success() throws Exception {
        Member seller = saveMember("mpSeller1", Role.SELLER);
        Product product = saveProduct(seller, 10);
        Member buyer = saveMember("mpBuyer1", Role.BUYER);
        paymentRepository.save(new Payment(buyer, product, null, 25000));

        mockMvc.perform(get("/api/buyer/mypage/purchases").with(asUser(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].amount").value(25000))
                .andExpect(jsonPath("$.data[0].productName").value("제주 감귤 5kg"));
    }

    @Test
    @DisplayName("구매 완료 목록은 본인 결제만 보이고 타인 결제는 안 보인다 (스코핑)")
    void purchases_scoping_onlyOwnPayments() throws Exception {
        Member seller = saveMember("mpSeller2", Role.SELLER);
        Product product = saveProduct(seller, 10);
        Member buyerA = saveMember("mpBuyerA", Role.BUYER);
        Member buyerB = saveMember("mpBuyerB", Role.BUYER);
        paymentRepository.save(new Payment(buyerA, product, null, 25000));
        paymentRepository.save(new Payment(buyerB, product, null, 30000));

        mockMvc.perform(get("/api/buyer/mypage/purchases").with(asUser(buyerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].amount").value(25000));
    }

    @Test
    @DisplayName("판매자 계정으로 구매 목록 조회 시 403 FORBIDDEN")
    void purchases_forbidden_seller() throws Exception {
        Member seller = saveMember("mpSeller3", Role.SELLER);

        mockMvc.perform(get("/api/buyer/mypage/purchases").with(asUser(seller)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("비로그인으로 구매 목록 조회 시 401 UNAUTHORIZED")
    void purchases_unauthorized() throws Exception {
        mockMvc.perform(get("/api/buyer/mypage/purchases"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("공구 참여 목록은 성사/미성사 상관없이 전체 반환한다")
    void teams_success_returnsAllStatuses() throws Exception {
        Member seller = saveMember("mpSeller4", Role.SELLER);
        Product product = saveProduct(seller, 2);
        Member buyer = saveMember("mpBuyer2", Role.BUYER);

        GroupBuyTeam recruitingTeam = groupBuyTeamRepository.save(
                new GroupBuyTeam(product, buyer, 5, LocalDateTime.now().plusDays(7)));
        teamParticipationRepository.save(new TeamParticipation(recruitingTeam, buyer));

        GroupBuyTeam failedTeam = groupBuyTeamRepository.save(
                new GroupBuyTeam(product, buyer, 5, LocalDateTime.now().plusDays(7)));
        teamParticipationRepository.save(new TeamParticipation(failedTeam, buyer));

        mockMvc.perform(get("/api/buyer/mypage/teams").with(asUser(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("판매자 계정으로 공구 참여 목록 조회 시 403 FORBIDDEN")
    void teams_forbidden_seller() throws Exception {
        Member seller = saveMember("mpSeller5", Role.SELLER);

        mockMvc.perform(get("/api/buyer/mypage/teams").with(asUser(seller)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("비로그인으로 공구 참여 목록 조회 시 401 UNAUTHORIZED")
    void teams_unauthorized() throws Exception {
        mockMvc.perform(get("/api/buyer/mypage/teams"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
