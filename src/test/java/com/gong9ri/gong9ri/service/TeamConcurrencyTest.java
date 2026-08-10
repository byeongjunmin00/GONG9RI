package com.gong9ri.gong9ri.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.entity.TeamParticipation;
import com.gong9ri.gong9ri.entity.TeamStatus;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.PriceTierRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.TeamParticipationRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 실제 동시 요청 상황을 재현해야 해서 이 클래스는 의도적으로 @Transactional을 안 쓴다.
 * 워커 스레드들이 메인 테스트 스레드와 별도의 DB 커넥션/트랜잭션을 쓰기 때문에,
 * 롤백 방식이면 워커 스레드가 메인 스레드가 만든(아직 커밋 안 된) 데이터를 못 본다.
 * 대신 테스트 끝나고 직접 정리(cleanup)한다.
 */
@SpringBootTest
class TeamConcurrencyTest {

    @Autowired
    private TeamService teamService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PriceTierRepository priceTierRepository;

    @Autowired
    private GroupBuyTeamRepository groupBuyTeamRepository;

    @Autowired
    private TeamParticipationRepository teamParticipationRepository;

    private final List<Long> memberIdsToClean = new ArrayList<>();
    private Long productIdToClean;
    private Long teamIdToClean;

    @AfterEach
    void cleanUp() {
        if (teamIdToClean != null) {
            teamParticipationRepository.deleteByTeamId(teamIdToClean);
            groupBuyTeamRepository.deleteById(teamIdToClean);
        }
        if (productIdToClean != null) {
            priceTierRepository.deleteByProductId(productIdToClean);
            productRepository.deleteById(productIdToClean);
        }
        memberIdsToClean.forEach(memberRepository::deleteById);
        memberIdsToClean.clear();
    }

    @Test
    @DisplayName("동시에 여러 명이 참가해도 정원을 초과하지 않는다 (비관적 락 검증)")
    void concurrentJoin_doesNotExceedCapacity() throws InterruptedException {
        int maxParticipants = 5;
        int attempts = 8;
        int expectedSuccess = maxParticipants - 1; // 리더 1명이 이미 자리를 차지한 상태에서 시작

        Member seller = memberRepository.save(
                new Member("conc-seller", "pw", "판매자", "conc-seller@test.com", Role.SELLER));
        memberIdsToClean.add(seller.getId());

        Product product = productRepository.save(
                new Product(seller, "동시성테스트상품", "설명", 10000, maxParticipants, null));
        productIdToClean = product.getId();

        Member leader = memberRepository.save(
                new Member("conc-leader", "pw", "리더", "conc-leader@test.com", Role.BUYER));
        memberIdsToClean.add(leader.getId());

        GroupBuyTeam team = groupBuyTeamRepository.save(
                new GroupBuyTeam(product, leader, maxParticipants, LocalDateTime.now().plusDays(7)));
        teamParticipationRepository.save(new TeamParticipation(team, leader));
        teamIdToClean = team.getId();

        List<Member> joiners = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            Member joiner = memberRepository.save(new Member(
                    "conc-joiner" + i, "pw", "참가자" + i, "conc-joiner" + i + "@test.com", Role.BUYER));
            memberIdsToClean.add(joiner.getId());
            joiners.add(joiner);
        }

        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch readyLatch = new CountDownLatch(attempts);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(attempts);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger teamFullCount = new AtomicInteger();

        for (Member joiner : joiners) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    teamService.join(new MemberUserDetails(joiner), team.getId());
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    teamFullCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        boolean finished = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(true, finished, "모든 참가 요청이 제한시간 안에 끝나야 한다");
        assertEquals(expectedSuccess, successCount.get(), "정원만큼만 성공해야 한다");
        assertEquals(attempts - expectedSuccess, teamFullCount.get(), "나머지는 TEAM_FULL로 막혀야 한다");

        GroupBuyTeam finalTeam = groupBuyTeamRepository.findById(team.getId()).orElseThrow();
        assertEquals(maxParticipants, finalTeam.getCurrentCount(), "current_count가 정원을 넘으면 안 된다");
        assertEquals(TeamStatus.SUCCESS, finalTeam.getStatus(), "정원이 다 차면 SUCCESS로 전환돼야 한다");
    }
}
