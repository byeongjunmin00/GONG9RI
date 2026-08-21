package com.gong9ri.gong9ri.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.gong9ri.gong9ri.client.PortOneCancelResult;
import com.gong9ri.gong9ri.client.PortOneClient;
import com.gong9ri.gong9ri.client.PortOnePaymentDetail;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.InquiryAnswerRequest;
import com.gong9ri.gong9ri.dto.InquiryCreateRequest;
import com.gong9ri.gong9ri.dto.RefundRequestCreateRequest;
import com.gong9ri.gong9ri.dto.RefundRequestRejectRequest;
import com.gong9ri.gong9ri.dto.ReviewCreateRequest;
import com.gong9ri.gong9ri.dto.ShipmentUpdateRequest;
import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Notification;
import com.gong9ri.gong9ri.entity.NotificationType;
import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.RefundRejectionReason;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.entity.ShipmentStatus;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.InquiryRepository;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.NotificationRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.RefundRequestRepository;
import com.gong9ri.gong9ri.repository.ReviewRepository;
import com.gong9ri.gong9ri.repository.SellerRevenueSummaryRepository;
import com.gong9ri.gong9ri.repository.TeamParticipationRepository;
import com.gong9ri.gong9ri.service.InquiryService;
import com.gong9ri.gong9ri.service.PaymentService;
import com.gong9ri.gong9ri.service.RefundRequestService;
import com.gong9ri.gong9ri.service.ReviewService;
import com.gong9ri.gong9ri.service.SellerMypageService;
import com.gong9ri.gong9ri.service.TeamService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 알림 8종이 <b>올바른 수신자에게만</b> 생성되는지 검증한다 (docs/dev/ongoing/notification-types-expansion.md).
 *
 * <p>"알림이 만들어졌다"가 아니라 "누구에게 만들어졌는지"를 단언한다 — 이 기능의 핵심 리스크가 수신자를
 * 잘못 잡는 것(예: 문의 답변 알림이 답변한 판매자 자신에게 가는 것)이기 때문이다.
 *
 * <p><b>{@code @Transactional}을 붙이면 안 된다.</b> 알림은 {@code @TransactionalEventListener(AFTER_COMMIT)}
 * 로 만들어지는데, 테스트를 트랜잭션으로 감싸면 원본 트랜잭션이 끝내 커밋되지 않아 리스너가 아예 실행되지
 * 않는다. 그래서 {@code TeamDeadlineEventFlowTest}와 동일하게 실제로 커밋하고 {@code @AfterEach}에서
 * 직접 지운다. (리스너가 {@code @Async}가 아니라 동기라, 서비스 호출이 끝난 시점엔 알림이 이미 있다 —
 * 폴링 대기가 필요 없다.)
 */
@SpringBootTest
class NotificationTypesFlowTest {

    @Autowired
    private InquiryService inquiryService;
    @Autowired
    private ReviewService reviewService;
    @Autowired
    private RefundRequestService refundRequestService;
    @Autowired
    private TeamService teamService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private SellerMypageService sellerMypageService;

    // 결제 확정/환불 승인은 실제 PortOne HTTP 호출을 타므로 목으로 대체한다
    // (TeamDeadlineEventFlowTest와 동일한 방식).
    @MockitoBean
    private PortOneClient portOneClient;

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private GroupBuyTeamRepository groupBuyTeamRepository;
    @Autowired
    private TeamParticipationRepository teamParticipationRepository;
    @Autowired
    private InquiryRepository inquiryRepository;
    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private RefundRequestRepository refundRequestRepository;
    @Autowired
    private SellerRevenueSummaryRepository sellerRevenueSummaryRepository;

