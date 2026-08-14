package com.gong9ri.gong9ri.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Notification;
import com.gong9ri.gong9ri.entity.NotificationType;
import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.RefundRequest;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.entity.SellerRevenueSummary;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.NotificationRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.RefundRequestRepository;
import com.gong9ri.gong9ri.repository.SellerRevenueSummaryRepository;
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
    private SellerRevenueSummaryRepository sellerRevenueSummaryRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RefundRequestRepository refundRequestRepository;

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
        return productRepository.save(new Product(seller, name, "설명", 25000, maxParticipants, null));
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
    @DisplayName("수익 현황은 seller_revenue_summary 요약 값을 그대로 반환한다(PAID 합산, REFUNDED는 건수만 별도)")
    void revenue_success_excludesRefundedFromTotal() throws Exception {
        // PAID/REFUNDED 집계 로직 자체(incrementPaid/applyRefund)는 service/SellerRevenueSummaryTest에서
        // 결제/환불 실 흐름(PaymentService.create, TeamDeadlineService.processDeadline)으로 검증한다.
        // 이 컨트롤러 테스트는 GET 엔드포인트가 요약 행 값을 정확히 그대로 응답하는지(HTTP 계층 wiring)만
        // 본다 — 그래서 요약 행을 직접 seed한다.
        Member seller = saveMember("mpSellerD1", Role.SELLER);
        saveProduct(seller, "제주 감귤 5kg", 10);
        sellerRevenueSummaryRepository.save(new SellerRevenueSummary(seller, 40000, 2L, 1L));

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

    @Test
    @DisplayName("알림 목록 조회 성공")
    void notifications_success() throws Exception {
        Member seller = saveMember("mpSellerF1", Role.SELLER);
        notificationRepository.save(new Notification(seller, NotificationType.TEAM_REFUNDED,
                "등록하신 상품의 공구팀이 미성사되어 환불 처리되었습니다.", null));

        mockMvc.perform(get("/api/seller/mypage/notifications").with(asUser(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].type").value("TEAM_REFUNDED"))
                .andExpect(jsonPath("$.data[0].isRead").value(false));
    }

    @Test
    @DisplayName("알림 목록은 본인 알림만 보이고 타인 알림은 안 보인다 (스코핑)")
    void notifications_scoping_onlyOwnNotifications() throws Exception {
        Member sellerA = saveMember("mpSellerF2", Role.SELLER);
        Member sellerB = saveMember("mpSellerF3", Role.SELLER);
        notificationRepository.save(new Notification(sellerA, NotificationType.TEAM_REFUNDED, "A 알림", null));
        notificationRepository.save(new Notification(sellerB, NotificationType.TEAM_REFUNDED, "B 알림", null));

        mockMvc.perform(get("/api/seller/mypage/notifications").with(asUser(sellerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].message").value("A 알림"));
    }

    @Test
    @DisplayName("구매자 계정으로 알림 목록 조회 시 403 FORBIDDEN")
    void notifications_forbidden_buyer() throws Exception {
        Member buyer = saveMember("mpBuyerC4", Role.BUYER);

        mockMvc.perform(get("/api/seller/mypage/notifications").with(asUser(buyer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("비로그인으로 알림 목록 조회 시 401 UNAUTHORIZED")
    void notifications_unauthorized() throws Exception {
        mockMvc.perform(get("/api/seller/mypage/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("내 상품에 대한 환불 요청 목록 조회 성공")
    void refundRequests_success() throws Exception {
        Member seller = saveMember("mpSellerG1", Role.SELLER);
        Product product = saveProduct(seller, "제주 감귤 5kg", 10);
        Member buyer = saveMember("mpBuyerH1", Role.BUYER);
        Payment payment = paymentRepository.save(new Payment(buyer, product, null, 25000));
        refundRequestRepository.save(new RefundRequest(payment, buyer, "단순 변심"));

        mockMvc.perform(get("/api/seller/mypage/refund-requests").with(asUser(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].reason").value("단순 변심"));
    }

    @Test
    @DisplayName("환불 요청 목록은 다른 판매자의 상품에 대한 요청은 안 보인다 (스코핑)")
    void refundRequests_scoping_onlyOwnProducts() throws Exception {
        Member sellerA = saveMember("mpSellerG2", Role.SELLER);
        Member sellerB = saveMember("mpSellerG3", Role.SELLER);
        Product productA = saveProduct(sellerA, "제주 감귤 5kg", 10);
        Product productB = saveProduct(sellerB, "경북 사과 3kg", 8);
        Member buyer = saveMember("mpBuyerH2", Role.BUYER);
        Payment paymentA = paymentRepository.save(new Payment(buyer, productA, null, 25000));
        Payment paymentB = paymentRepository.save(new Payment(buyer, productB, null, 20000));
        refundRequestRepository.save(new RefundRequest(paymentA, buyer, "A 상품 환불"));
        refundRequestRepository.save(new RefundRequest(paymentB, buyer, "B 상품 환불"));

        mockMvc.perform(get("/api/seller/mypage/refund-requests").with(asUser(sellerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].reason").value("A 상품 환불"));
    }

    @Test
    @DisplayName("구매자 계정으로 환불 요청 목록 조회 시 403 FORBIDDEN")
    void refundRequests_forbidden_buyer() throws Exception {
        Member buyer = saveMember("mpBuyerC5", Role.BUYER);

        mockMvc.perform(get("/api/seller/mypage/refund-requests").with(asUser(buyer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("비로그인으로 환불 요청 목록 조회 시 401 UNAUTHORIZED")
    void refundRequests_unauthorized() throws Exception {
        mockMvc.perform(get("/api/seller/mypage/refund-requests"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
