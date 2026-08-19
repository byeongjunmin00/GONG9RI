package com.gong9ri.gong9ri.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.NotificationRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.RefundRequestRepository;
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

    @Autowired
    private NotificationRepository notificationRepository;

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

    @Test
    @DisplayName("알림 목록 조회 성공")
    void notifications_success() throws Exception {
        Member buyer = saveMember("mpBuyer3", Role.BUYER);
        notificationRepository.save(new Notification(buyer, NotificationType.TEAM_REFUNDED,
                "참여하신 공구팀이 미성사되어 환불 처리되었습니다.", null));

        mockMvc.perform(get("/api/buyer/mypage/notifications").with(asUser(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].type").value("TEAM_REFUNDED"))
                .andExpect(jsonPath("$.data[0].isRead").value(false));
    }

    @Test
    @DisplayName("알림 목록은 본인 알림만 보이고 타인 알림은 안 보인다 (스코핑)")
    void notifications_scoping_onlyOwnNotifications() throws Exception {
        Member buyerA = saveMember("mpBuyerF1", Role.BUYER);
        Member buyerB = saveMember("mpBuyerF2", Role.BUYER);
        notificationRepository.save(new Notification(buyerA, NotificationType.TEAM_REFUNDED, "A 알림", null));
        notificationRepository.save(new Notification(buyerB, NotificationType.TEAM_REFUNDED, "B 알림", null));

        mockMvc.perform(get("/api/buyer/mypage/notifications").with(asUser(buyerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].message").value("A 알림"));
    }

    @Test
    @DisplayName("판매자 계정으로 알림 목록 조회 시 403 FORBIDDEN")
    void notifications_forbidden_seller() throws Exception {
        Member seller = saveMember("mpSeller6", Role.SELLER);

        mockMvc.perform(get("/api/buyer/mypage/notifications").with(asUser(seller)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("비로그인으로 알림 목록 조회 시 401 UNAUTHORIZED")
    void notifications_unauthorized() throws Exception {
        mockMvc.perform(get("/api/buyer/mypage/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("본인 알림을 읽음 처리하면 isRead가 true로 바뀐다")
    void markNotificationAsRead_success() throws Exception {
        Member buyer = saveMember("mpBuyer7", Role.BUYER);
        Notification notification = notificationRepository.save(
                new Notification(buyer, NotificationType.TEAM_REFUNDED, "읽음처리대상", null));

        mockMvc.perform(post("/api/buyer/mypage/notifications/" + notification.getId() + "/read")
                        .with(asUser(buyer)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/buyer/mypage/notifications").with(asUser(buyer)))
                .andExpect(jsonPath("$.data[0].isRead").value(true));
    }

    @Test
    @DisplayName("타인의 알림을 읽음 처리하려 하면 403 FORBIDDEN")
    void markNotificationAsRead_forbidden_notOwner() throws Exception {
        Member owner = saveMember("mpBuyer8", Role.BUYER);
        Member other = saveMember("mpBuyer9", Role.BUYER);
        Notification notification = notificationRepository.save(
                new Notification(owner, NotificationType.TEAM_REFUNDED, "소유자 알림", null));

        mockMvc.perform(post("/api/buyer/mypage/notifications/" + notification.getId() + "/read")
                        .with(asUser(other)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("존재하지 않는 알림을 읽음 처리하면 404 NOTIFICATION_NOT_FOUND")
    void markNotificationAsRead_notFound() throws Exception {
        Member buyer = saveMember("mpBuyer10", Role.BUYER);

        mockMvc.perform(post("/api/buyer/mypage/notifications/999999999/read").with(asUser(buyer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("모두 읽음 처리하면 본인의 안 읽은 알림이 전부 읽음으로 바뀐다")
    void markAllNotificationsAsRead_success() throws Exception {
        Member buyer = saveMember("mpBuyer11", Role.BUYER);
        notificationRepository.save(new Notification(buyer, NotificationType.TEAM_REFUNDED, "알림1", null));
        notificationRepository.save(new Notification(buyer, NotificationType.TEAM_REFUNDED, "알림2", null));

        mockMvc.perform(post("/api/buyer/mypage/notifications/read-all").with(asUser(buyer)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/buyer/mypage/notifications").with(asUser(buyer)))
                .andExpect(jsonPath("$.data[0].isRead").value(true))
                .andExpect(jsonPath("$.data[1].isRead").value(true));
    }

    @Test
    @DisplayName("환불 요청 목록 조회 성공(대기/승인/거절 포함)")
    void refundRequests_success() throws Exception {
        Member seller = saveMember("mpSeller7", Role.SELLER);
        Product product = saveProduct(seller, 10);
        Member buyer = saveMember("mpBuyer4", Role.BUYER);
        Payment payment = paymentRepository.save(new Payment(buyer, product, null, 25000));
        refundRequestRepository.save(new RefundRequest(payment, buyer, "단순 변심"));

        mockMvc.perform(get("/api/buyer/mypage/refund-requests").with(asUser(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].reason").value("단순 변심"));
    }

    @Test
    @DisplayName("환불 요청 목록은 본인 요청만 보이고 타인 요청은 안 보인다 (스코핑)")
    void refundRequests_scoping_onlyOwnRequests() throws Exception {
        Member seller = saveMember("mpSeller8", Role.SELLER);
        Product product = saveProduct(seller, 10);
        Member buyerA = saveMember("mpBuyerG1", Role.BUYER);
        Member buyerB = saveMember("mpBuyerG2", Role.BUYER);
        Payment paymentA = paymentRepository.save(new Payment(buyerA, product, null, 25000));
        Payment paymentB = paymentRepository.save(new Payment(buyerB, product, null, 30000));
        refundRequestRepository.save(new RefundRequest(paymentA, buyerA, "A 요청"));
        refundRequestRepository.save(new RefundRequest(paymentB, buyerB, "B 요청"));

        mockMvc.perform(get("/api/buyer/mypage/refund-requests").with(asUser(buyerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].reason").value("A 요청"));
    }

    @Test
    @DisplayName("판매자 계정으로 환불 요청 목록 조회 시 403 FORBIDDEN")
    void refundRequests_forbidden_seller() throws Exception {
        Member seller = saveMember("mpSeller9", Role.SELLER);

        mockMvc.perform(get("/api/buyer/mypage/refund-requests").with(asUser(seller)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("비로그인으로 환불 요청 목록 조회 시 401 UNAUTHORIZED")
    void refundRequests_unauthorized() throws Exception {
        mockMvc.perform(get("/api/buyer/mypage/refund-requests"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
