package com.gong9ri.gong9ri.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.PriceTier;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.entity.TeamParticipation;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.PriceTierRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.TeamParticipationRepository;
import java.time.LocalDateTime;
import java.util.Map;
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
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PriceTierRepository priceTierRepository;

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

    private Product saveProduct(Member seller, int basePrice, int maxParticipants) {
        return productRepository.save(new Product(seller, "제주 감귤 5kg", "설명", basePrice, maxParticipants));
    }

    private GroupBuyTeam saveTeamWithCount(Product product, Member leader, int maxParticipants, int currentCount) {
        GroupBuyTeam team = new GroupBuyTeam(product, leader, maxParticipants, LocalDateTime.now().plusDays(7));
        for (int i = 1; i < currentCount; i++) {
            team.increaseParticipant();
        }
        GroupBuyTeam saved = groupBuyTeamRepository.save(team);
        teamParticipationRepository.save(new TeamParticipation(saved, leader));
        return saved;
    }

    private String toJson(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    @Test
    @DisplayName("혼자구매 결제 생성 시 201이고 금액은 basePrice다")
    void create_solo_success() throws Exception {
        Member seller = saveMember("paySeller1", Role.SELLER);
        Product product = saveProduct(seller, 25000, 10);
        Member buyer = saveMember("payBuyer1", Role.BUYER);

        mockMvc.perform(post("/api/payments")
                        .with(asUser(buyer))
                        .contentType("application/json")
                        .content(toJson(Map.of("productId", product.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amount").value(25000))
                .andExpect(jsonPath("$.data.teamId").doesNotExist())
                .andExpect(jsonPath("$.data.status").value("PAID"));
    }

    @Test
    @DisplayName("공구팀 결제 생성 시 currentCount 구간의 tier 가격이 적용된다")
    void create_team_success_appliesTierPrice() throws Exception {
        Member seller = saveMember("paySeller2", Role.SELLER);
        Product product = saveProduct(seller, 25000, 10);
        priceTierRepository.save(new PriceTier(product, 3, 22000));
        priceTierRepository.save(new PriceTier(product, 5, 20000));
        Member leader = saveMember("payLeader1", Role.BUYER);
        GroupBuyTeam team = saveTeamWithCount(product, leader, 10, 5);

        mockMvc.perform(post("/api/payments")
                        .with(asUser(leader))
                        .contentType("application/json")
                        .content(toJson(Map.of("productId", product.getId(), "teamId", team.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amount").value(20000))
                .andExpect(jsonPath("$.data.teamId").value(team.getId()));
    }

    @Test
    @DisplayName("productId 없이 요청하면 400 VALIDATION_FAILED")
    void create_validationFailed() throws Exception {
        Member buyer = saveMember("payBuyer2", Role.BUYER);

        mockMvc.perform(post("/api/payments")
                        .with(asUser(buyer))
                        .contentType("application/json")
                        .content(toJson(Map.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("존재하지 않는 상품으로 결제 시 404 PRODUCT_NOT_FOUND")
    void create_productNotFound() throws Exception {
        Member buyer = saveMember("payBuyer3", Role.BUYER);

        mockMvc.perform(post("/api/payments")
                        .with(asUser(buyer))
                        .contentType("application/json")
                        .content(toJson(Map.of("productId", 999999L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("존재하지 않는 팀으로 결제 시 404 TEAM_NOT_FOUND")
    void create_teamNotFound() throws Exception {
        Member seller = saveMember("paySeller3", Role.SELLER);
        Product product = saveProduct(seller, 25000, 10);
        Member buyer = saveMember("payBuyer4", Role.BUYER);

        mockMvc.perform(post("/api/payments")
                        .with(asUser(buyer))
                        .contentType("application/json")
                        .content(toJson(Map.of("productId", product.getId(), "teamId", 999999L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
    }

    @Test
    @DisplayName("참가하지 않은 사람이 정원 찬 팀으로 결제 시도하면 409 TEAM_FULL")
    void create_teamFull_forNonParticipant() throws Exception {
        Member seller = saveMember("paySeller4", Role.SELLER);
        Product product = saveProduct(seller, 25000, 2);
        Member leader = saveMember("payLeader2", Role.BUYER);
        GroupBuyTeam team = saveTeamWithCount(product, leader, 2, 2);
        Member outsider = saveMember("payOutsider1", Role.BUYER);

        mockMvc.perform(post("/api/payments")
                        .with(asUser(outsider))
                        .contentType("application/json")
                        .content(toJson(Map.of("productId", product.getId(), "teamId", team.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEAM_FULL"));
    }

    @Test
    @DisplayName("이미 참가한 사람은 정원이 다 찬 팀이어도 결제할 수 있다")
    void create_success_forExistingParticipant_evenIfTeamFull() throws Exception {
        Member seller = saveMember("paySeller5", Role.SELLER);
        Product product = saveProduct(seller, 25000, 2);
        priceTierRepository.save(new PriceTier(product, 2, 21000));
        Member leader = saveMember("payLeader3", Role.BUYER);
        GroupBuyTeam team = saveTeamWithCount(product, leader, 2, 2);

        mockMvc.perform(post("/api/payments")
                        .with(asUser(leader))
                        .contentType("application/json")
                        .content(toJson(Map.of("productId", product.getId(), "teamId", team.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amount").value(21000));
    }

    @Test
    @DisplayName("판매자 계정으로 결제 시도 시 403 FORBIDDEN")
    void create_forbidden_seller() throws Exception {
        Member seller = saveMember("paySeller6", Role.SELLER);
        Product product = saveProduct(seller, 25000, 10);

        mockMvc.perform(post("/api/payments")
                        .with(asUser(seller))
                        .contentType("application/json")
                        .content(toJson(Map.of("productId", product.getId()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("비로그인으로 결제 시도 시 401 UNAUTHORIZED")
    void create_unauthorized() throws Exception {
        Member seller = saveMember("paySeller7", Role.SELLER);
        Product product = saveProduct(seller, 25000, 10);

        mockMvc.perform(post("/api/payments")
                        .contentType("application/json")
                        .content(toJson(Map.of("productId", product.getId()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("본인 결제 상세 조회 성공")
    void detail_success() throws Exception {
        Member seller = saveMember("paySeller8", Role.SELLER);
        Product product = saveProduct(seller, 25000, 10);
        Member buyer = saveMember("payBuyer5", Role.BUYER);
        Payment payment = paymentRepository.save(new Payment(buyer, product, null, 25000));

        mockMvc.perform(get("/api/payments/" + payment.getId()).with(asUser(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentId").value(payment.getId()))
                .andExpect(jsonPath("$.data.amount").value(25000))
                .andExpect(jsonPath("$.data.status").value("PAID"));
    }

    @Test
    @DisplayName("존재하지 않는 결제 조회 시 404 PAYMENT_NOT_FOUND")
    void detail_paymentNotFound() throws Exception {
        Member buyer = saveMember("payBuyer6", Role.BUYER);

        mockMvc.perform(get("/api/payments/999999").with(asUser(buyer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("타인의 결제 조회 시 403 FORBIDDEN")
    void detail_forbidden_notOwner() throws Exception {
        Member seller = saveMember("paySeller9", Role.SELLER);
        Product product = saveProduct(seller, 25000, 10);
        Member buyer = saveMember("payBuyer7", Role.BUYER);
        Payment payment = paymentRepository.save(new Payment(buyer, product, null, 25000));
        Member stranger = saveMember("payStranger1", Role.BUYER);

        mockMvc.perform(get("/api/payments/" + payment.getId()).with(asUser(stranger)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("비로그인으로 결제 조회 시 401 UNAUTHORIZED")
    void detail_unauthorized() throws Exception {
        Member seller = saveMember("paySeller10", Role.SELLER);
        Product product = saveProduct(seller, 25000, 10);
        Member buyer = saveMember("payBuyer8", Role.BUYER);
        Payment payment = paymentRepository.save(new Payment(buyer, product, null, 25000));

        mockMvc.perform(get("/api/payments/" + payment.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
