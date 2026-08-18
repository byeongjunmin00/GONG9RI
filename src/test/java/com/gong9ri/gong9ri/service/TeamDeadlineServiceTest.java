package com.gong9ri.gong9ri.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.PaymentStatus;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.RefundRequest;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.entity.TeamParticipation;
import com.gong9ri.gong9ri.entity.TeamStatus;
import com.gong9ri.gong9ri.event.TeamPaymentsRefundRequestedEvent;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.NotificationRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.RefundRequestRepository;
import com.gong9ri.gong9ri.repository.TeamParticipationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마감 지난 팀을 실시간으로 스캔·전환하는 스케줄러 로직을 검증한다.
 * 실제 1분 대기 없이, deadline을 과거로 박아 저장한 팀에 대해 서비스 메서드(processDeadline)를 직접 호출한다
 * (@Scheduled 애노테이션 자체는 스프링이 등록만 해줄 뿐 여기서 실행 스케줄을 검증할 필요는 없음).
 *
 * <p><b>PortOne 연동 이후</b>: 이 클래스는 클래스 레벨 {@code @Transactional}로 각 테스트가 끝나면
 * 롤백된다 — 즉 {@code TeamPaymentsRefundRequestedEvent}를 소비하는 AFTER_COMMIT 리스너
 * ({@code TeamPaymentsRefundRequestedEventListener})는 절대 실행되지 않는다(진짜 커밋이 필요해서).
 * 그래서 여기서는 "processDeadline이 결제 상태를 직접 바꾸지 않고 이벤트만 발행하는지"만 검증하고,
 * 실제 PortOne 취소 호출까지 이어지는 end-to-end 흐름은 {@code event/TeamPaymentsRefundRequestedEventFlowTest}에서
 * (실제 커밋 + 목 PortOneClient로) 검증한다.
 */
@SpringBootTest
@RecordApplicationEvents
@Transactional
class TeamDeadlineServiceTest {

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

    @Autowired
    private RefundRequestRepository refundRequestRepository;

    private Member saveMember(String username, Role role) {
        return memberRepository.save(new Member(username, "pw", "테스트유저", username + "@test.com", role));
    }

    private Product saveProduct(Member seller) {
        return productRepository.save(new Product(seller, "마감테스트상품", "설명", 10000, 10, null));
    }

    private GroupBuyTeam saveTeam(Product product, Member leader, LocalDateTime deadline) {
        GroupBuyTeam team = groupBuyTeamRepository.save(new GroupBuyTeam(product, leader, 10, deadline));
        teamParticipationRepository.save(new TeamParticipation(team, leader));
        return team;
    }

    @Test
    @DisplayName("RECRUITING + deadline 지난 팀은 FAILED로 전환되고, PAID 결제의 환불취소 요청 이벤트가 발행된다"
            + "(실제 REFUNDED 전환은 PortOne 취소 확인 후 별도로 일어난다 — 아래 클래스 Javadoc 참고)")
    void processDeadline_failsTeamAndPublishesRefundRequestedEvent(ApplicationEvents events) {
        Member seller = saveMember("dlSeller1", Role.SELLER);
        Product product = saveProduct(seller);
        Member leader = saveMember("dlLeader1", Role.BUYER);
        GroupBuyTeam team = saveTeam(product, leader, LocalDateTime.now().minusMinutes(1));

        Member buyer = saveMember("dlBuyer1", Role.BUYER);
        Payment payment1 = paymentRepository.save(new Payment(leader, product, team, 10000));
        Payment payment2 = paymentRepository.save(new Payment(buyer, product, team, 10000));

        teamDeadlineService.processDeadline(team.getId());

        GroupBuyTeam updatedTeam = groupBuyTeamRepository.findById(team.getId()).orElseThrow();
        assertEquals(TeamStatus.FAILED, updatedTeam.getStatus());

        // 이 트랜잭션이 아직 커밋되지 않았으므로(테스트가 끝나면 롤백) 결제 상태는 이 시점에 바뀌지 않는다.
        Payment updatedPayment1 = paymentRepository.findById(payment1.getId()).orElseThrow();
        Payment updatedPayment2 = paymentRepository.findById(payment2.getId()).orElseThrow();
        assertEquals(PaymentStatus.PAID, updatedPayment1.getStatus());
        assertEquals(PaymentStatus.PAID, updatedPayment2.getStatus());

        List<TeamPaymentsRefundRequestedEvent> published = events.stream(TeamPaymentsRefundRequestedEvent.class).toList();
        assertEquals(1, published.size(), "환불취소 요청 이벤트가 정확히 1건 발행돼야 한다");
        assertEquals(team.getId(), published.get(0).teamId());
        assertTrue(published.get(0).paymentIds().containsAll(List.of(payment1.getId(), payment2.getId())),
                "이벤트에 두 결제 id가 모두 포함돼야 한다");
    }

