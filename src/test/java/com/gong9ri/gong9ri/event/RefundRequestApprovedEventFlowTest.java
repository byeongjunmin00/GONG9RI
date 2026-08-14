package com.gong9ri.gong9ri.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

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
import com.gong9ri.gong9ri.repository.NotificationRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.RefundRequestRepository;
import com.gong9ri.gong9ri.repository.TeamParticipationRepository;
import com.gong9ri.gong9ri.service.RefundRequestService;
import com.gong9ri.gong9ri.service.TeamService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.api.Test;

/**
 * 판매자 승인/상품별 자동환불 설정이 실제로 PortOne 결제취소(목)까지 이어지는 end-to-end 흐름을
 * 검증한다(docs/dev/ongoing/team-leave-and-refund-request.md 평가 기준 — "판매자가 환불 요청을
 * 승인하면 실제 PortOne 취소 경로를 타고 결제가 REFUNDED로 전환되는지"). {@code TeamDeadlineEventFlowTest}
 * 와 동일한 이유로 클래스 레벨 {@code @Transactional}을 쓰지 않는다 — AFTER_COMMIT 리스너가 "진짜 커밋"에
 * 반응하는지를 보는 테스트라 실제 커밋이 필요하고, {@code TeamService.leave}는 비관적 락(findByIdForUpdate)
 * 트랜잭션이라 이 테스트 스레드의 트랜잭션이 열려 있으면 락 대기로 블로킹된다.
 */
@SpringBootTest
class RefundRequestApprovedEventFlowTest {

    @MockitoBean
    private PortOneClient portOneClient;

    @BeforeEach
    void stubPortOneCancelSucceeds() {
        when(portOneClient.cancelPayment(anyString(), anyString()))
                .thenReturn(new PortOneCancelResult(PortOneCancelResult.SUCCEEDED));
    }

    private static final long ASYNC_WAIT_TIMEOUT_MS = 5_000L;
    private static final long ASYNC_WAIT_INTERVAL_MS = 100L;

    @Autowired
    private RefundRequestService refundRequestService;

    @Autowired
    private TeamService teamService;

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

    @Autowired
    private NotificationRepository notificationRepository;

