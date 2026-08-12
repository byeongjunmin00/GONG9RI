package com.gong9ri.gong9ri.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.TeamJoinResponse;
import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.entity.TeamParticipation;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.TeamParticipationRepository;
import com.gong9ri.gong9ri.service.TeamService;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * 실시간 메시징(발제 도전과제) — {@code team/join}이 실제로 커밋된 뒤 STOMP 토픽으로 정확한 페이로드가
 * 도착하는지 실제 {@code WebSocketStompClient}(목이 아닌 진짜 STOMP 클라이언트)로 검증한다.
 * 컨트롤러/인증 계층은 기존 {@code TeamControllerTest}가 이미 커버하므로, 이 테스트는 서비스 계층
 * 호출 → 트랜잭션 커밋 → 브로드캐스트 도착까지의 경로에만 집중한다.
 *
 * <p>{@code AFTER_COMMIT} 리스너가 실제로 발동하려면 진짜 커밋이 있어야 해서 이 클래스는
 * {@code TeamConcurrencyTest}와 동일하게 의도적으로 {@code @Transactional}을 안 쓰고, 대신
 * {@code @AfterEach}에서 직접 정리한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TeamCapacityBroadcastTest {

    @LocalServerPort
    private int port;

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
            productRepository.deleteById(productIdToClean);
        }
        memberIdsToClean.forEach(memberRepository::deleteById);
        memberIdsToClean.clear();
    }

    @Test
    @DisplayName("참가가 커밋되면 해당 상품 토픽으로 갱신된 팀 상태가 실제로 브로드캐스트된다")
    void join_broadcastsCapacityChangeOverStomp() throws Exception {
        Member seller = memberRepository.save(
                new Member("ws-seller", "pw", "판매자", "ws-seller@test.com", Role.SELLER));
        memberIdsToClean.add(seller.getId());

        Product product = productRepository.save(
                new Product(seller, "실시간테스트상품", "설명", 10000, 5, null));
        productIdToClean = product.getId();

        Member leader = memberRepository.save(
                new Member("ws-leader", "pw", "리더", "ws-leader@test.com", Role.BUYER));
        memberIdsToClean.add(leader.getId());

        GroupBuyTeam team = groupBuyTeamRepository.save(
                new GroupBuyTeam(product, leader, 5, LocalDateTime.now().plusDays(7)));
        teamParticipationRepository.save(new TeamParticipation(team, leader));
        teamIdToClean = team.getId();

        Member joiner = memberRepository.save(
                new Member("ws-joiner", "pw", "참가자", "ws-joiner@test.com", Role.BUYER));
        memberIdsToClean.add(joiner.getId());

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());

        BlockingQueue<TeamJoinResponse> received = new LinkedBlockingQueue<>();
        StompSession session = stompClient
                .connectAsync("ws://localhost:" + port + "/ws-team", new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        session.subscribe("/topic/products/" + product.getId() + "/teams", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TeamJoinResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((TeamJoinResponse) payload);
            }
        });

        // 구독 등록이 서버에 실제로 반영될 시간을 조금 준다(경합 방지).
        Thread.sleep(300);

        teamService.join(new MemberUserDetails(joiner), team.getId());

        TeamJoinResponse message = received.poll(5, TimeUnit.SECONDS);
        assertNotNull(message, "5초 안에 브로드캐스트 메시지를 받아야 한다");
        assertEquals(team.getId(), message.teamId());
        assertEquals(2, message.currentCount());
        assertEquals(5, message.maxParticipants());

        session.disconnect();
        stompClient.stop();
    }
}
