package com.gong9ri.gong9ri.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.gong9ri.gong9ri.client.PortOneCancelResult;
import com.gong9ri.gong9ri.client.PortOneClient;
import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Notification;
import com.gong9ri.gong9ri.entity.NotificationType;
import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.PaymentStatus;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.entity.TeamParticipation;
import com.gong9ri.gong9ri.entity.TeamStatus;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.NotificationRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.TeamParticipationRepository;
import com.gong9ri.gong9ri.service.TeamDeadlineService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 이벤트 경유 마감 처리 + 환불 완료 알림 생성의 실제 end-to-end 흐름을 검증한다
 * (docs/dev/ongoing/refund-event-messaging.md 신규 테스트 (a), (c) — (b)는 @Transactional 롤백으로
 * 검증 가능해 TeamDeadlineServiceTest에 있다).
 *
 * <p><b>PortOne 연동 이후(docs/dev/payment/portone/design.md)</b>: 결제 REFUNDED 확정은 이제
 * {@code processDeadline}의 커밋 이후 {@code TeamPaymentsRefundRequestedEventListener}(@Async)가
 * {@code PortOneClient.cancelPayment}(이 테스트에서는 목으로 SUCCEEDED 고정)를 호출하고 그 결과를
 * {@code PaymentRefundService}가 반영해야 비로소 일어난다 — 한 단계 더 비동기 홉이 늘었으므로, (c)
 * 테스트도 더 이상 "같은 스레드에서 즉시" 알림이 생기는 게 아니라 waitUntil로 기다려야 한다.
 *
 * 클래스 레벨 @Transactional을 의도적으로 쓰지 않는다 — (a)는 TeamDeadlineEventListener가 @Async라
 * 별도 스레드에서 findByIdForUpdate(비관적 락)로 팀을 다시 읽는데, 이 테스트 스레드의 트랜잭션이
 * 롤백 전까지 열려 있으면 그 SELECT FOR UPDATE가 커밋될 때까지 블로킹된다. (c)는 AFTER_COMMIT 리스너가
 * "진짜 커밋"에 반응하는지를 보는 테스트라 원래도 실제 커밋이 필요하다. 그래서 데이터는 직접 정리한다
 * (SellerRevenueSummaryConcurrencyTest와 같은 패턴) — cleanUp에서 멤버id 기준으로 알림을 다시 조회해
 * 지우므로, 비동기로 늦게 생성되는 알림도(waitUntil로 생성을 기다린 뒤) 누락 없이 정리된다.
 */
@SpringBootTest
class TeamDeadlineEventFlowTest {

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
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TeamDeadlineService teamDeadlineService;

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