    private final List<Long> refundRequestIdsToClean = new ArrayList<>();
    private final List<Long> paymentIdsToClean = new ArrayList<>();
    private final List<Long> teamParticipationIdsToClean = new ArrayList<>();
    private final List<Long> teamIdsToClean = new ArrayList<>();
    private final List<Long> productIdsToClean = new ArrayList<>();
    private final List<Long> memberIdsToClean = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        // 알림은 비동기 체인(AFTER_COMMIT) 끝에 생성될 수 있어, 미리 모은 id가 아니라 멤버id로 다시
        // 조회해서 지운다(TeamDeadlineEventFlowTest와 동일한 패턴) — 그래야 team/member를 지우기 전에
        // notification.related_team_id/member_id FK가 먼저 정리된다.
        for (Long memberId : memberIdsToClean) {
            notificationRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId)
                    .forEach(notification -> notificationRepository.deleteById(notification.getId()));
        }
        refundRequestIdsToClean.forEach(refundRequestRepository::deleteById);
        refundRequestIdsToClean.clear();
        paymentIdsToClean.forEach(paymentRepository::deleteById);
        paymentIdsToClean.clear();
        teamParticipationIdsToClean.forEach(teamParticipationRepository::deleteById);
        teamParticipationIdsToClean.clear();
        teamIdsToClean.forEach(groupBuyTeamRepository::deleteById);
        teamIdsToClean.clear();
        productIdsToClean.forEach(productRepository::deleteById);
        productIdsToClean.clear();
        memberIdsToClean.forEach(memberRepository::deleteById);
        memberIdsToClean.clear();
    }

    private Member saveMember(String username, Role role) {
        Member member = memberRepository.save(new Member(username, "pw", "테스트유저", username + "@test.com", role));
        memberIdsToClean.add(member.getId());
        return member;
    }

    private Product saveProduct(Member seller, boolean autoRefundOnCancel) {
        Product product = productRepository.save(
                new Product(seller, "환불흐름테스트상품", "설명", 10000, 10, null, autoRefundOnCancel));
        productIdsToClean.add(product.getId());
        return product;
    }

    private Payment savePaidPayment(Member buyer, Product product, GroupBuyTeam team) {
        Payment payment = new Payment(buyer, product, team, 10000, "pay_refund_flow_" + java.util.UUID.randomUUID());
        payment.confirm();
        payment = paymentRepository.save(payment);
        paymentIdsToClean.add(payment.getId());
        return payment;
    }

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

    @Test
    @DisplayName("판매자가 솔로 구매 환불 요청을 승인하면 커밋 이후 PortOne 취소(목, SUCCEEDED)를 거쳐 결제가 REFUNDED로 전환된다")
    void approve_commitSuccess_refundsPaymentViaPortOne() {
        Member seller = saveMember("flowSeller1", Role.SELLER);
        Product product = saveProduct(seller, false);
        Member buyer = saveMember("flowBuyer1", Role.BUYER);
        Payment payment = savePaidPayment(buyer, product, null);
        RefundRequest refundRequest = refundRequestRepository.save(new RefundRequest(payment, buyer, "단순 변심"));
        refundRequestIdsToClean.add(refundRequest.getId());

        refundRequestService.approve(new MemberUserDetails(seller), refundRequest.getId());

        waitUntil(() -> paymentRepository.findById(payment.getId()).orElseThrow().getStatus() == PaymentStatus.REFUNDED,
                "5초 내에 결제가 REFUNDED로 전환되지 않았다");

        RefundRequest refreshed = refundRequestRepository.findById(refundRequest.getId()).orElseThrow();
        assertEquals(RefundRequestStatus.APPROVED, refreshed.getStatus());
    }

    @Test
    @DisplayName("상품별 '참여 취소 시 자동 환불' 설정이 켜진 상태로 참여 취소하면, 판매자 승인 없이도"
            + " 커밋 이후 PortOne 취소(목)를 거쳐 결제가 REFUNDED로 전환된다")
    void leave_autoRefundOnCancel_commitSuccess_refundsPaymentViaPortOne() {
        Member seller = saveMember("flowSeller2", Role.SELLER);
        Product product = saveProduct(seller, true);
        Member leader = saveMember("flowLeader2", Role.BUYER);
        GroupBuyTeam team = groupBuyTeamRepository.save(
                new GroupBuyTeam(product, leader, 5, LocalDateTime.now().plusDays(7)));
        teamIdsToClean.add(team.getId());
        TeamParticipation leaderParticipation = teamParticipationRepository.save(new TeamParticipation(team, leader));
        teamParticipationIdsToClean.add(leaderParticipation.getId());

        Member joiner = saveMember("flowJoiner2", Role.BUYER);
        TeamParticipation joinerParticipation = teamParticipationRepository.save(new TeamParticipation(team, joiner));
        teamParticipationIdsToClean.add(joinerParticipation.getId());
        team.increaseParticipant();
        groupBuyTeamRepository.save(team);

        Payment payment = savePaidPayment(joiner, product, team);

        teamService.leave(new MemberUserDetails(joiner), team.getId());

        waitUntil(() -> paymentRepository.findById(payment.getId()).orElseThrow().getStatus() == PaymentStatus.REFUNDED,
                "5초 내에 결제가 REFUNDED로 전환되지 않았다(참여 취소 자동환불)");
        // 환불 완료 알림(TeamRefundedEvent, AFTER_COMMIT) 생성까지 끝나길 기다린다 — cleanUp이 team을
        // 지우기 전에 관련 알림이 이미 존재해야(그래서 함께 정리되어야) 함을 보장한다(TeamDeadlineEventFlowTest와 동일 이유).
        waitUntil(() -> !notificationRepository.findAllByMemberIdOrderByCreatedAtDesc(seller.getId()).isEmpty(),
                "5초 내에 판매자 환불 알림이 생성되지 않았다");

        List<RefundRequest> refundRequests = refundRequestRepository.findAllByRequesterIdWithPaymentAndProduct(joiner.getId());
        refundRequests.forEach(r -> refundRequestIdsToClean.add(r.getId()));
        assertEquals(1, refundRequests.size());
        assertEquals(RefundRequestStatus.APPROVED, refundRequests.get(0).getStatus());
    }
}
