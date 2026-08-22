package com.gong9ri.gong9ri.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.gong9ri.gong9ri.client.PortOneClient;
import com.gong9ri.gong9ri.client.PortOnePaymentDetail;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.PaymentStatus;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.entity.TeamParticipation;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.RefundRequestRepository;
import com.gong9ri.gong9ri.repository.SellerRevenueSummaryRepository;
import com.gong9ri.gong9ri.repository.TeamParticipationRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 미결제 참여 자동 만료(team/reservation-expiry)와 <b>진행 중인 결제</b>가 겹치는 경우.
 *
 * <p>만료 판정은 "{@code PAID} 결제가 있는가"만 본다. 그래서 사용자가 결제창에 10분 넘게 머물면
 * ({@code PENDING} 상태) 자리가 회수되는데, 그 뒤 결제가 확정되면 <b>돈은 나갔는데 팀에는 없는</b>
 * 상태가 된다 — 환불 요청도 생기지 않는다({@code cancelParticipation}이 PAID 결제만 보기 때문).
 *
 * <p>그래서 진행 중인(PENDING) 결제에는 별도 유예를 준다. 다만 <b>유예를 넘긴 뒤에 결제가 확정되는
 * 경우</b>는 여기서 막지 못한다 — 확정 시점에 자리를 되살리는 보정을 넣어봤더니 "팀에 참여하지 않고
 * 결제만 한" 기존 경로에서 정원이 늘어나 <b>공구 성사 여부가 달라졌다</b>(SellerRevenueSummaryTest가
 * 잡아냄). 성패 로직을 건드리는 변경이라 별도 판단이 필요해 이번 범위에서는 뺐다.
 */
@SpringBootTest
class TeamReservationExpiryPendingPaymentTest {

    @MockitoBean
    private PortOneClient portOneClient;
    @MockitoBean
    private NotificationPublisher notificationPublisher;

    @Autowired
    private TeamReservationExpiryService expiryService;
    @Autowired
    private PaymentService paymentService;
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
    private SellerRevenueSummaryRepository sellerRevenueSummaryRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private EntityManager entityManager;

    private final List<Long> memberIds = new ArrayList<>();
    private Long teamId;
    private Long productId;
    private Long paymentId;

    @BeforeEach
    void stubPortOnePaid() {
        when(portOneClient.getPayment(anyString()))
                .thenReturn(new PortOnePaymentDetail("PAID", 10_000));
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(s -> {
            if (paymentId != null) {
                refundRequestRepository.deleteByPayment_Product_Id(productId);
                paymentRepository.deleteById(paymentId);
            }
            if (teamId != null) {
                teamParticipationRepository.deleteByTeamId(teamId);
                groupBuyTeamRepository.deleteById(teamId);
            }
            if (productId != null) {
                productRepository.deleteById(productId);
            }
            memberIds.forEach(id -> {
                sellerRevenueSummaryRepository.findBySellerId(id)
                        .ifPresent(sellerRevenueSummaryRepository::delete);
                memberRepository.deleteById(id);
            });
            memberIds.clear();
        });
    }

    @Test
    @DisplayName("결제창에 머무는 중(PENDING)이면 10분이 지나도 자리를 뺏지 않는다")
    void inFlightPayment_keepsSeat() {
        setUpTeamWithPendingPayment(LocalDateTime.now());

        expiryService.processExpiredParticipations(teamId);

        assertTrue(teamParticipationRepository.existsByTeamIdAndMemberId(teamId, memberIds.get(1)),
                "결제를 진행 중인 사람의 자리를 회수하면, 결제가 확정됐을 때 돈만 나가고 팀에는 없는 상태가 된다");
    }

    @Test
    @DisplayName("버려진 결제 시도(유예를 넘긴 PENDING)는 자리를 회수한다 — 안 그러면 자리가 영구히 묶인다")
    void abandonedPayment_releasesSeat() {
        setUpTeamWithPendingPayment(LocalDateTime.now().minusHours(2));

        expiryService.processExpiredParticipations(teamId);

        assertFalse(teamParticipationRepository.existsByTeamIdAndMemberId(teamId, memberIds.get(1)),
                "이 프로젝트엔 방치된 PENDING 결제를 정리하는 장치가 없어, 무기한 유예하면 자리가 영원히 묶인다");
    }

    private void setUpTeamWithPendingPayment(LocalDateTime paymentRequestedAt) {
        transactionTemplate.executeWithoutResult(s -> {
            Member seller = memberRepository.save(new Member(
                    "expPendSeller", "pw", "판매자", "exp-pend-seller@test.com", Role.SELLER));
            Member buyer = memberRepository.save(new Member(
                    "expPendBuyer", "pw", "구매자", "exp-pend-buyer@test.com", Role.BUYER));
            memberIds.add(seller.getId());
            memberIds.add(buyer.getId());

            Product product = productRepository.save(
                    new Product(seller, "결제창 체류 재현 상품", "설명", 10_000, 5, null));
            productId = product.getId();

            GroupBuyTeam team = groupBuyTeamRepository.save(new GroupBuyTeam(
                    product, buyer, 5, LocalDateTime.now().plusDays(3)));
            teamId = team.getId();
            teamParticipationRepository.save(new TeamParticipation(team, buyer));

            // 결제창에 머무는 중 — PortOne 결제건은 만들어졌지만 아직 확정 전(PENDING).
            Payment payment = paymentRepository.save(
                    new Payment(buyer, product, team, 10_000, "pay_pending_expiry_test"));
            paymentId = payment.getId();
        });

        // joinedAt·paidAt은 @CreatedDate라 직접 못 넣는다 — 과거 시점으로 되돌린다.
        transactionTemplate.executeWithoutResult(s -> {
            entityManager.createQuery(
                            "UPDATE TeamParticipation p SET p.joinedAt = :past WHERE p.team.id = :teamId")
                    .setParameter("past", LocalDateTime.now().minusMinutes(30))
                    .setParameter("teamId", teamId)
                    .executeUpdate();
            entityManager.createQuery("UPDATE Payment p SET p.paidAt = :at WHERE p.id = :paymentId")
                    .setParameter("at", paymentRequestedAt)
                    .setParameter("paymentId", paymentId)
                    .executeUpdate();
        });
    }
}