    private final List<Long> paymentIdsToClean = new ArrayList<>();
    private final List<Long> teamParticipationIdsToClean = new ArrayList<>();
    private final List<Long> teamIdsToClean = new ArrayList<>();
    private final List<Long> productIdsToClean = new ArrayList<>();
    private final List<Long> memberIdsToClean = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        // 알림은 비동기로 늦게 생성될 수 있어 미리 모은 id가 아니라 멤버id로 다시 조회해서 지운다
        // (그래야 team/product/member를 지우기 전에 FK를 참조하는 알림이 먼저 제거된다).
        for (Long memberId : memberIdsToClean) {
            notificationRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId)
                    .forEach(notification -> notificationRepository.deleteById(notification.getId()));
        }
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

    private Product saveProduct(Member seller) {
        Product product = productRepository.save(new Product(seller, "이벤트흐름테스트상품", "설명", 10000, 10, null));
        productIdsToClean.add(product.getId());
        return product;
    }

    private GroupBuyTeam saveExpiredTeam(Product product, Member leader) {
        GroupBuyTeam team = groupBuyTeamRepository.save(
                new GroupBuyTeam(product, leader, 10, LocalDateTime.now().minusMinutes(1)));
        teamIdsToClean.add(team.getId());
        TeamParticipation participation = teamParticipationRepository.save(new TeamParticipation(team, leader));
        teamParticipationIdsToClean.add(participation.getId());
        return team;
    }

    // pgPaymentId가 있는(=실제 PortOne 취소 대상이 될 수 있는) PAID 결제를 만든다 — 5-arg 생성자로
    // PENDING을 만든 뒤 즉시 confirm()해 PAID로 전환한다(레거시 4-arg 생성자는 pgPaymentId가 null이라
    // PortOneClient.cancelPayment(pgPaymentId, ...) 목 스텁의 anyString() 매처와 맞지 않는다).
    private Payment savePayment(Member buyer, Product product, GroupBuyTeam team) {
        Payment payment = new Payment(buyer, product, team, 10000, "pay_test_" + java.util.UUID.randomUUID());
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
    @DisplayName("(a) TeamDeadlineDetectedEvent를 발행하면 비동기 리스너가 상태전환+환불을 수행한다")
    void teamDeadlineDetectedEvent_triggersProcessDeadlineAsync() {
        Member seller = saveMember("evtSeller1", Role.SELLER);
        Product product = saveProduct(seller);
        Member leader = saveMember("evtLeader1", Role.BUYER);
        GroupBuyTeam team = saveExpiredTeam(product, leader);
        Payment payment = savePayment(leader, product, team);

        eventPublisher.publishEvent(new TeamDeadlineDetectedEvent(team.getId()));

        waitUntil(
                () -> groupBuyTeamRepository.findById(team.getId()).orElseThrow().getStatus() == TeamStatus.FAILED,
                "5초 내에 팀이 FAILED로 전환되지 않았다(비동기 리스너 미동작 가능성)");

        Payment refreshedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        assertEquals(PaymentStatus.REFUNDED, refreshedPayment.getStatus(),
                "이벤트 경유로도 결제가 REFUNDED로 전환돼야 한다");

        // 환불 완료 알림(AFTER_COMMIT)까지 끝나길 기다린다 — 내용 검증은 (c)에서 하고, 여기서는
        // cleanUp이 team/member를 지우기 전에 알림이 이미 존재해야(그래서 함께 정리되어야) 함을 보장한다.
        waitUntil(() -> !notificationRepository.findAllByMemberIdOrderByCreatedAtDesc(leader.getId()).isEmpty(),
                "5초 내에 구매자 환불 알림이 생성되지 않았다");
    }

    @Test
    @DisplayName("(c) 환불 트랜잭션이 커밋에 성공하면 구매자 전원 + 판매자에게 알림이 생성된다")
    void processDeadline_commitSuccess_createsNotificationsForAllBuyersAndSeller() {
        Member seller = saveMember("evtSeller2", Role.SELLER);
        Product product = saveProduct(seller);
        Member leader = saveMember("evtLeader2", Role.BUYER);
        GroupBuyTeam team = saveExpiredTeam(product, leader);

        Member otherBuyer = saveMember("evtBuyer2", Role.BUYER);
        savePayment(leader, product, team);
        savePayment(otherBuyer, product, team);

        // 이벤트를 거치지 않고 processDeadline을 직접 호출한다(리스너 자체는 event 패키지 클래스라
        // 별도 재검증 불필요) — 이 호출은 테스트 메서드가 트랜잭션 밖이라 자체 트랜잭션으로 실제 커밋된다.
        // PortOne 연동 이후에는 커밋 직후 곧바로 알림이 생기지 않는다 — @Async인
        // TeamPaymentsRefundRequestedEventListener가 커밋 이후 별도 스레드에서 PortOneClient.cancelPayment
        // (이 테스트는 목으로 SUCCEEDED 고정)를 호출하고, 그 결과를 PaymentRefundService가 반영해야 비로소
        // TeamRefundedEvent가 발행되어 알림이 만들어진다 — 그래서 waitUntil로 기다린다.
        teamDeadlineService.processDeadline(team.getId());

        waitUntil(() -> !notificationRepository.findAllByMemberIdOrderByCreatedAtDesc(seller.getId()).isEmpty(),
                "5초 내에 판매자 환불 알림이 생성되지 않았다");
        waitUntil(() -> !notificationRepository.findAllByMemberIdOrderByCreatedAtDesc(otherBuyer.getId()).isEmpty(),
                "5초 내에 다른 구매자 환불 알림이 생성되지 않았다");

        // PortOne 연동 이후 알려진 동작 변화: team/deadline-check가 팀 단위로 한 번에 알림을 배치
        // 발행하던 것과 달리, 이제는 결제 건이 실제로 확정될 때마다 개별 발행한다(PaymentRefundService
        // 참고) — 이 팀은 결제가 2건이므로 판매자는 결제 건수만큼(2건) 알림을 받는다.
        List<Notification> sellerNotifications = notificationRepository.findAllByMemberIdOrderByCreatedAtDesc(seller.getId());
        assertEquals(2, sellerNotifications.size(), "판매자에게 결제 건수(2건)만큼 알림이 생성돼야 한다");
        assertEquals(NotificationType.TEAM_REFUNDED, sellerNotifications.get(0).getType());
        assertEquals(team.getId(), sellerNotifications.get(0).getRelatedTeam().getId());

        List<Notification> leaderNotifications = notificationRepository.findAllByMemberIdOrderByCreatedAtDesc(leader.getId());
        assertEquals(1, leaderNotifications.size(), "구매자(팀장)에게 알림이 정확히 1건 생성돼야 한다");

        List<Notification> otherBuyerNotifications =
                notificationRepository.findAllByMemberIdOrderByCreatedAtDesc(otherBuyer.getId());
        assertEquals(1, otherBuyerNotifications.size(), "다른 구매자에게도 알림이 정확히 1건 생성돼야 한다");

        assertTrue(sellerNotifications.get(0).getIsRead() != null && !sellerNotifications.get(0).getIsRead(),
                "새로 생성된 알림은 읽지 않은 상태여야 한다");

        Payment refreshedLeaderPayment = paymentRepository.findByTeamIdAndStatus(team.getId(), PaymentStatus.REFUNDED)
                .stream().findFirst().orElseThrow();
        assertEquals(PaymentStatus.REFUNDED, refreshedLeaderPayment.getStatus());
    }

    @Test
    @DisplayName("PortOne 취소가 비동기(REQUESTED)로 응답하면 결제는 REFUND_PENDING로 대기하고, "
            + "웹훅(Transaction.Cancelled)에 해당하는 재검증 호출이 와야 최종 REFUNDED로 확정된다")
    void processDeadline_cancelRequested_staysRefundPendingUntilWebhookConfirms() {
        when(portOneClient.cancelPayment(anyString(), anyString()))
                .thenReturn(new PortOneCancelResult(PortOneCancelResult.REQUESTED));

        Member seller = saveMember("evtSeller3", Role.SELLER);
        Product product = saveProduct(seller);
        Member leader = saveMember("evtLeader3", Role.BUYER);
        GroupBuyTeam team = saveExpiredTeam(product, leader);
        Payment payment = savePayment(leader, product, team);

        teamDeadlineService.processDeadline(team.getId());

        waitUntil(() -> paymentRepository.findById(payment.getId()).orElseThrow().getStatus()
                        == PaymentStatus.REFUND_PENDING,
                "5초 내에 결제가 REFUND_PENDING으로 전환되지 않았다");

        assertTrue(notificationRepository.findAllByMemberIdOrderByCreatedAtDesc(seller.getId()).isEmpty(),
                "아직 최종 확정 전이라 환불 완료 알림이 생성되면 안 된다");
    }
}
