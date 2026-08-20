package com.gong9ri.gong9ri.controller;

import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.SupportMessageResponse;
import com.gong9ri.gong9ri.dto.SupportMessageSendRequest;
import com.gong9ri.gong9ri.service.SupportChatService;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

/**
 * 상담 실시간 송수신 (support/chat).
 *
 * <p><b>저장이 먼저, 브로드캐스트가 나중이다.</b> {@code @SendTo}로 반환값을 자동 전송하지 않고
 * 직접 {@code convertAndSend}하는 이유가 이것 — 저장이 실패하면 아무것도 나가지 않아야 한다.
 *
 * <p>구독 권한은 {@code SupportChatChannelInterceptor}가 막지만, <b>발행도 여기서 다시 검사한다</b>
 * ({@code SupportChatService.send} 안의 requireParticipant). 구독만 막고 발행을 열어두면 남의 방에
 * 메시지를 밀어 넣을 수 있다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class SupportChatWsController {

    private final SupportChatService supportChatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/support/{roomId}/send")
    public void send(@DestinationVariable Long roomId, SupportMessageSendRequest request, Principal principal) {
        MemberUserDetails sender = resolve(principal);
        if (sender == null) {
            log.warn("상담 메시지 발행 거절(비로그인): roomId={}", roomId);
            return;
        }
        SupportMessageResponse saved = supportChatService.send(sender.getMember(), roomId, request.content());
        messagingTemplate.convertAndSend("/topic/support/" + roomId, saved);
    }

    /**
     * "입력 중" 표시 — 저장하지 않고 그대로 흘려보낸다. 사라져도 되는 신호라 DB에 남길 이유가 없다.
     * 다만 <b>남의 방에 신호를 보내는 것도 막아야</b> 하므로 권한 검사는 똑같이 한다.
     */
    @MessageMapping("/support/{roomId}/typing")
    public void typing(@DestinationVariable Long roomId, Principal principal) {
        MemberUserDetails sender = resolve(principal);
        if (sender == null) {
            return;
        }
        supportChatService.requireParticipant(sender.getMember(), roomId);
        messagingTemplate.convertAndSend("/topic/support/" + roomId,
                new TypingSignal(sender.getMember().getId(), sender.getMember().getName()));
    }

    /** 메시지와 구분되도록 type 필드를 둔다 — 프론트가 이걸로 갈라 처리한다. */
    private record TypingSignal(Long senderId, String senderName) {
        @SuppressWarnings("unused")
        public String getType() {
            return "TYPING";
        }
    }

    private MemberUserDetails resolve(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof MemberUserDetails details) {
            return details;
        }
        return null;
    }
}