    @Test
    @DisplayName("참여 취소로 이미 대기 중인 환불 요청이 걸린 결제는 마감 스윕 대상에서 제외된다")
    void processDeadline_paymentWithPendingRefundRequest_isExcludedFromSweep(ApplicationEvents events) {
        Member seller = saveMember("dlSeller7", Role.SELLER);
        Product product = saveProduct(seller);
        Member leader = saveMember("dlLeader7", Role.BUYER);
        GroupBuyTeam team = saveTeam(product, leader, LocalDateTime.now().minusMinutes(1));

        Member pendingBuyer = saveMember("dlBuyer7a", Role.BUYER);
        Payment pendingPayment = paymentRepository.save(new Payment(pendingBuyer, product, team, 10000));
        refundRequestRepository.save(new RefundRequest(pendingPayment, pendingBuyer, null));

        Member normalBuyer = saveMember("dlBuyer7b", Role.BUYER);
        Payment normalPayment = paymentRepository.save(new Payment(normalBuyer, product, team, 10000));

        teamDeadlineService.processDeadline(team.getId());

        List<TeamPaymentsRefundRequestedEvent> published = events.stream(TeamPaymentsRefundRequestedEvent.class).toList();
        assertEquals(1, published.size(), "환불취소 요청 이벤트가 정확히 1건 발행돼야 한다");
        assertTrue(published.get(0).paymentIds().contains(normalPayment.getId()),
                "대기 중인 환불 요청이 없는 결제는 그대로 마감 환불 대상이어야 한다");
        assertTrue(!published.get(0).paymentIds().contains(pendingPayment.getId()),
                "이미 대기 중인 환불 요청이 걸린 결제는 마감 스윕이 건드리면 안 된다(고아 요청 방지)");
    }

    @Test
    @DisplayName("연결된 결제가 없는 팀도 에러 없이 FAILED로 전환된다")
    void processDeadline_teamWithoutPayments_transitionsWithoutError() {
        Member seller = saveMember("dlSeller2", Role.SELLER);
        Product product = saveProduct(seller);
        Member leader = saveMember("dlLeader2", Role.BUYER);
        GroupBuyTeam team = saveTeam(product, leader, LocalDateTime.now().minusMinutes(1));

        teamDeadlineService.processDeadline(team.getId());

        GroupBuyTeam updatedTeam = groupBuyTeamRepository.findById(team.getId()).orElseThrow();
        assertEquals(TeamStatus.FAILED, updatedTeam.getStatus());
    }

    @Test
    @DisplayName("deadline이 아직 안 지난 RECRUITING 팀은 그대로 유지된다")
    void processDeadline_deadlineNotYetPassed_staysRecruiting() {
        Member seller = saveMember("dlSeller3", Role.SELLER);
        Product product = saveProduct(seller);
        Member leader = saveMember("dlLeader3", Role.BUYER);
        GroupBuyTeam team = saveTeam(product, leader, LocalDateTime.now().plusDays(1));

        teamDeadlineService.processDeadline(team.getId());

        GroupBuyTeam updatedTeam = groupBuyTeamRepository.findById(team.getId()).orElseThrow();
        assertEquals(TeamStatus.RECRUITING, updatedTeam.getStatus());
    }

