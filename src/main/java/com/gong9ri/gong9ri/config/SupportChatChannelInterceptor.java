package com.gong9ri.gong9ri.config;

import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.service.SupportChatService;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * 상담방 구독 권한 검사 (support/chat).
 *
 * <p><b>왜 필요한가</b> — STOMP 구독은 HTTP 인가 규칙({@code SecurityConfig})을 타지 않는다. 기존
 * 채널({@code /topic/products/../teams})은 공개 정보라 문제가 없었지만, 상담은 사적인 대화다.
 * 이 인터셉터가 없으면 <b>아무나 {@code /topic/support/{roomId}}를 구독해 남의 상담을 훔쳐볼 수 있다.</b>
 *
 * <p>발행(메시지 전송)은 STOMP {@code SEND}가 아니라 컨트롤러의 {@code @MessageMapping}이 받고, 거기서
 * 같은 판정({@code SupportChatService.requireParticipant})을 다시 한다 — 구독만 막고 발행을 열어두면
 * 남의 방에 메시지를 밀어 넣을 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupportChatChannelInterceptor implements ChannelInterceptor {

    private static final String SUPPORT_TOPIC_PREFIX = "/topic/support/";
    /** 관리자 상담 목록 갱신 신호 — 관리자만 구독할 수 있다. */
    private static final String ADMIN_TOPIC_PREFIX = "/topic/admin/";

    private final SupportChatService supportChatService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return message;
        }
        String destination = accessor.getDestination();
        boolean roomTopic = destination != null && destination.startsWith(SUPPORT_TOPIC_PREFIX);
        boolean adminTopic = destination != null && destination.startsWith(ADMIN_TOPIC_PREFIX);
        if (!roomTopic && !adminTopic) {
            // 상담 외 토픽(공구팀 정원 등)은 예전처럼 공개다 — 여기서 새로 막지 않는다.
            return message;
        }

        MemberUserDetails principal = resolvePrincipal(accessor);
        if (principal == null) {
            log.warn("상담 토픽 구독 거절(비로그인): destination={}", destination);
            throw new IllegalArgumentException("상담을 보려면 로그인이 필요합니다.");
        }

        if (adminTopic) {
            // 관리자 목록 갱신 신호에는 내용이 없지만, 구독만으로도 "상담이 몇 건 오가는지"가 새므로 막는다.
            if (principal.getMember().getRole() != Role.ADMIN) {
                log.warn("관리자 토픽 구독 거절(권한 없음): memberId={}, destination={}",
                        principal.getMember().getId(), destination);
                throw new IllegalArgumentException("관리자만 구독할 수 있습니다.");
            }
            return message;
        }

        Long roomId = parseRoomId(destination);
        // REST와 완전히 같은 판정을 쓴다. 여기서 별도 규칙을 만들면 두 경로가 어긋난다.
        supportChatService.requireParticipant(principal.getMember(), roomId);
        return message;
    }

    private MemberUserDetails resolvePrincipal(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof MemberUserDetails details) {
            return details;
        }
        return null;
    }

    private Long parseRoomId(String destination) {
        String raw = destination.substring(SUPPORT_TOPIC_PREFIX.length());
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("잘못된 상담방 주소입니다: " + destination);
        }
    }
}
