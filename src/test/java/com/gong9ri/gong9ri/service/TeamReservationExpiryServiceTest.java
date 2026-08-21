package com.gong9ri.gong9ri.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.entity.TeamParticipation;
import com.gong9ri.gong9ri.entity.TeamStatus;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.TeamParticipationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * 미결제 참여 자동 만료(team/reservation-expiry, docs/dev/ongoing/team-payment-enforcement.md) 검증.
 * 실제 10분 대기 없이, joinedAt을 과거로 박아 저장한 참여에 대해 서비스 메서드를 직접 호출한다
 * (team/deadline-check의 TeamDeadlineServiceTest와 동일한 접근).
 */
@SpringBootTest
@Transactional
class TeamReservationExpiryServiceTest {

    @Autowired
    private TeamReservationExpiryService teamReservationExpiryService;

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

    @PersistenceContext
    private EntityManager entityManager;

    private Member saveMember(String username, Role role) {
        return memberRepository.save(new Member(username, "pw", "테스트유저", username + "@test.com", role));
    }

    private Product saveProduct(Member seller, int maxParticipants) {
        return productRepository.save(new Product(seller, "만료테스트상품", "설명", 25000, maxParticipants, null));
    }

    private GroupBuyTeam saveTeam(Product product, Member leader, int maxParticipants) {
        GroupBuyTeam team = groupBuyTeamRepository.save(
                new GroupBuyTeam(product, leader, maxParticipants, LocalDateTime.now().plusDays(7)));
        teamParticipationRepository.save(new TeamParticipation(team, leader));
        return team;
    }

    /**
     * {@code TeamParticipation.joinedAt}은 {@code @CreatedDate} + {@code updatable=false}라 일반적인
     * 엔티티 필드 변경(save 등)으로는 DB에 반영되지 않는다. 스캔 쿼리({@code findTeamIdsWithParticipationBefore})는
     * 스칼라 프로젝션이라 영속성 컨텍스트(식별자 맵)를 거치지 않고 DB 컬럼 값을 직접 읽으므로, 실제로
     * "10분 전에 참여한 것"처럼 동작하게 하려면 네이티브 UPDATE로 DB 값 자체를 바꿔야 한다. 동시에
     * 영속성 컨텍스트에 이미 캐시된 엔티티도 리플렉션으로 맞춰 둬야, 같은 트랜잭션 안에서 그 이후 실행되는
     * 전체 엔티티 조회(예: {@code findAllByTeamIdWithMemberOrderByJoinedAtAsc})가 오래된 캐시 값을
     * 되돌려주는 일 없이 일관되게 동작한다.
     */
    private void backdateJoinedAt(Long teamId, Long memberId, LocalDateTime joinedAt) {
        TeamParticipation participation = teamParticipationRepository.findAll().stream()
                .filter(p -> p.getTeam().getId().equals(teamId) && p.getMember().getId().equals(memberId))
                .findFirst()
                .orElseThrow();
        ReflectionTestUtils.setField(participation, "joinedAt", joinedAt);
        entityManager.createNativeQuery("UPDATE team_participation SET joined_at = ? WHERE id = ?")
                .setParameter(1, joinedAt)
                .setParameter(2, participation.getId())
                .executeUpdate();
    }

