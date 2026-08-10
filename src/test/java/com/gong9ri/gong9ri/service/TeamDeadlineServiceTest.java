package com.gong9ri.gong9ri.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.PaymentStatus;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.entity.TeamParticipation;
import com.gong9ri.gong9ri.entity.TeamStatus;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.TeamParticipationRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마감 지난 팀을 실시간으로 스캔·전환하는 스케줄러 로직을 검증한다.
 * 실제 1분 대기 없이, deadline을 과거로 박아 저장한 팀에 대해 서비스 메서드(processDeadline)를 직접 호출한다
 * (@Scheduled 애노테이션 자체는 스프링이 등록만 해줄 뿐 여기서 실행 스케줄을 검증할 필요는 없음).
 */
@SpringBootTest
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
    @DisplayName("RECRUITING + deadline 지난 팀은 FAILED로 전환되고, PAID 결제는 전부 REFUNDED로 전환된다")
    void processDeadline_failsTeamAndRefundsPaidPayments() {
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

        Payment updatedPayment1 = paymentRepository.findById(payment1.getId()).orElseThrow();
        Payment updatedPayment2 = paymentRepository.findById(payment2.getId()).orElseThrow();
        assertEquals(PaymentStatus.REFUNDED, updatedPayment1.getStatus());
        assertEquals(PaymentStatus.REFUNDED, updatedPayment2.getStatus());
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
