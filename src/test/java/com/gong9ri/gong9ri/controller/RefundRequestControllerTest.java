package com.gong9ri.gong9ri.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.gong9ri.gong9ri.client.PortOneCancelResult;
import com.gong9ri.gong9ri.client.PortOneClient;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.PaymentStatus;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.RefundRequest;
import com.gong9ri.gong9ri.entity.RefundRequestStatus;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.entity.TeamParticipation;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.RefundRequestRepository;
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
 * {@code PortOneClient}를 {@code @MockitoBean}으로 대체한다(docs/dev/ongoing/
 * team-leave-and-refund-request.md, PaymentControllerTest와 동일 패턴) — 승인(approve)이 발행하는
 * {@code RefundRequestApprovedEvent}는 AFTER_COMMIT에만 소비되므로, 이 클래스가 클래스 레벨
 * {@code @Transactional}(각 테스트 종료 시 롤백, 실제 커밋 없음)인 이상 그 비동기 취소 호출까지는
 * 이 테스트에서 검증하지 않는다 — 여기서는 승인/거절의 동기 부분(상태 전이·권한·검증)만 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RefundRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    @Autowired
    private RefundRequestRepository refundRequestRepository;

    @MockitoBean
    private PortOneClient portOneClient;

    @BeforeEach
    void stubPortOneCancelSucceeds() {
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

    private Product saveProduct(Member seller) {
        return productRepository.save(new Product(seller, "제주 감귤 5kg", "설명", 25000, 10, null));
    }

    // 팀이 없는(team=null) 솔로 구매 PAID 결제.
    private Payment saveSoloPayment(Member buyer, Product product) {
        Payment payment = new Payment(buyer, product, null, 25000, "pay_refund_" + java.util.UUID.randomUUID());
        payment.confirm();
        return paymentRepository.save(payment);
    }

    // 팀이 딸린 PAID 결제.
    private Payment saveTeamPayment(Member buyer, Product product, GroupBuyTeam team) {
        Payment payment = new Payment(buyer, product, team, 25000, "pay_refund_" + java.util.UUID.randomUUID());
        payment.confirm();
        return paymentRepository.save(payment);
    }

    private GroupBuyTeam saveTeam(Product product, Member leader) {
        GroupBuyTeam team = groupBuyTeamRepository.save(
                new GroupBuyTeam(product, leader, 5, LocalDateTime.now().plusDays(7)));
        teamParticipationRepository.save(new TeamParticipation(team, leader));
        return team;
    }

    private String toJson(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    // ---------- 구매자 직접 환불 요청(POST /api/payments/{paymentId}/refund-requests) ----------

    @Test
    @DisplayName("솔로 구매 PAID 결제는 사유를 입력해 직접 환불 요청 가능(201, PENDING)")
    void create_success() throws Exception {
        Member seller = saveMember("refundSeller1", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("refundBuyer1", Role.BUYER);
        Payment payment = saveSoloPayment(buyer, product);

        mockMvc.perform(post("/api/payments/" + payment.getId() + "/refund-requests")
                        .with(asUser(buyer))
                        .contentType("application/json")
                        .content(toJson(Map.of("reason", "단순 변심"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.reason").value("단순 변심"))
                .andExpect(jsonPath("$.data.paymentId").value(payment.getId()));
    }

    @Test
    @DisplayName("팀이 딸린 결제는 직접 환불 요청 시 409 TEAM_PAYMENT_REFUND_NOT_ALLOWED")
    void create_teamPayment_conflict() throws Exception {
        Member seller = saveMember("refundSeller2", Role.SELLER);
        Product product = saveProduct(seller);
        Member leader = saveMember("refundLeader2", Role.BUYER);
        GroupBuyTeam team = saveTeam(product, leader);
        Payment payment = saveTeamPayment(leader, product, team);

        mockMvc.perform(post("/api/payments/" + payment.getId() + "/refund-requests")
                        .with(asUser(leader))
                        .contentType("application/json")
                        .content(toJson(Map.of("reason", "단순 변심"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEAM_PAYMENT_REFUND_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("본인 결제가 아니면 직접 환불 요청 시 403 FORBIDDEN")
    void create_notOwner_forbidden() throws Exception {
        Member seller = saveMember("refundSeller3", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("refundBuyer3", Role.BUYER);
        Payment payment = saveSoloPayment(buyer, product);
        Member otherBuyer = saveMember("refundBuyer3b", Role.BUYER);

        mockMvc.perform(post("/api/payments/" + payment.getId() + "/refund-requests")
                        .with(asUser(otherBuyer))
                        .contentType("application/json")
                        .content(toJson(Map.of("reason", "단순 변심"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("PAID가 아닌 결제는 직접 환불 요청 시 409 PAYMENT_NOT_REFUNDABLE")
    void create_notPaid_conflict() throws Exception {
        Member seller = saveMember("refundSeller4", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("refundBuyer4", Role.BUYER);
        Payment payment = paymentRepository.save(
                new Payment(buyer, product, null, 25000, "pay_refund_pending_1")); // PENDING(미확정)

        mockMvc.perform(post("/api/payments/" + payment.getId() + "/refund-requests")
                        .with(asUser(buyer))
                        .contentType("application/json")
                        .content(toJson(Map.of("reason", "단순 변심"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_REFUNDABLE"));
    }

    @Test
    @DisplayName("같은 결제에 이미 대기 중인 환불 요청이 있으면 409 REFUND_REQUEST_ALREADY_EXISTS")
    void create_duplicatePending_conflict() throws Exception {
        Member seller = saveMember("refundSeller5", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("refundBuyer5", Role.BUYER);
        Payment payment = saveSoloPayment(buyer, product);
        refundRequestRepository.save(new RefundRequest(payment, buyer, "먼저 요청함"));

        mockMvc.perform(post("/api/payments/" + payment.getId() + "/refund-requests")
                        .with(asUser(buyer))
                        .contentType("application/json")
                        .content(toJson(Map.of("reason", "또 요청"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_REQUEST_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("사유 없이 직접 환불 요청 시 400 VALIDATION_FAILED")
    void create_missingReason_validationFailed() throws Exception {
        Member seller = saveMember("refundSeller6", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("refundBuyer6", Role.BUYER);
        Payment payment = saveSoloPayment(buyer, product);

        mockMvc.perform(post("/api/payments/" + payment.getId() + "/refund-requests")
                        .with(asUser(buyer))
                        .contentType("application/json")
                        .content(toJson(Map.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("존재하지 않는 결제로 직접 환불 요청 시 404 PAYMENT_NOT_FOUND")
    void create_paymentNotFound() throws Exception {
        Member buyer = saveMember("refundBuyer7", Role.BUYER);

        mockMvc.perform(post("/api/payments/999999/refund-requests")
                        .with(asUser(buyer))
                        .contentType("application/json")
                        .content(toJson(Map.of("reason", "단순 변심"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("판매자 계정으로 직접 환불 요청 시 403 FORBIDDEN")
    void create_forbidden_seller() throws Exception {
        Member seller = saveMember("refundSeller8", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("refundBuyer8", Role.BUYER);
        Payment payment = saveSoloPayment(buyer, product);

        mockMvc.perform(post("/api/payments/" + payment.getId() + "/refund-requests")
                        .with(asUser(seller))
                        .contentType("application/json")
                        .content(toJson(Map.of("reason", "단순 변심"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("비로그인으로 직접 환불 요청 시 401 UNAUTHORIZED")
    void create_unauthorized() throws Exception {
        Member seller = saveMember("refundSeller9", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("refundBuyer9", Role.BUYER);
        Payment payment = saveSoloPayment(buyer, product);

        mockMvc.perform(post("/api/payments/" + payment.getId() + "/refund-requests")
                        .contentType("application/json")
                        .content(toJson(Map.of("reason", "단순 변심"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // ---------- 판매자 승인/거절 ----------

    @Test
    @DisplayName("판매자가 본인 상품의 환불 요청을 승인하면 200, APPROVED로 전환된다")
    void approve_success() throws Exception {
        Member seller = saveMember("refundSeller10", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("refundBuyer10", Role.BUYER);
        Payment payment = saveSoloPayment(buyer, product);
        RefundRequest refundRequest = refundRequestRepository.save(new RefundRequest(payment, buyer, "단순 변심"));

        mockMvc.perform(post("/api/refund-requests/" + refundRequest.getId() + "/approve").with(asUser(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @DisplayName("본인 상품이 아닌 환불 요청을 승인하려 하면 403 FORBIDDEN")
    void approve_notOwner_forbidden() throws Exception {
        Member seller = saveMember("refundSeller11", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("refundBuyer11", Role.BUYER);
        Payment payment = saveSoloPayment(buyer, product);
        RefundRequest refundRequest = refundRequestRepository.save(new RefundRequest(payment, buyer, "단순 변심"));
        Member otherSeller = saveMember("refundSeller11b", Role.SELLER);

        mockMvc.perform(post("/api/refund-requests/" + refundRequest.getId() + "/approve").with(asUser(otherSeller)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("존재하지 않는 환불 요청 승인 시 404 REFUND_REQUEST_NOT_FOUND")
    void approve_notFound() throws Exception {
        Member seller = saveMember("refundSeller12", Role.SELLER);

        mockMvc.perform(post("/api/refund-requests/999999/approve").with(asUser(seller)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REFUND_REQUEST_NOT_FOUND"));
    }

    @Test
    @DisplayName("이미 처리된 환불 요청을 다시 승인하면 409 REFUND_REQUEST_ALREADY_DECIDED")
    void approve_alreadyDecided_conflict() throws Exception {
        Member seller = saveMember("refundSeller13", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("refundBuyer13", Role.BUYER);
        Payment payment = saveSoloPayment(buyer, product);
        RefundRequest refundRequest = refundRequestRepository.save(new RefundRequest(payment, buyer, "단순 변심"));

        mockMvc.perform(post("/api/refund-requests/" + refundRequest.getId() + "/approve").with(asUser(seller)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/refund-requests/" + refundRequest.getId() + "/approve").with(asUser(seller)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_REQUEST_ALREADY_DECIDED"));
    }

    @Test
    @DisplayName("구매자 계정으로 승인 시도 시 403 FORBIDDEN")
    void approve_forbidden_buyer() throws Exception {
        Member seller = saveMember("refundSeller14", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("refundBuyer14", Role.BUYER);
        Payment payment = saveSoloPayment(buyer, product);
        RefundRequest refundRequest = refundRequestRepository.save(new RefundRequest(payment, buyer, "단순 변심"));

        mockMvc.perform(post("/api/refund-requests/" + refundRequest.getId() + "/approve").with(asUser(buyer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("비로그인으로 승인 시도 시 401 UNAUTHORIZED")
    void approve_unauthorized() throws Exception {
        Member seller = saveMember("refundSeller15", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("refundBuyer15", Role.BUYER);
        Payment payment = saveSoloPayment(buyer, product);
        RefundRequest refundRequest = refundRequestRepository.save(new RefundRequest(payment, buyer, "단순 변심"));

        mockMvc.perform(post("/api/refund-requests/" + refundRequest.getId() + "/approve"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("판매자가 거절하면 200, REJECTED로 전환되고 사유 템플릿 설명이 남으며 결제는 PAID로 유지된다")
    void reject_success() throws Exception {
        Member seller = saveMember("refundSeller16", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("refundBuyer16", Role.BUYER);
        Payment payment = saveSoloPayment(buyer, product);
        RefundRequest refundRequest = refundRequestRepository.save(new RefundRequest(payment, buyer, "단순 변심"));

        mockMvc.perform(post("/api/refund-requests/" + refundRequest.getId() + "/reject")
                        .with(asUser(seller))
                        .contentType("application/json")
                        .content(toJson(Map.of("rejectionReason", "ALREADY_SHIPPED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectionReason").value("상품이 이미 발송되어 환불이 어렵습니다."));

        Payment refreshedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.PAID, refreshedPayment.getStatus());
    }

    @Test
    @DisplayName("거절 사유 없이 요청 시 400 VALIDATION_FAILED")
    void reject_missingReason_validationFailed() throws Exception {
        Member seller = saveMember("refundSeller17", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("refundBuyer17", Role.BUYER);
        Payment payment = saveSoloPayment(buyer, product);
        RefundRequest refundRequest = refundRequestRepository.save(new RefundRequest(payment, buyer, "단순 변심"));

        mockMvc.perform(post("/api/refund-requests/" + refundRequest.getId() + "/reject")
                        .with(asUser(seller))
                        .contentType("application/json")
                        .content(toJson(Map.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("본인 상품이 아닌 환불 요청 거절 시 403 FORBIDDEN")
    void reject_notOwner_forbidden() throws Exception {
        Member seller = saveMember("refundSeller18", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("refundBuyer18", Role.BUYER);
        Payment payment = saveSoloPayment(buyer, product);
        RefundRequest refundRequest = refundRequestRepository.save(new RefundRequest(payment, buyer, "단순 변심"));
        Member otherSeller = saveMember("refundSeller18b", Role.SELLER);

        mockMvc.perform(post("/api/refund-requests/" + refundRequest.getId() + "/reject")
                        .with(asUser(otherSeller))
                        .contentType("application/json")
                        .content(toJson(Map.of("rejectionReason", "OTHER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("이미 처리된 환불 요청을 거절하면 409 REFUND_REQUEST_ALREADY_DECIDED")
    void reject_alreadyDecided_conflict() throws Exception {
        Member seller = saveMember("refundSeller19", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("refundBuyer19", Role.BUYER);
        Payment payment = saveSoloPayment(buyer, product);
        RefundRequest refundRequest = refundRequestRepository.save(new RefundRequest(payment, buyer, "단순 변심"));

        mockMvc.perform(post("/api/refund-requests/" + refundRequest.getId() + "/approve").with(asUser(seller)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/refund-requests/" + refundRequest.getId() + "/reject")
                        .with(asUser(seller))
                        .contentType("application/json")
                        .content(toJson(Map.of("rejectionReason", "OTHER"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_REQUEST_ALREADY_DECIDED"));
    }

    @Test
    @DisplayName("RefundRequestStatus 참고 — PENDING으로 생성된 요청은 처리 전 상태를 유지한다")
    void newRefundRequest_isPending() {
        Member seller = saveMember("refundSeller20", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("refundBuyer20", Role.BUYER);
        Payment payment = saveSoloPayment(buyer, product);
        RefundRequest refundRequest = refundRequestRepository.save(new RefundRequest(payment, buyer, "단순 변심"));

        org.junit.jupiter.api.Assertions.assertEquals(RefundRequestStatus.PENDING, refundRequest.getStatus());
    }
}