    @Test
    @DisplayName("joinedAt이 10분 넘게 지났고 PAID 결제가 없는 참여는 스캔 후보에 포함되고, 처리 시 취소된다(정원 반환)")
    void processExpiredParticipations_unpaidPastCutoff_cancelsParticipation() {
        Member seller = saveMember("expSeller1", Role.SELLER);
        Product product = saveProduct(seller, 5);
        Member leader = saveMember("expLeader1", Role.BUYER);
        GroupBuyTeam team = saveTeam(product, leader, 5);
        Member joiner = saveMember("expJoiner1", Role.BUYER);
        teamParticipationRepository.save(new TeamParticipation(team, joiner));
        team.increaseParticipant();
        groupBuyTeamRepository.save(team);
        backdateJoinedAt(team.getId(), joiner.getId(), LocalDateTime.now().minusMinutes(11));

        List<Long> expiredTeamIds = teamReservationExpiryService.findTeamIdsWithExpiredUnpaidParticipations();
        assertTrue(expiredTeamIds.contains(team.getId()), "스캔 후보에 이 팀이 포함돼야 한다");

        teamReservationExpiryService.processExpiredParticipations(team.getId());

        assertFalse(teamParticipationRepository.existsByTeamIdAndMemberId(team.getId(), joiner.getId()),
                "만료된 미결제 참여는 제거돼야 한다");
        GroupBuyTeam refreshed = groupBuyTeamRepository.findById(team.getId()).orElseThrow();
        assertEquals(1, refreshed.getCurrentCount(), "취소로 정원이 반환돼야 한다");
        assertEquals(TeamStatus.RECRUITING, refreshed.getStatus());
    }

    @Test
    @DisplayName("PAID 결제가 있는 참여는 joinedAt이 10분 넘게 지났어도 취소되지 않는다")
    void processExpiredParticipations_paidParticipation_isNotCanceled() {
        Member seller = saveMember("expSeller2", Role.SELLER);
        Product product = saveProduct(seller, 5);
        Member leader = saveMember("expLeader2", Role.BUYER);
        GroupBuyTeam team = saveTeam(product, leader, 5);
        Member joiner = saveMember("expJoiner2", Role.BUYER);
        teamParticipationRepository.save(new TeamParticipation(team, joiner));
        team.increaseParticipant();
        groupBuyTeamRepository.save(team);
        Payment payment = new Payment(joiner, product, team, 25000, "pay_exp_test_1");
        payment.confirm();
        paymentRepository.save(payment);
        backdateJoinedAt(team.getId(), joiner.getId(), LocalDateTime.now().minusMinutes(11));

        teamReservationExpiryService.processExpiredParticipations(team.getId());

        assertTrue(teamParticipationRepository.existsByTeamIdAndMemberId(team.getId(), joiner.getId()),
                "PAID 결제가 있으면 만료 대상이 아니다");
        GroupBuyTeam refreshed = groupBuyTeamRepository.findById(team.getId()).orElseThrow();
        assertEquals(2, refreshed.getCurrentCount());
    }

    @Test
    @DisplayName("joinedAt이 아직 10분이 안 지난 참여는 취소되지 않는다")
    void processExpiredParticipations_withinCutoff_isNotCanceled() {
        Member seller = saveMember("expSeller3", Role.SELLER);
        Product product = saveProduct(seller, 5);
        Member leader = saveMember("expLeader3", Role.BUYER);
        GroupBuyTeam team = saveTeam(product, leader, 5);
        Member joiner = saveMember("expJoiner3", Role.BUYER);
        teamParticipationRepository.save(new TeamParticipation(team, joiner));
        team.increaseParticipant();
        groupBuyTeamRepository.save(team);

        List<Long> expiredTeamIds = teamReservationExpiryService.findTeamIdsWithExpiredUnpaidParticipations();
        assertFalse(expiredTeamIds.contains(team.getId()), "10분이 안 지났으면 스캔 후보에 포함되면 안 된다");

        teamReservationExpiryService.processExpiredParticipations(team.getId());

        assertTrue(teamParticipationRepository.existsByTeamIdAndMemberId(team.getId(), joiner.getId()));
    }

    @Test
    @DisplayName("마지막 남은 참여자의 참여가 만료되면 팀이 FAILED로 전환된다")
    void processExpiredParticipations_lastParticipant_teamBecomesFailed() {
        Member seller = saveMember("expSeller4", Role.SELLER);
        Product product = saveProduct(seller, 5);
        Member leader = saveMember("expLeader4", Role.BUYER);
        GroupBuyTeam team = saveTeam(product, leader, 5);
        backdateJoinedAt(team.getId(), leader.getId(), LocalDateTime.now().minusMinutes(11));

        teamReservationExpiryService.processExpiredParticipations(team.getId());

        GroupBuyTeam refreshed = groupBuyTeamRepository.findById(team.getId()).orElseThrow();
        assertEquals(TeamStatus.FAILED, refreshed.getStatus());
        assertEquals(0, refreshed.getCurrentCount());
    }

