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
import org.junit.jupiter.api.BeforeEach;
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
    /** 다른 테스트와 rate limit 카운터를 나누기 위한 전용 IP(TEST-NET-3, 실제로 라우팅되지 않는 대역). */
    private static final String TEST_CLIENT_IP = "203.0.113.201";

    @LocalServerPort
    private int port;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private SupportRoomRepository supportRoomRepository;

    @Autowired
    private com.gong9ri.gong9ri.repository.NotificationRepository notificationRepository;

    @Autowired
    private SupportMessageRepository supportMessageRepository;

    @Autowired
    private SupportChatService supportChatService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    private final List<Long> memberIdsToClean = new ArrayList<>();
    private final List<Long> roomIdsToClean = new ArrayList<>();

    /**
     * 이 테스트는 실제 HTTP 로그인을 하므로 rate limit 카운터를 남긴다. 짧은 간격으로 반복 실행하면
     * 임계값(60초 10회)에 걸려 <b>로그인 자체가 429</b>가 된다 — 테스트가 자기 흔적을 치우지 않으면
     * 결과가 "언제 마지막으로 돌렸는지"에 의존한다(2026-08-21 실제로 겪음, LoginRateLimitFilterTest와 동일).
     */
    @BeforeEach
    void clearRateLimit() {
        stringRedisTemplate.delete("rate-limit:login:" + TEST_CLIENT_IP);
    }

    @AfterEach
    void cleanUp() {
        stringRedisTemplate.delete("rate-limit:login:" + TEST_CLIENT_IP);
        roomIdsToClean.forEach(roomId -> {
            supportMessageRepository.deleteByRoom_Id(roomId);
            supportRoomRepository.deleteById(roomId);
        });
        roomIdsToClean.clear();
        memberIdsToClean.forEach(this::deleteMemberWithNotifications);
        memberIdsToClean.clear();
    }

    /**
     * 회원과 그 알림을 지운다. <b>알림이 뒤늦게 도착할 수 있어 재시도한다.</b>
     *
     * <p>상담 메시지가 오가면 관리자에게 알림이 생기는데, 그 발행이 {@code AFTER_COMMIT} + {@code @Async}라
     * 테스트가 끝난 뒤에 INSERT될 수 있다. 알림을 한 번만 지우고 바로 회원을 지우면, 그 사이에 들어온
     * 알림 때문에 <b>FK 위반으로 회원 삭제가 실패</b>하고 → 다음 테스트가 같은 아이디로 가입하려다
     * 중복 키로 깨진다. 로컬에서는 빨라서 안 걸리고 <b>CI에서만 터졌다</b>(2026-08-21).
     */
    private void deleteMemberWithNotifications(Long memberId) {
        for (int attempt = 0; attempt < 5; attempt++) {
            notificationRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId)
                    .forEach(n -> notificationRepository.deleteById(n.getId()));
            try {
                memberRepository.deleteById(memberId);
                memberRepository.flush();
                return;
            } catch (Exception e) {
                // 뒤늦게 도착한 알림이 원인일 수 있다 — 잠깐 기다렸다 다시 시도한다.
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private Member saveMember(String username, Role role) {
        Member member = new Member(username, passwordEncoder.encode(PASSWORD),
                username + "이름", username + "@test.com", role);
        member.verifyEmail();
        Member saved = memberRepository.save(member);
        memberIdsToClean.add(saved.getId());
        return saved;
    }

    /**
     * 로그인해서 세션 쿠키를 얻는다. <b>같은 계정은 한 번만 로그인하고 결과를 재사용한다</b> —
     * 로그인에는 IP 단위 rate limit(60초 10회)이 있어, 테스트마다 매번 새로 로그인하면 이 클래스
     * 하나만으로도 임계값을 넘겨 429가 난다(실제로 겪음).
     *
     * <p><b>X-Forwarded-For를 붙이는 이유</b>: 로그인에는 IP 단위 rate limit(60초에 10회)이 걸려 있다.
     * 헤더 없이 호출하면 전부 127.0.0.1로 묶여, 이 클래스만으로도 한 번 실행에 여러 번 로그인하므로
     * 다른 테스트와 합쳐지면 임계값을 넘겨 <b>로그인 자체가 429로 막힌다</b>(실제로 겪음).
     * 테스트 전용 IP를 써서 실사용·다른 테스트와 카운터를 분리한다.
     */
    private final java.util.Map<String, String> cookieCache = new java.util.HashMap<>();

    private String loginCookie(String username) {
        String cached = cookieCache.get(username);
        if (cached != null) {
            return cached;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Forwarded-For", TEST_CLIENT_IP);
        ResponseEntity<String> login = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/auth/login",
                new HttpEntity<>("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}", headers),
                String.class);
        List<String> cookies = login.getHeaders().get(HttpHeaders.SET_COOKIE);
        // 실패 원인을 메시지에 담는다 — "쿠키가 null"만으로는 401인지 429인지 알 수 없어 진단이 오래 걸린다.
        assertNotNull(cookies, "로그인 세션 쿠키가 있어야 WebSocket 핸드셰이크에서 인증된다. "
                + "status=" + login.getStatusCode() + ", body=" + login.getBody());
        String cookie = cookies.get(0).split(";", 2)[0];
        cookieCache.put(username, cookie);
        return cookie;
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

    @Test
    @DisplayName("[보안] 관리자가 아니면 상담 목록 갱신 토픽을 구독해도 신호가 오지 않는다")
    void adminTopic_rejected_forNonAdmin() throws Exception {
        // 이 토픽에는 대화 내용이 없지만, 신호만 받아도 "상담이 몇 건 오가는지"가 샌다.
        Member buyer = saveMember("ws-admintopic-buyer", Role.BUYER);
        Member other = saveMember("ws-admintopic-other", Role.BUYER);
        Long roomId = supportChatService.openRoom(buyer).roomId();
        roomIdsToClean.add(roomId);

        StompSession session = connect(loginCookie(other.getUsername()));
        BlockingQueue<Object> received = new LinkedBlockingQueue<>();
        session.subscribe("/topic/admin/support", new StompFrameHandler() {
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

        // 방 주인이 메시지를 보내면 관리자 토픽으로 갱신 신호가 나간다 — 구매자에게는 오면 안 된다.
        StompSession ownerSession = connect(loginCookie(buyer.getUsername()));
        ownerSession.send("/app/support/" + roomId + "/send", new SendPayload("문의드려요"));

        assertTrue(received.poll(3, TimeUnit.SECONDS) == null,
                "관리자가 아닌 사용자에게 상담 발생 신호가 새면 안 된다");
    }

    @Test
    @DisplayName("관리자는 다른 방에 온 메시지도 목록 갱신 신호로 받는다 — 새로고침 없이 뜨게")
    void adminTopic_notifiesAdmin() throws Exception {
        Member buyer = saveMember("ws-admintopic-buyer2", Role.BUYER);
        Member admin = saveMember("ws-admintopic-admin", Role.ADMIN);
        Long roomId = supportChatService.openRoom(buyer).roomId();
        roomIdsToClean.add(roomId);

        StompSession adminSession = connect(loginCookie(admin.getUsername()));
        BlockingQueue<Object> received = new LinkedBlockingQueue<>();
        adminSession.subscribe("/topic/admin/support", new StompFrameHandler() {
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

        // 관리자는 이 방을 구독하지 않았다 — 그래도 목록 갱신 신호는 와야 한다(그게 이 토픽의 이유다).
        StompSession ownerSession = connect(loginCookie(buyer.getUsername()));
        ownerSession.send("/app/support/" + roomId + "/send", new SendPayload("다른 방 메시지"));

        assertNotNull(received.poll(5, TimeUnit.SECONDS),
                "구독하지 않은 방의 변화도 관리자 목록에는 반영돼야 한다");
    }
}
