package com.gong9ri.gong9ri.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.SupportMessageRepository;
import com.gong9ri.gong9ri.repository.SupportRoomRepository;
import com.gong9ri.gong9ri.service.SupportChatService;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * 상담방 구독 권한 (support/chat) — <b>실제 STOMP 클라이언트</b>로 검증한다.
 *
 * <p><b>왜 실제로 붙어봐야 하나</b> — STOMP 구독은 {@code SecurityConfig}의 HTTP 인가를 타지 않는다.
 * 서비스 계층 테스트(REST)를 아무리 통과해도, 구독 경로에 검사가 빠져 있으면 <b>아무나 남의 상담을
 * 실시간으로 훔쳐볼 수 있다.</b> 그 갭은 목이 아닌 실제 연결로만 드러난다.
 *
 * <p>{@code @Transactional}을 쓰지 않는다 — 다른 스레드(WebSocket)가 DB를 읽어야 해서 롤백되는
 * 트랜잭션 안의 데이터는 보이지 않는다({@code TeamCapacityBroadcastTest}와 같은 이유). 대신
 * {@code @AfterEach}에서 직접 정리한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SupportChatSubscriptionSecurityTest {

    private static final String PASSWORD = "password123!";

    @LocalServerPort
    private int port;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private SupportRoomRepository supportRoomRepository;

    @Autowired
    private SupportMessageRepository supportMessageRepository;

    @Autowired
    private SupportChatService supportChatService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final List<Long> memberIdsToClean = new ArrayList<>();
    private final List<Long> roomIdsToClean = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        roomIdsToClean.forEach(roomId -> {
            supportMessageRepository.deleteByRoom_Id(roomId);
            supportRoomRepository.deleteById(roomId);
        });
        roomIdsToClean.clear();
        memberIdsToClean.forEach(memberRepository::deleteById);
        memberIdsToClean.clear();
    }

    private Member saveMember(String username, Role role) {
        Member member = new Member(username, passwordEncoder.encode(PASSWORD),
                username + "이름", username + "@test.com", role);
        member.verifyEmail();
        Member saved = memberRepository.save(member);
        memberIdsToClean.add(saved.getId());
        return saved;
    }

    private String loginCookie(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> login = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/auth/login",
                new HttpEntity<>("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}", headers),
                String.class);
        List<String> cookies = login.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertNotNull(cookies, "로그인 세션 쿠키가 있어야 WebSocket 핸드셰이크에서 인증된다");
        return cookies.get(0).split(";", 2)[0];
    }

    private StompSession connect(String sessionCookie) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.add(HttpHeaders.COOKIE, sessionCookie);
        return client.connectAsync("ws://localhost:" + port + "/ws-support",
                        handshakeHeaders, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("[보안] 남의 상담방은 실시간 구독 자체가 거절된다")
    void subscribe_rejected_forStranger() throws Exception {
        Member owner = saveMember("ws-sc-owner", Role.BUYER);
        Member stranger = saveMember("ws-sc-stranger", Role.BUYER);
        Long roomId = supportChatService.openRoom(owner).roomId();
        roomIdsToClean.add(roomId);

        StompSession strangerSession = connect(loginCookie(stranger.getUsername()));
        BlockingQueue<Object> received = new LinkedBlockingQueue<>();
        strangerSession.subscribe("/topic/support/" + roomId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Object.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add(payload);
            }
        });
        Thread.sleep(300);

        // **반드시 WebSocket으로 보내야 한다.** 서비스를 직접 호출하면 저장만 되고 브로드캐스트가
        // 일어나지 않아, 인터셉터가 없어도 침입자에게 아무것도 안 간다 — 그러면 이 테스트는 통과하지만
        // 아무것도 증명하지 못한다(실제로 인터셉터를 빼고도 통과하는 걸 확인한 뒤 이렇게 고쳤다).
        StompSession ownerSession = connect(loginCookie(owner.getUsername()));
        ownerSession.send("/app/support/" + roomId + "/send", new SendPayload("카드 결제가 실패해요"));

        assertTrue(received.poll(3, TimeUnit.SECONDS) == null,
                "남의 상담 메시지가 도착하면 대화 내용이 그대로 새는 것이다");
    }

    @Test
    @DisplayName("[보안] 비로그인은 상담 WebSocket에 연결조차 못 한다")
    void connect_rejected_withoutLogin() {
        // /ws-support는 SecurityConfig의 permitAll 목록에 없어 핸드셰이크부터 막힌다.
        assertThrows(ExecutionException.class, () -> {
            WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
            client.setMessageConverter(new JacksonJsonMessageConverter());
            client.connectAsync("ws://localhost:" + port + "/ws-support",
                            new StompSessionHandlerAdapter() {})
                    .get(5, TimeUnit.SECONDS);
        });
    }

    @Test
    @DisplayName("방 주인은 자기 상담 메시지를 실시간으로 받는다")
    void subscribe_allowed_forOwner() throws Exception {
        Member owner = saveMember("ws-sc-owner2", Role.BUYER);
        Long roomId = supportChatService.openRoom(owner).roomId();
        roomIdsToClean.add(roomId);

        StompSession session = connect(loginCookie(owner.getUsername()));
        BlockingQueue<Object> received = new LinkedBlockingQueue<>();
        session.subscribe("/topic/support/" + roomId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Object.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add(payload);
            }
        });
        Thread.sleep(300); // 구독이 브로커에 등록될 시간

        session.send("/app/support/" + roomId + "/send", new SendPayload("배송이 안 와요"));

        assertNotNull(received.poll(5, TimeUnit.SECONDS), "자기 방 메시지는 실시간으로 도착해야 한다");
    }

    private record SendPayload(String content) {
    }
}