    @Test
    @DisplayName("리더의 참여가 만료되면 그다음 최초 참가자에게 리더가 승계된다")
    void processExpiredParticipations_leaderExpires_leaderSucceeds() {
        Member seller = saveMember("expSeller5", Role.SELLER);
        Product product = saveProduct(seller, 5);
        Member leader = saveMember("expLeader5", Role.BUYER);
        GroupBuyTeam team = saveTeam(product, leader, 5);
        Member secondJoiner = saveMember("expJoiner5", Role.BUYER);
        teamParticipationRepository.save(new TeamParticipation(team, secondJoiner));
        team.increaseParticipant();
        groupBuyTeamRepository.save(team);
        // 리더만 만료 대상으로 만든다 — secondJoiner는 방금 참여해 아직 10분이 안 지났다.
        backdateJoinedAt(team.getId(), leader.getId(), LocalDateTime.now().minusMinutes(11));

        teamReservationExpiryService.processExpiredParticipations(team.getId());

        GroupBuyTeam refreshed = groupBuyTeamRepository.findById(team.getId()).orElseThrow();
        assertEquals(secondJoiner.getId(), refreshed.getLeader().getId());
        assertEquals(1, refreshed.getCurrentCount());
    }

    @Test
    @DisplayName("이미 SUCCESS로 전환된 팀은 만료 처리 대상이 아니다(락 획득 후 방어적 재검증)")
    void processExpiredParticipations_teamNotRecruiting_isSkipped() {
        Member seller = saveMember("expSeller6", Role.SELLER);
        Product product = saveProduct(seller, 2);
        Member leader = saveMember("expLeader6", Role.BUYER);
        GroupBuyTeam team = new GroupBuyTeam(product, leader, 2, LocalDateTime.now().plusDays(7));
        team.increaseParticipant(); // currentCount 1 -> 2 == maxParticipants → SUCCESS 전환
        GroupBuyTeam saved = groupBuyTeamRepository.save(team);
        teamParticipationRepository.save(new TeamParticipation(saved, leader));
        assertEquals(TeamStatus.SUCCESS, saved.getStatus());
        backdateJoinedAt(saved.getId(), leader.getId(), LocalDateTime.now().minusMinutes(11));

        teamReservationExpiryService.processExpiredParticipations(saved.getId());

        GroupBuyTeam refreshed = groupBuyTeamRepository.findById(saved.getId()).orElseThrow();
        assertEquals(TeamStatus.SUCCESS, refreshed.getStatus());
        assertTrue(teamParticipationRepository.existsByTeamIdAndMemberId(saved.getId(), leader.getId()),
                "RECRUITING이 아닌 팀의 참여는 건드리면 안 된다");
    }

    @Test
    @DisplayName("스캔 쿼리는 RECRUITING이면서 10분 넘게 지난 참여가 있는 팀 id만 반환한다")
    void findTeamIdsWithExpiredUnpaidParticipations_returnsOnlyMatchingTeams() {
        Member seller = saveMember("expSeller7", Role.SELLER);
        Product product = saveProduct(seller, 5);
        Member leader = saveMember("expLeader7", Role.BUYER);

        GroupBuyTeam expiredTeam = saveTeam(product, leader, 5);
        backdateJoinedAt(expiredTeam.getId(), leader.getId(), LocalDateTime.now().minusMinutes(11));

        Member leader2 = saveMember("expLeader7b", Role.BUYER);
        GroupBuyTeam notYetExpiredTeam = saveTeam(product, leader2, 5);

        List<Long> expiredIds = teamReservationExpiryService.findTeamIdsWithExpiredUnpaidParticipations();

        assertTrue(expiredIds.contains(expiredTeam.getId()));
        assertFalse(expiredIds.contains(notYetExpiredTeam.getId()));
    }
}