    @Test
    @DisplayName("이미 SUCCESS인 팀은 deadline이 지났어도 재전환되지 않고, 결제도 건드리지 않는다")
    void processDeadline_alreadySuccess_isUntouched() {
        Member seller = saveMember("dlSeller4", Role.SELLER);
        Product product = saveProduct(seller);
        Member leader = saveMember("dlLeader4", Role.BUYER);
        GroupBuyTeam team = new GroupBuyTeam(product, leader, 2, LocalDateTime.now().minusMinutes(1));
        team.increaseParticipant(); // currentCount 1 -> 2 == maxParticipants → SUCCESS 전환
        team = groupBuyTeamRepository.save(team);
        teamParticipationRepository.save(new TeamParticipation(team, leader));
        assertEquals(TeamStatus.SUCCESS, team.getStatus(), "정원이 다 찬 팀은 SUCCESS여야 한다");

        Payment payment = paymentRepository.save(new Payment(leader, product, team, 10000));

        teamDeadlineService.processDeadline(team.getId());

        GroupBuyTeam updatedTeam = groupBuyTeamRepository.findById(team.getId()).orElseThrow();
        assertEquals(TeamStatus.SUCCESS, updatedTeam.getStatus());
        Payment updatedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        assertEquals(PaymentStatus.PAID, updatedPayment.getStatus(), "SUCCESS 팀의 결제는 환불 대상이 아니다");
    }

    @Test
    @DisplayName("이 트랜잭션이 커밋되지 않으면(테스트 클래스 @Transactional 롤백) 환불취소 요청 이벤트가 소비되지 않아 알림도 생성되지 않는다")
    void processDeadline_transactionNotCommitted_doesNotCreateRefundNotifications() {
        // TeamPaymentsRefundRequestedEvent는 processDeadline() 안에서 publishEvent로 발행되지만,
        // 구독자(TeamPaymentsRefundRequestedEventListener)는 @TransactionalEventListener(phase =
        // AFTER_COMMIT)라 이 트랜잭션이 실제로 커밋될 때만 실행된다. 이 테스트 클래스는 클래스 레벨
        // @Transactional로 테스트가 끝나면 항상 롤백되므로(커밋되지 않음), PortOne 취소 호출도, 그
        // 결과로 이어지는 결제 확정(PaymentRefundService)·환불 완료 알림(TeamRefundedEvent)도 전혀
        // 일어나지 않는다 — 이게 AFTER_COMMIT 리스너의 핵심 정합성 보장이다.
        Member seller = saveMember("dlSeller6", Role.SELLER);
        Product product = saveProduct(seller);
        Member leader = saveMember("dlLeader6", Role.BUYER);
        GroupBuyTeam team = saveTeam(product, leader, LocalDateTime.now().minusMinutes(1));
        paymentRepository.save(new Payment(leader, product, team, 10000));

        teamDeadlineService.processDeadline(team.getId());

        GroupBuyTeam updatedTeam = groupBuyTeamRepository.findById(team.getId()).orElseThrow();
        assertEquals(TeamStatus.FAILED, updatedTeam.getStatus(), "팀 실패 전환 자체는 이 트랜잭션 안에서 정상 수행돼야 한다");
        assertEquals(0, notificationRepository.findAllByMemberIdOrderByCreatedAtDesc(leader.getId()).size(),
                "커밋되지 않은 트랜잭션에서는 환불 완료 알림이 생성되면 안 된다");
        assertEquals(0, notificationRepository.findAllByMemberIdOrderByCreatedAtDesc(seller.getId()).size(),
                "커밋되지 않은 트랜잭션에서는 판매자에게도 알림이 생성되면 안 된다");
    }

    @Test
    @DisplayName("스캔 쿼리는 RECRUITING이면서 deadline 지난 팀의 id만 반환한다")
    void findExpiredRecruitingTeamIds_returnsOnlyRecruitingPastDeadline() {
        Member seller = saveMember("dlSeller5", Role.SELLER);
        Product product = saveProduct(seller);
        Member leader = saveMember("dlLeader5", Role.BUYER);

        GroupBuyTeam expiredRecruiting = saveTeam(product, leader, LocalDateTime.now().minusMinutes(1));
        GroupBuyTeam notYetExpired = saveTeam(product, leader, LocalDateTime.now().plusDays(1));

        var expiredIds = teamDeadlineService.findExpiredRecruitingTeamIds();

        assertTrue(expiredIds.contains(expiredRecruiting.getId()), "마감 지난 RECRUITING 팀은 포함돼야 한다");
        assertTrue(!expiredIds.contains(notYetExpired.getId()), "마감 안 지난 팀은 제외돼야 한다");
    }
}
