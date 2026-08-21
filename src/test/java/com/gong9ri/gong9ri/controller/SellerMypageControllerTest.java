package com.gong9ri.gong9ri.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.gong9ri.gong9ri.entity.TeamParticipation;
import com.gong9ri.gong9ri.entity.SellerRevenueSummary;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.NotificationRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.RefundRequestRepository;
import com.gong9ri.gong9ri.repository.TeamParticipationRepository;
import com.gong9ri.gong9ri.repository.SellerRevenueSummaryRepository;
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
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SellerMypageControllerTest {

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
    private SellerRevenueSummaryRepository sellerRevenueSummaryRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RefundRequestRepository refundRequestRepository;

    @Autowired
    private TeamParticipationRepository teamParticipationRepository;

    private Member saveMember(String username, Role role) {
        return saveMember(username, role, "테스트유저");
    }

    // 이름이 응답에 실리는지 보는 테스트에서는 전부 "테스트유저"면 검증이 안 되므로 이름을 지정한다.
    private Member saveMember(String username, Role role, String name) {
        Member member = new Member(username, "encoded-password", name, username + "@test.com", role);
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
        productRepository.save(new Product(seller, "제주 감귤 5kg", "설명", 25000, 10, "https://example.com/orange.jpg"));

        mockMvc.perform(get("/api/seller/mypage/products").with(asUser(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("제주 감귤 5kg"))
                .andExpect(jsonPath("$.data[0].imageUrl").value("https://example.com/orange.jpg"));
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
    @DisplayName("공구 참여 현황에 팀장과 참여자 이름이 참여 순서대로 함께 내려온다")
    void teams_includeLeaderAndParticipantNames() throws Exception {
        // 상품명과 인원 수만 내려가서 판매자가 "누가 참여했는지" 알 수 없던 걸 고친 회귀 방지
        // (2026-08-20 사용자 리포트). 값이 아니라 "필드가 응답에 있는지"가 핵심이다.
        Member seller = saveMember("mpSellerT1", Role.SELLER);
        Product product = saveProduct(seller, "제주 감귤 5kg", 5);
        Member leader = saveMember("mpLeaderT1", Role.BUYER, "김팀장");
        Member joiner = saveMember("mpJoinerT1", Role.BUYER, "박참여");
        GroupBuyTeam team = groupBuyTeamRepository.save(
                new GroupBuyTeam(product, leader, 5, LocalDateTime.now().plusDays(7)));
        teamParticipationRepository.save(new TeamParticipation(team, leader));
        teamParticipationRepository.save(new TeamParticipation(team, joiner));

        mockMvc.perform(get("/api/seller/mypage/teams").with(asUser(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].leaderName").value("김팀장"))
                .andExpect(jsonPath("$.data[0].participantNames.length()").value(2))
                .andExpect(jsonPath("$.data[0].participantNames[0]").value("김팀장"))
                .andExpect(jsonPath("$.data[0].participantNames[1]").value("박참여"));
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
                .andExpect(jsonPath("$.data.notifications.length()").value(1))
                .andExpect(jsonPath("$.data.notifications[0].type").value("TEAM_REFUNDED"))
                .andExpect(jsonPath("$.data.notifications[0].isRead").value(false));
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
                .andExpect(jsonPath("$.data.notifications.length()").value(1))
                .andExpect(jsonPath("$.data.notifications[0].message").value("A 알림"));
    }

    @Test
    @DisplayName("page/size가 잘못되면 400 VALIDATION_FAILED (PageRequest.of가 던지는 예외가 500으로 새던 버그)")
    void notifications_invalidPageOrSize_returns400() throws Exception {
        Member seller = saveMember("mpSellerInvalidPage", Role.SELLER);

        mockMvc.perform(get("/api/seller/mypage/notifications?page=-1").with(asUser(seller)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/seller/mypage/notifications?size=0").with(asUser(seller)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
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
    @DisplayName("본인 알림을 읽음 처리하면 isRead가 true로 바뀐다")
    void markNotificationAsRead_success() throws Exception {
        Member seller = saveMember("mpSeller7", Role.SELLER);
        Notification notification = notificationRepository.save(
                new Notification(seller, NotificationType.TEAM_REFUNDED, "읽음처리대상", null));

        mockMvc.perform(post("/api/seller/mypage/notifications/" + notification.getId() + "/read")
                        .with(asUser(seller)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/seller/mypage/notifications").with(asUser(seller)))
                .andExpect(jsonPath("$.data.notifications[0].isRead").value(true));
    }

    @Test
    @DisplayName("타인의 알림을 읽음 처리하려 하면 403 FORBIDDEN")
    void markNotificationAsRead_forbidden_notOwner() throws Exception {
        Member owner = saveMember("mpSeller8", Role.SELLER);
        Member other = saveMember("mpSeller9", Role.SELLER);
        Notification notification = notificationRepository.save(
                new Notification(owner, NotificationType.TEAM_REFUNDED, "소유자 알림", null));

        mockMvc.perform(post("/api/seller/mypage/notifications/" + notification.getId() + "/read")
                        .with(asUser(other)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("존재하지 않는 알림을 읽음 처리하려 하면 404 NOTIFICATION_NOT_FOUND")
    void markNotificationAsRead_notFound() throws Exception {
        Member seller = saveMember("mpSeller11", Role.SELLER);

        mockMvc.perform(post("/api/seller/mypage/notifications/999999999/read").with(asUser(seller)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("모두 읽음 처리하면 본인의 안 읽은 알림이 전부 읽음으로 바뀐다")
    void markAllNotificationsAsRead_success() throws Exception {
        Member seller = saveMember("mpSeller10", Role.SELLER);
        notificationRepository.save(new Notification(seller, NotificationType.TEAM_REFUNDED, "알림1", null));
        notificationRepository.save(new Notification(seller, NotificationType.TEAM_REFUNDED, "알림2", null));

        mockMvc.perform(post("/api/seller/mypage/notifications/read-all").with(asUser(seller)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/seller/mypage/notifications").with(asUser(seller)))
                .andExpect(jsonPath("$.data.notifications[0].isRead").value(true))
                .andExpect(jsonPath("$.data.notifications[1].isRead").value(true));
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
    @DisplayName("환불 요청 목록에 요청자 정보가 함께 내려온다")
    void refundRequests_includeRequester() throws Exception {
        // 상품명만 보여서 판매자가 "누가 환불을 요청했는지" 알 수 없던 걸 고친 회귀 방지
        // (2026-08-20 사용자 리포트). 조회 쿼리는 이미 requester를 fetch join하고 있었는데
        // DTO가 그 값을 버리고 있었다.
        Member seller = saveMember("mpSellerR1", Role.SELLER);
        Product product = saveProduct(seller, "제주 감귤 5kg", 10);
        Member buyer = saveMember("mpBuyerR1", Role.BUYER, "이환불");
        Payment payment = paymentRepository.save(new Payment(buyer, product, null, 25000));
        refundRequestRepository.save(new RefundRequest(payment, buyer, "단순 변심"));

        mockMvc.perform(get("/api/seller/mypage/refund-requests").with(asUser(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].requesterName").value("이환불"))
                .andExpect(jsonPath("$.data[0].requesterId").value(buyer.getId()));
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

    @Test
    @DisplayName("판매자가 주문·배송 내역을 조회하면 구매자 정보 및 배송 준비 상태가 함께 반환된다")
    void orders_asSeller_returnsSellerOrdersWithBuyerInfo() throws Exception {
        Member seller = saveMember("orderSeller1", Role.SELLER);
        Product product = saveProduct(seller, "유기농 딸기 1kg", 5);
        Member buyer = saveMember("orderBuyer1", Role.BUYER, "홍길동");
        Payment payment = paymentRepository.save(new Payment(buyer, product, null, 15000));

        mockMvc.perform(get("/api/seller/mypage/orders").with(asUser(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].buyerName").value("홍길동"))
                .andExpect(jsonPath("$.data[0].buyerEmail").value("orderBuyer1@test.com"))
                .andExpect(jsonPath("$.data[0].productName").value("유기농 딸기 1kg"))
                .andExpect(jsonPath("$.data[0].amount").value(15000))
                .andExpect(jsonPath("$.data[0].preparationStatus").value("PREPARING"))
                .andExpect(jsonPath("$.data[0].preparationStatusLabel").value("🚚 배송 준비 중"));
    }

    @Test
    @DisplayName("주문·배송 내역은 다른 판매자의 결제 건은 안 보인다 (스코핑)")
    void orders_scoping_onlyOwnSalesPayments() throws Exception {
        Member sellerA = saveMember("orderSeller2", Role.SELLER);
        Member sellerB = saveMember("orderSeller3", Role.SELLER);
        Product productA = saveProduct(sellerA, "제주 감귤 5kg", 10);
        Product productB = saveProduct(sellerB, "경북 사과 3kg", 8);
        Member buyer = saveMember("orderBuyer2", Role.BUYER, "김철수");
        paymentRepository.save(new Payment(buyer, productA, null, 20000));
        paymentRepository.save(new Payment(buyer, productB, null, 10000));

        mockMvc.perform(get("/api/seller/mypage/orders").with(asUser(sellerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].productName").value("제주 감귤 5kg"));
    }

    @Test
    @DisplayName("구매자 계정으로 주문·배송 내역 조회 시 403 FORBIDDEN")
    void orders_forbidden_buyer() throws Exception {
        Member buyer = saveMember("mpBuyerC6", Role.BUYER);

        mockMvc.perform(get("/api/seller/mypage/orders").with(asUser(buyer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("비로그인으로 주문·배송 내역 조회 시 401 UNAUTHORIZED")
    void orders_unauthorized() throws Exception {
        mockMvc.perform(get("/api/seller/mypage/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("판매자가 배송 단계를 바꾸면 200과 함께 변경된 값이 반환된다")
    void updateShipment_asSeller_success() throws Exception {
        Member seller = saveMember("shipSeller1", Role.SELLER);
        Product product = saveProduct(seller, "유기농 딸기 1kg", 5);
        Member buyer = saveMember("shipBuyer1", Role.BUYER);
        Payment payment = paymentRepository.save(new Payment(buyer, product, null, 15000));

        mockMvc.perform(patch("/api/seller/mypage/orders/" + payment.getId() + "/shipment")
                        .with(asUser(seller))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                Map.of("shipmentStatus", "SHIPPING_PREPARING"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shipmentStatus").value("SHIPPING_PREPARING"))
                .andExpect(jsonPath("$.data.shipmentStatusLabel").value("배송 준비중"));
    }

    @Test
    @DisplayName("배송중으로 바꾸는데 송장번호가 없으면 400 TRACKING_NUMBER_REQUIRED")
    void updateShipment_toInTransitWithoutTrackingNumber_rejected() throws Exception {
        Member seller = saveMember("shipSeller2", Role.SELLER);
        Product product = saveProduct(seller, "유기농 딸기 1kg", 5);
        Member buyer = saveMember("shipBuyer2", Role.BUYER);
        Payment payment = paymentRepository.save(new Payment(buyer, product, null, 15000));

        mockMvc.perform(patch("/api/seller/mypage/orders/" + payment.getId() + "/shipment")
                        .with(asUser(seller))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("shipmentStatus", "IN_TRANSIT"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRACKING_NUMBER_REQUIRED"));
    }

    @Test
    @DisplayName("송장번호와 함께 배송중으로 바꾸면 200이고 택배사·송장번호가 저장된다")
    void updateShipment_toInTransitWithTrackingNumber_success() throws Exception {
        Member seller = saveMember("shipSeller3", Role.SELLER);
        Product product = saveProduct(seller, "유기농 딸기 1kg", 5);
        Member buyer = saveMember("shipBuyer3", Role.BUYER);
        Payment payment = paymentRepository.save(new Payment(buyer, product, null, 15000));

        mockMvc.perform(patch("/api/seller/mypage/orders/" + payment.getId() + "/shipment")
                        .with(asUser(seller))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shipmentStatus", "IN_TRANSIT",
                                "trackingCarrier", "CJ대한통운",
                                "trackingNumber", "123456789012"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shipmentStatus").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.data.trackingCarrier").value("CJ대한통운"))
                .andExpect(jsonPath("$.data.trackingNumber").value("123456789012"));
    }

    @Test
    @DisplayName("환불된 주문의 배송 단계를 바꾸려 하면 409 SHIPMENT_STATUS_NOT_APPLICABLE")
    void updateShipment_refundedPayment_rejected() throws Exception {
        Member seller = saveMember("shipSeller4", Role.SELLER);
        Product product = saveProduct(seller, "유기농 딸기 1kg", 5);
        Member buyer = saveMember("shipBuyer4", Role.BUYER);
        Payment payment = new Payment(buyer, product, null, 15000);
        payment.refund();
        paymentRepository.save(payment);

        mockMvc.perform(patch("/api/seller/mypage/orders/" + payment.getId() + "/shipment")
                        .with(asUser(seller))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                Map.of("shipmentStatus", "SHIPPING_PREPARING"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHIPMENT_STATUS_NOT_APPLICABLE"));
    }

    @Test
    @DisplayName("다른 판매자 상품 주문의 배송 단계를 바꾸려 하면 403 FORBIDDEN")
    void updateShipment_otherSellersPayment_forbidden() throws Exception {
        Member sellerA = saveMember("shipSeller5", Role.SELLER);
        Member sellerB = saveMember("shipSeller6", Role.SELLER);
        Product product = saveProduct(sellerA, "유기농 딸기 1kg", 5);
        Member buyer = saveMember("shipBuyer5", Role.BUYER);
        Payment payment = paymentRepository.save(new Payment(buyer, product, null, 15000));

        mockMvc.perform(patch("/api/seller/mypage/orders/" + payment.getId() + "/shipment")
                        .with(asUser(sellerB))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                Map.of("shipmentStatus", "SHIPPING_PREPARING"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("구매자 계정으로 배송 단계 변경 시도 시 403 FORBIDDEN")
    void updateShipment_asBuyer_forbidden() throws Exception {
        Member seller = saveMember("shipSeller7", Role.SELLER);
        Product product = saveProduct(seller, "유기농 딸기 1kg", 5);
        Member buyer = saveMember("shipBuyer6", Role.BUYER);
        Payment payment = paymentRepository.save(new Payment(buyer, product, null, 15000));

        mockMvc.perform(patch("/api/seller/mypage/orders/" + payment.getId() + "/shipment")
                        .with(asUser(buyer))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                Map.of("shipmentStatus", "SHIPPING_PREPARING"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("비로그인으로 배송 단계 변경 시도 시 401 UNAUTHORIZED")
    void updateShipment_unauthorized() throws Exception {
        mockMvc.perform(patch("/api/seller/mypage/orders/1/shipment")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                Map.of("shipmentStatus", "SHIPPING_PREPARING"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("주문·배송 내역은 결제 미확정(PENDING)·실패(FAILED) 건은 제외한다")
    void orders_excludesPendingAndFailedPayments() throws Exception {
        Member seller = saveMember("orderSeller4", Role.SELLER);
        Product product = saveProduct(seller, "제주 감귤 5kg", 10);
        Member buyer = saveMember("orderBuyer3", Role.BUYER, "이영희");

        Payment pending = new Payment(buyer, product, null, 9000, "pg-pending-1");
        paymentRepository.save(pending);

        Payment failed = new Payment(buyer, product, null, 8000, "pg-failed-1");
        failed.fail();
        paymentRepository.save(failed);

        Payment paid = paymentRepository.save(new Payment(buyer, product, null, 15000));

        mockMvc.perform(get("/api/seller/mypage/orders").with(asUser(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].paymentId").value(paid.getId()));
    }
}
