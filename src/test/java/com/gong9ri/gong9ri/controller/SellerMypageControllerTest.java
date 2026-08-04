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
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
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
class SellerMypageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private GroupBuyTeamRepository groupBuyTeamRepository;

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

    private Product saveProduct(Member seller, String name, int maxParticipants) {
        return productRepository.save(new Product(seller, name, "설명", 25000, maxParticipants));
    }

    @Test
    @DisplayName("내가 등록한 상품 목록 조회 성공")
    void products_success() throws Exception {
        Member seller = saveMember("mpSellerA1", Role.SELLER);
        saveProduct(seller, "제주 감귤 5kg", 10);

        mockMvc.perform(get("/api/seller/mypage/products").with(asUser(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("제주 감귤 5kg"));
    }

    @Test
    @DisplayName("내가 등록한 상품 목록은 다른 판매자의 상품은 안 보인다 (스코핑)")
    void products_scoping_onlyOwnProducts() throws Exception {
        Member sellerA = saveMember("mpSellerB1", Role.SELLER);
        Member sellerB = saveMember("mpSellerB2", Role.SELLER);
        saveProduct(sellerA, "제주 감귤 5kg", 10);
        saveProduct(sellerB, "경북 사과 3kg", 8);

        mockMvc.perform(get("/api/seller/mypage/products").with(asUser(sellerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("제주 감귤 5kg"));
    }

    @Test
    @DisplayName("구매자 계정으로 등록 상품 목록 조회 시 403 FORBIDDEN")
    void products_forbidden_buyer() throws Exception {
        Member buyer = saveMember("mpBuyerC1", Role.BUYER);

        mockMvc.perform(get("/api/seller/mypage/products").with(asUser(buyer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("비로그인으로 등록 상품 목록 조회 시 401 UNAUTHORIZED")
    void products_unauthorized() throws Exception {
        mockMvc.perform(get("/api/seller/mypage/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("수익 현황은 PAID만 합산하고 REFUNDED는 금액에서 제외한다")
    void revenue_success_excludesRefundedFromTotal() throws Exception {
        Member seller = saveMember("mpSellerD1", Role.SELLER);
        Product product = saveProduct(seller, "제주 감귤 5kg", 10);
        Member buyer1 = saveMember("mpBuyerD1", Role.BUYER);
        Member buyer2 = saveMember("mpBuyerD2", Role.BUYER);
        Member buyer3 = saveMember("mpBuyerD3", Role.BUYER);

        paymentRepository.save(new Payment(buyer1, product, null, 20000));
        paymentRepository.save(new Payment(buyer2, product, null, 20000));
        Payment refunded = paymentRepository.save(new Payment(buyer3, product, null, 30000));
        refunded.refund();

        mockMvc.perform(get("/api/seller/mypage/revenue").with(asUser(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRevenue").value(40000))
                .andExpect(jsonPath("$.data.paidCount").value(2))
                .andExpect(jsonPath("$.data.refundedCount").value(1));
    }

    @Test
    @DisplayName("결제가 하나도 없으면 수익 현황은 전부 0이다")
    void revenue_success_zeroWhenNoPayments() throws Exception {
        Member seller = saveMember("mpSellerD2", Role.SELLER);
        saveProduct(seller, "제주 감귤 5kg", 10);

        mockMvc.perform(get("/api/seller/mypage/revenue").with(asUser(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRevenue").value(0))
                .andExpect(jsonPath("$.data.paidCount").value(0))
                .andExpect(jsonPath("$.data.refundedCount").value(0));
    }

    @Test
    @DisplayName("구매자 계정으로 수익 현황 조회 시 403 FORBIDDEN")
    void revenue_forbidden_buyer() throws Exception {
        Member buyer = saveMember("mpBuyerC2", Role.BUYER);

        mockMvc.perform(get("/api/seller/mypage/revenue").with(asUser(buyer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("비로그인으로 수익 현황 조회 시 401 UNAUTHORIZED")
    void revenue_unauthorized() throws Exception {
        mockMvc.perform(get("/api/seller/mypage/revenue"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("내 상품 공구 참여 현황 조회 성공")
    void teams_success() throws Exception {
        Member seller = saveMember("mpSellerE1", Role.SELLER);
        Product product = saveProduct(seller, "제주 감귤 5kg", 5);
        Member leader = saveMember("mpBuyerE1", Role.BUYER);
        groupBuyTeamRepository.save(new GroupBuyTeam(product, leader, 5, LocalDateTime.now().plusDays(7)));

        mockMvc.perform(get("/api/seller/mypage/teams").with(asUser(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].productName").value("제주 감귤 5kg"));
    }

    @Test
    @DisplayName("구매자 계정으로 공구 참여 현황 조회 시 403 FORBIDDEN")
    void teams_forbidden_buyer() throws Exception {
        Member buyer = saveMember("mpBuyerC3", Role.BUYER);

        mockMvc.perform(get("/api/seller/mypage/teams").with(asUser(buyer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("비로그인으로 공구 참여 현황 조회 시 401 UNAUTHORIZED")
    void teams_unauthorized() throws Exception {
        mockMvc.perform(get("/api/seller/mypage/teams"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
