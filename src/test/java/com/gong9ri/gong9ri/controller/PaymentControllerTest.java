package com.gong9ri.gong9ri.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.gong9ri.gong9ri.client.PortOneCancelResult;
import com.gong9ri.gong9ri.client.PortOneClient;
import com.gong9ri.gong9ri.client.PortOnePaymentDetail;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.PriceTier;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.ProductCategory;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code PortOneClient}를 {@code @MockitoBean}으로 대체해 실제 PortOne 호출 없이 검증한다
 * (docs/dev/payment/portone/design.md) — {@code AiProductSuggestionServiceTest}가 {@code
 * ChatClient.Builder}를 목으로 대체하는 것과 같은 패턴. 기본 스텁은 "요청받은 pgPaymentId에 해당하는
 * 결제를 그대로 PAID로 승인"하도록 동작해, 개별 테스트가 금액을 하드코딩하지 않아도 되게 한다.
 */
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

    @MockitoBean
    private PortOneClient portOneClient;

    @BeforeEach
    void stubPortOnePaidByDefault() {
        // 기본 동작: 조회한 pgPaymentId에 해당하는 결제를 그 결제의 실제 금액 그대로 PAID 승인한다.
        when(portOneClient.getPayment(anyString())).thenAnswer(invocation -> {
            String pgPaymentId = invocation.getArgument(0);
            Payment payment = paymentRepository.findByPgPaymentId(pgPaymentId).orElseThrow();
            return new PortOnePaymentDetail("PAID", payment.getAmount());
        });
        when(portOneClient.cancelPayment(anyString(), anyString()))
                .thenReturn(new PortOneCancelResult(PortOneCancelResult.SUCCEEDED));
    }

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
        return productRepository.save(new Product(seller, "제주 감귤 5kg", "설명", basePrice, maxParticipants, null));
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
    @DisplayName("혼자구매 결제 생성 시 201이고 금액은 basePrice, 상태는 PENDING(승인 대기)이다")
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
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.pgPaymentId").exists())
                .andExpect(jsonPath("$.data.portoneStoreId").exists());
    }

    @Test
    @DisplayName("공구팀 결제 생성 시 maxParticipants 구간의 tier 가격이 적용되고, 상태는 PENDING이다")
    void create_team_success_appliesTierPrice() throws Exception {
        Member seller = saveMember("paySeller2", Role.SELLER);
        Product product = saveProduct(seller, 25000, 10);
        priceTierRepository.save(new PriceTier(product, 3, 22000));
        priceTierRepository.save(new PriceTier(product, 5, 20000));
        // maxParticipants(10)와 정확히 일치하는 구간을 하나 더 둬서, currentCount(5)가 아니라
        // maxParticipants가 기준이라는 걸 실제로 구분해 검증한다 — 구간이 3/5뿐이면 currentCount=5든
        // maxParticipants=10이든 둘 다 "5 이상" 구간(20000)에 걸려 우연히 같은 값이 나와 이 둘을 구분
        // 못 한다(팀원 리뷰로 발견된 문제).
        priceTierRepository.save(new PriceTier(product, 10, 18000));
        Member leader = saveMember("payLeader1", Role.BUYER);
        GroupBuyTeam team = saveTeamWithCount(product, leader, 10, 5);

        mockMvc.perform(post("/api/payments")
                        .with(asUser(leader))
                        .contentType("application/json")
                        .content(toJson(Map.of("productId", product.getId(), "teamId", team.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amount").value(18000))
                .andExpect(jsonPath("$.data.teamId").value(team.getId()))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("같은 팀 안에서 서로 다른 시점(currentCount=1, 중간, 정원 다 찬 시점)에 결제해도 "
            + "항상 동일한 금액(maxParticipants 기준 tier 가격)이 적용된다")
    void create_team_multipleTimestamps_sameAmountRegardlessOfCurrentCount() throws Exception {
        Member seller = saveMember("paySeller8", Role.SELLER);
        Product product = saveProduct(seller, 25000, 10);
        priceTierRepository.save(new PriceTier(product, 3, 22000));
        priceTierRepository.save(new PriceTier(product, 5, 20000));
        priceTierRepository.save(new PriceTier(product, 10, 18000));

        Member leader = saveMember("payLeader4", Role.BUYER);
        GroupBuyTeam team = groupBuyTeamRepository.save(
                new GroupBuyTeam(product, leader, 10, LocalDateTime.now().plusDays(7)));
        teamParticipationRepository.save(new TeamParticipation(team, leader));

        // 시점 1: currentCount=1 (팀장만 참가한 시점) — maxParticipants(10) 기준으로 가장 큰 구간(18000)이 적용돼야 한다.
        mockMvc.perform(post("/api/payments")
                        .with(asUser(leader))
                        .contentType("application/json")
                        .content(toJson(Map.of("productId", product.getId(), "teamId", team.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amount").value(18000));

        // 시점 2: currentCount가 중간(1 < k < 10)까지 늘어난 시점 — 여전히 18000이어야 한다.
        Member middleMember = saveMember("payMember4a", Role.BUYER);
        for (int i = 0; i < 4; i++) {
            team.increaseParticipant();
        }
        team = groupBuyTeamRepository.save(team);
        teamParticipationRepository.save(new TeamParticipation(team, middleMember));

        mockMvc.perform(post("/api/payments")
                        .with(asUser(middleMember))
                        .contentType("application/json")
                        .content(toJson(Map.of("productId", product.getId(), "teamId", team.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amount").value(18000));

        // 시점 3: 정원이 다 찬 시점(currentCount == maxParticipants) — 여전히 18000이어야 한다(동일 팀, 고정 정원).
        Member lastMember = saveMember("payMember4b", Role.BUYER);
        for (int i = 0; i < 5; i++) {
            team.increaseParticipant();
        }
        team = groupBuyTeamRepository.save(team);
        teamParticipationRepository.save(new TeamParticipation(team, lastMember));

        mockMvc.perform(post("/api/payments")
                        .with(asUser(lastMember))
                        .contentType("application/json")
                        .content(toJson(Map.of("productId", product.getId(), "teamId", team.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amount").value(18000));
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
    @DisplayName("오픈예정(openAt이 미래)인 상품을 결제하려 하면 409 PRODUCT_NOT_YET_OPEN")
    void create_productNotYetOpen() throws Exception {
        Member seller = saveMember("paySeller5", Role.SELLER);
        Product product = productRepository.save(new Product(seller, "오픈예정상품", "설명", 25000, 10, null, false,
                ProductCategory.ETC, LocalDateTime.now().plusDays(3)));
        Member buyer = saveMember("payBuyer7", Role.BUYER);

        mockMvc.perform(post("/api/payments")
                        .with(asUser(buyer))
                        .contentType("application/json")
                        .content(toJson(Map.of("productId", product.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_YET_OPEN"));
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
    @DisplayName("결제 확정: PortOne 재조회 결과가 PAID+금액일치면 확정되고, 상태가 PAID로 바뀐다")
    void confirm_success() throws Exception {
        Member seller = saveMember("paySeller11", Role.SELLER);
        Product product = saveProduct(seller, 25000, 10);
        Member buyer = saveMember("payBuyer9", Role.BUYER);

        String createBody = mockMvc.perform(post("/api/payments")
                        .with(asUser(buyer))
                        .contentType("application/json")
                        .content(toJson(Map.of("productId", product.getId()))))
                .andReturn().getResponse().getContentAsString();
        Long paymentId = objectMapper.readTree(createBody).get("data").get("paymentId").asLong();

        mockMvc.perform(post("/api/payments/" + paymentId + "/confirm").with(asUser(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.amount").value(25000));
    }

    @Test
    @DisplayName("결제 확정: PortOne 재조회 금액이 요청 금액과 다르면 확정하지 않고 409를 반환한다")
    void confirm_amountMismatch_verificationFailed() throws Exception {
        Member seller = saveMember("paySeller12", Role.SELLER);
        Product product = saveProduct(seller, 25000, 10);
        Member buyer = saveMember("payBuyer10", Role.BUYER);

        String createBody = mockMvc.perform(post("/api/payments")
                        .with(asUser(buyer))
                        .contentType("application/json")
                        .content(toJson(Map.of("productId", product.getId()))))
                .andReturn().getResponse().getContentAsString();
        Long paymentId = objectMapper.readTree(createBody).get("data").get("paymentId").asLong();
        String pgPaymentId = objectMapper.readTree(createBody).get("data").get("pgPaymentId").asText();

        // 위변조 시나리오 재현 — 실제 PG 응답 금액이 우리가 기록한 요청 금액(25000)과 다르다.
        when(portOneClient.getPayment(pgPaymentId)).thenReturn(new PortOnePaymentDetail("PAID", 1));

        mockMvc.perform(post("/api/payments/" + paymentId + "/confirm").with(asUser(buyer)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_VERIFICATION_FAILED"));

        mockMvc.perform(get("/api/payments/" + paymentId).with(asUser(buyer)))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("결제 확정: PortOne 재조회 상태가 PAID가 아니면 확정하지 않고 409를 반환한다")
    void confirm_pgStatusNotPaid_verificationFailed() throws Exception {
        Member seller = saveMember("paySeller13", Role.SELLER);
        Product product = saveProduct(seller, 25000, 10);
        Member buyer = saveMember("payBuyer11", Role.BUYER);

        String createBody = mockMvc.perform(post("/api/payments")
                        .with(asUser(buyer))
                        .contentType("application/json")
                        .content(toJson(Map.of("productId", product.getId()))))
                .andReturn().getResponse().getContentAsString();
        Long paymentId = objectMapper.readTree(createBody).get("data").get("paymentId").asLong();
        String pgPaymentId = objectMapper.readTree(createBody).get("data").get("pgPaymentId").asText();

        when(portOneClient.getPayment(pgPaymentId)).thenReturn(new PortOnePaymentDetail("FAILED", 25000));

        mockMvc.perform(post("/api/payments/" + paymentId + "/confirm").with(asUser(buyer)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_VERIFICATION_FAILED"));

        mockMvc.perform(get("/api/payments/" + paymentId).with(asUser(buyer)))
                .andExpect(jsonPath("$.data.status").value("FAILED"));
    }

    @Test
    @DisplayName("결제 확정: PortOne 통신 실패 시 503 PAYMENT_GATEWAY_ERROR를 반환하고 상태는 그대로 PENDING이다")
    void confirm_portOneCallFails_gatewayError() throws Exception {
        Member seller = saveMember("paySeller14", Role.SELLER);
        Product product = saveProduct(seller, 25000, 10);
        Member buyer = saveMember("payBuyer12", Role.BUYER);

        String createBody = mockMvc.perform(post("/api/payments")
                        .with(asUser(buyer))
                        .contentType("application/json")
                        .content(toJson(Map.of("productId", product.getId()))))
                .andReturn().getResponse().getContentAsString();
        Long paymentId = objectMapper.readTree(createBody).get("data").get("paymentId").asLong();
        String pgPaymentId = objectMapper.readTree(createBody).get("data").get("pgPaymentId").asText();

        when(portOneClient.getPayment(pgPaymentId)).thenThrow(new RuntimeException("network error"));

        mockMvc.perform(post("/api/payments/" + paymentId + "/confirm").with(asUser(buyer)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_ERROR"));

        mockMvc.perform(get("/api/payments/" + paymentId).with(asUser(buyer)))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("결제 확정: 타인의 결제는 확정할 수 없다(403)")
    void confirm_notOwner_forbidden() throws Exception {
        Member seller = saveMember("paySeller15", Role.SELLER);
        Product product = saveProduct(seller, 25000, 10);
        Member buyer = saveMember("payBuyer13", Role.BUYER);
        Member stranger = saveMember("payStranger2", Role.BUYER);

        String createBody = mockMvc.perform(post("/api/payments")
                        .with(asUser(buyer))
                        .contentType("application/json")
                        .content(toJson(Map.of("productId", product.getId()))))
                .andReturn().getResponse().getContentAsString();
        Long paymentId = objectMapper.readTree(createBody).get("data").get("paymentId").asLong();

        mockMvc.perform(post("/api/payments/" + paymentId + "/confirm").with(asUser(stranger)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
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