    private final List<Long> memberIdsToClean = new ArrayList<>();
    private final List<Long> productIdsToClean = new ArrayList<>();
    private final List<Long> teamIdsToClean = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long memberId : memberIdsToClean) {
            notificationRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId)
                    .forEach(n -> notificationRepository.deleteById(n.getId()));
        }
        refundRequestRepository.deleteAll(refundRequestRepository.findAll().stream()
                .filter(r -> memberIdsToClean.contains(r.getRequester().getId()))
                .toList());
        reviewRepository.deleteAll(reviewRepository.findAll().stream()
                .filter(r -> productIdsToClean.contains(r.getProduct().getId()))
                .toList());
        inquiryRepository.deleteAll(inquiryRepository.findAll().stream()
                .filter(i -> productIdsToClean.contains(i.getProduct().getId()))
                .toList());
        paymentRepository.deleteAll(paymentRepository.findAll().stream()
                .filter(p -> productIdsToClean.contains(p.getProduct().getId()))
                .toList());
        // 결제가 확정되면 판매자의 매출 집계 행(seller_revenue_summary)이 생긴다 — member를 참조하는
        // FK라 회원보다 먼저 지워야 한다(안 지우면 회원 삭제가 FK 위반으로 막힌다, 실제로 겪음).
        memberIdsToClean.forEach(sellerRevenueSummaryRepository::deleteBySellerId);
        teamIdsToClean.forEach(teamParticipationRepository::deleteByTeamId);
        teamIdsToClean.forEach(groupBuyTeamRepository::deleteById);
        productIdsToClean.forEach(productRepository::deleteById);
        memberIdsToClean.forEach(memberRepository::deleteById);
        teamIdsToClean.clear();
        productIdsToClean.clear();
        memberIdsToClean.clear();
    }

    // ---------- 헬퍼 ----------

    private Member saveMember(String username, Role role) {
        Member member = memberRepository.save(
                new Member(username, "pw", "알림테스트", username + "@test.com", role));
        memberIdsToClean.add(member.getId());
        return member;
    }

    private Product saveProduct(Member seller) {
        Product product = productRepository.save(
                new Product(seller, "알림테스트상품", "설명", 10000, 10, null));
        productIdsToClean.add(product.getId());
        return product;
    }

    private Payment savePaidPayment(Member buyer, Product product, GroupBuyTeam team) {
        return paymentRepository.save(new Payment(buyer, product, team, product.getBasePrice()));
    }

    private MemberUserDetails as(Member member) {
        return new MemberUserDetails(member);
    }

    private static final long ASYNC_WAIT_TIMEOUT_MS = 5_000L;
    private static final long ASYNC_WAIT_INTERVAL_MS = 50L;

    /**
     * 알림 리스너가 {@code @Async}라(커넥션 풀 고갈을 피하기 위한 필수 조치 —
     * {@code NotificationRequestedEventListener} 주석 참고) 서비스 호출 직후엔 아직 알림이 없을 수 있다.
     * 그래서 "생겼는지"는 폴링으로 기다린다.
     */
    private void waitUntil(BooleanSupplier condition, String failureMessage) {
        long deadline = System.currentTimeMillis() + ASYNC_WAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(ASYNC_WAIT_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("대기 중 인터럽트됨");
            }
        }
        fail(failureMessage);
    }

    /** 그 회원에게 해당 타입 알림이 정확히 1건 생길 때까지 기다린다. */
    private void assertReceivesExactlyOne(Member member, NotificationType type, String message) {
        waitUntil(() -> notificationsOf(member, type).size() == 1, message);
    }

    /**
     * 그 회원에게는 해당 타입 알림이 <b>가지 않아야</b> 한다는 단언.
     * 비동기라 "아직 안 온 것"과 "영영 안 오는 것"을 즉시 구분할 수 없어서, 먼저 와야 할 쪽이 도착한
     * 뒤(= 리스너가 이 이벤트를 이미 처리한 뒤)에 호출해야 의미가 있다 — 각 테스트가 그 순서를 지킨다.
     */
    private void assertReceivesNone(Member member, NotificationType type, String message) {
        assertTrue(notificationsOf(member, type).isEmpty(), message);
    }

    /** 그 회원이 받은 특정 타입 알림만 추린다 — "누구에게 갔는지"를 단언하기 위한 핵심 헬퍼. */
    private List<Notification> notificationsOf(Member member, NotificationType type) {
        return notificationRepository.findAllByMemberIdOrderByCreatedAtDesc(member.getId()).stream()
                .filter(n -> n.getType() == type)
                .toList();
    }

    // ---------- 문의 ----------

    @Test
    @DisplayName("문의를 등록하면 판매자에게만 INQUIRY_CREATED 알림이 간다(문의한 본인에겐 안 간다)")
    void inquiryCreatedNotifiesSellerOnly() {
        Member seller = saveMember("noti_inq_seller", Role.SELLER);
        Member buyer = saveMember("noti_inq_buyer", Role.BUYER);
        Product product = saveProduct(seller);

        inquiryService.create(as(buyer), product.getId(), new InquiryCreateRequest("배송 언제 오나요?"));

        assertReceivesExactlyOne(seller, NotificationType.INQUIRY_CREATED,
                "상품 판매자가 문의 알림을 받아야 한다");
        assertReceivesNone(buyer, NotificationType.INQUIRY_CREATED,
                "문의를 남긴 본인은 알림을 받으면 안 된다");
    }

    @Test
    @DisplayName("판매자가 답변하면 문의 작성자에게만 INQUIRY_ANSWERED 알림이 간다(답변한 판매자에겐 안 간다)")
    void inquiryAnsweredNotifiesAskerOnly() {
        Member seller = saveMember("noti_ans_seller", Role.SELLER);
        Member buyer = saveMember("noti_ans_buyer", Role.BUYER);
        Product product = saveProduct(seller);
        Long inquiryId = inquiryService.create(as(buyer), product.getId(),
                new InquiryCreateRequest("재고 있나요?")).inquiryId();

        inquiryService.registerAnswer(as(seller), inquiryId, new InquiryAnswerRequest("네 있습니다."));

        assertReceivesExactlyOne(buyer, NotificationType.INQUIRY_ANSWERED,
                "문의 작성자가 답변 알림을 받아야 한다");
        assertReceivesNone(seller, NotificationType.INQUIRY_ANSWERED,
                "답변한 판매자 본인은 알림을 받으면 안 된다");
    }

    // ---------- 리뷰 ----------

    @Test
    @DisplayName("리뷰를 작성하면 판매자에게만 REVIEW_CREATED 알림이 간다")
    void reviewCreatedNotifiesSellerOnly() {
        Member seller = saveMember("noti_rev_seller", Role.SELLER);
        Member buyer = saveMember("noti_rev_buyer", Role.BUYER);
        Product product = saveProduct(seller);
        savePaidPayment(buyer, product, null); // 리뷰 작성 자격(PAID 결제 이력)

        reviewService.create(as(buyer), product.getId(), new ReviewCreateRequest(5, "좋아요"));

        assertReceivesExactlyOne(seller, NotificationType.REVIEW_CREATED,
                "상품 판매자가 리뷰 알림을 받아야 한다");
        assertReceivesNone(buyer, NotificationType.REVIEW_CREATED,
                "리뷰를 쓴 본인은 알림을 받으면 안 된다");
    }

    // ---------- 환불 요청 ----------

    @Test
    @DisplayName("환불을 요청하면 판매자에게만 REFUND_REQUESTED 알림이 간다")
    void refundRequestedNotifiesSellerOnly() {
        Member seller = saveMember("noti_rr_seller", Role.SELLER);
        Member buyer = saveMember("noti_rr_buyer", Role.BUYER);
        Product product = saveProduct(seller);
        Payment payment = savePaidPayment(buyer, product, null);

        refundRequestService.createDirect(as(buyer), payment.getId(),
                new RefundRequestCreateRequest("단순 변심"));

        assertReceivesExactlyOne(seller, NotificationType.REFUND_REQUESTED,
                "판매자가 환불 요청 접수 알림을 받아야 한다(승인/거절 처리가 필요하다)");
        assertReceivesNone(buyer, NotificationType.REFUND_REQUESTED,
                "환불을 요청한 본인은 접수 알림을 받으면 안 된다");
    }

    @Test
    @DisplayName("판매자가 환불 요청을 거절하면 요청한 구매자에게만 REFUND_REQUEST_REJECTED 알림이 간다")
    void refundRejectedNotifiesRequesterOnly() {
        Member seller = saveMember("noti_rj_seller", Role.SELLER);
        Member buyer = saveMember("noti_rj_buyer", Role.BUYER);
        Product product = saveProduct(seller);
        Payment payment = savePaidPayment(buyer, product, null);
        Long refundRequestId = refundRequestService.createDirect(as(buyer), payment.getId(),
                new RefundRequestCreateRequest("단순 변심")).refundRequestId();

        refundRequestService.reject(as(seller), refundRequestId,
                new RefundRequestRejectRequest(RefundRejectionReason.ALREADY_SHIPPED));

        assertReceivesExactlyOne(buyer, NotificationType.REFUND_REQUEST_REJECTED,
                "환불을 요청한 구매자가 거절 알림을 받아야 한다");
        assertReceivesNone(seller, NotificationType.REFUND_REQUEST_REJECTED,
                "거절한 판매자 본인은 알림을 받으면 안 된다");
    }

    // ---------- 공구팀 성사 ----------

    @Test
    @DisplayName("공구팀이 정원을 채우면 참여자 전원과 판매자에게 TEAM_SUCCESS 알림이 간다")
    void teamSuccessNotifiesParticipantsAndSeller() {
        Member seller = saveMember("noti_ts_seller", Role.SELLER);
        Member leader = saveMember("noti_ts_leader", Role.BUYER);
        Member joiner = saveMember("noti_ts_joiner", Role.BUYER);
        Product product = saveProduct(seller);

        // 정원 2명짜리 팀 — 리더가 만들고 한 명이 더 들어오면 그 순간 SUCCESS가 된다.
        GroupBuyTeam team = groupBuyTeamRepository.save(
                new GroupBuyTeam(product, leader, 2, LocalDateTime.now().plusDays(7)));
        teamIdsToClean.add(team.getId());
        teamParticipationRepository.save(
                new com.gong9ri.gong9ri.entity.TeamParticipation(team, leader));

        teamService.join(as(joiner), team.getId());

        assertReceivesExactlyOne(leader, NotificationType.TEAM_SUCCESS,
                "먼저 참여해 있던 리더도 성사 알림을 받아야 한다");
        assertReceivesExactlyOne(joiner, NotificationType.TEAM_SUCCESS,
                "마지막으로 참여해 정원을 채운 사람도 성사 알림을 받아야 한다");
        assertReceivesExactlyOne(seller, NotificationType.TEAM_SUCCESS,
                "상품 판매자도 성사 알림을 받아야 한다");
    }

    // ---------- 결제 ----------

    @Test
    @DisplayName("결제가 확정되면 판매자에게만 PAYMENT_RECEIVED 알림이 간다")
    void paymentReceivedNotifiesSellerOnly() {
        Member seller = saveMember("noti_pay_seller", Role.SELLER);
        Member buyer = saveMember("noti_pay_buyer", Role.BUYER);
        Product product = saveProduct(seller);
        Payment pending = paymentRepository.save(
                new Payment(buyer, product, null, product.getBasePrice(), "pg-noti-test-1"));
        when(portOneClient.getPayment(anyString()))
                .thenReturn(new PortOnePaymentDetail("PAID", product.getBasePrice()));

        paymentService.confirm(as(buyer), pending.getId());

        assertReceivesExactlyOne(seller, NotificationType.PAYMENT_RECEIVED,
                "상품 판매자가 결제 발생 알림을 받아야 한다");
        assertReceivesNone(buyer, NotificationType.PAYMENT_RECEIVED,
                "결제한 구매자는 이 알림을 받지 않는다(결제 완료는 본인이 방금 한 행동이라 화면에 이미 보인다)");
    }

    @Test
    @DisplayName("결제 확정 경로가 둘이어도(클라이언트 confirm + 웹훅) 알림은 한 번만 생긴다")
    void paymentReceivedIsNotDuplicatedAcrossConfirmPaths() {
        Member seller = saveMember("noti_dup_seller", Role.SELLER);
        Member buyer = saveMember("noti_dup_buyer", Role.BUYER);
        Product product = saveProduct(seller);
        Payment pending = paymentRepository.save(
                new Payment(buyer, product, null, product.getBasePrice(), "pg-noti-test-2"));
        when(portOneClient.getPayment(anyString()))
                .thenReturn(new PortOnePaymentDetail("PAID", product.getBasePrice()));

        paymentService.confirm(as(buyer), pending.getId());
        paymentService.confirmByPgPaymentId("pg-noti-test-2"); // 웹훅이 뒤늦게 도착한 상황

        assertReceivesExactlyOne(seller, NotificationType.PAYMENT_RECEIVED,
                "같은 결제로 알림이 두 번 생기면 안 된다");
    }

    // ---------- 환불 승인 ----------

    @Test
    @DisplayName("판매자가 환불 요청을 승인하면 요청한 구매자에게만 REFUND_REQUEST_APPROVED 알림이 간다")
    void refundApprovedNotifiesRequesterOnly() {
        Member seller = saveMember("noti_ap_seller", Role.SELLER);
        Member buyer = saveMember("noti_ap_buyer", Role.BUYER);
        Product product = saveProduct(seller);
        Payment payment = savePaidPayment(buyer, product, null);
        Long refundRequestId = refundRequestService.createDirect(as(buyer), payment.getId(),
                new RefundRequestCreateRequest("단순 변심")).refundRequestId();
        when(portOneClient.cancelPayment(anyString(), anyString()))
                .thenReturn(new PortOneCancelResult(PortOneCancelResult.SUCCEEDED));

        refundRequestService.approve(as(seller), refundRequestId);

        assertReceivesExactlyOne(buyer, NotificationType.REFUND_REQUEST_APPROVED,
                "환불을 요청한 구매자가 승인 알림을 받아야 한다");
        assertReceivesNone(seller, NotificationType.REFUND_REQUEST_APPROVED,
                "승인한 판매자 본인은 알림을 받으면 안 된다");
    }

    // ---------- 배송 ----------

    @Test
    @DisplayName("판매자가 배송 단계를 바꾸면 구매자에게만 SHIPMENT_UPDATED 알림이 간다")
    void shipmentUpdatedNotifiesBuyerOnly() {
        Member seller = saveMember("noti_ship_seller", Role.SELLER);
        Member buyer = saveMember("noti_ship_buyer", Role.BUYER);
        Product product = saveProduct(seller);
        Payment payment = savePaidPayment(buyer, product, null);

        sellerMypageService.updateShipment(as(seller), payment.getId(),
                new ShipmentUpdateRequest(ShipmentStatus.SHIPPING_PREPARING, null, null));

        assertReceivesExactlyOne(buyer, NotificationType.SHIPMENT_UPDATED,
                "주문의 구매자가 배송 상태 변경 알림을 받아야 한다");
        assertReceivesNone(seller, NotificationType.SHIPMENT_UPDATED,
                "배송 상태를 바꾼 판매자 본인은 알림을 받으면 안 된다");
    }

    // ---------- 링크 ----------

    @Test
    @DisplayName("알림에는 이동할 링크가 함께 저장된다")
    void notificationCarriesLinkUrl() {
        Member seller = saveMember("noti_link_seller", Role.SELLER);
        Member buyer = saveMember("noti_link_buyer", Role.BUYER);
        Product product = saveProduct(seller);

        inquiryService.create(as(buyer), product.getId(), new InquiryCreateRequest("문의합니다"));

        assertReceivesExactlyOne(seller, NotificationType.INQUIRY_CREATED, "먼저 알림이 도착해야 한다");
        Notification notification = notificationsOf(seller, NotificationType.INQUIRY_CREATED).get(0);
        assertEquals("/product.html?id=" + product.getId(), notification.getLinkUrl(),
                "문의 알림은 그 상품 상세로 이동해야 한다");
    }
}
