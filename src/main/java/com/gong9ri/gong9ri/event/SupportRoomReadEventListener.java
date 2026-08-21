package com.gong9ri.gong9ri.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 읽음 표시를 <b>상대 화면에</b> 실시간으로 반영한다 (support/chat, 2026-08-21).
 *
 * <p>이 신호가 없으면 "읽음"은 상대가 새로고침해야만 보인다 — 읽음 표시는 지금 읽혔는지가 궁금한
 * 기능이라 뒤늦게 보이면 의미가 절반이다.
 *
 * <p>AFTER_COMMIT인 이유와 예외를 삼키는 이유는 {@link SupportRoomUpdatedEventListener}와 같다 —
 * 커밋 전에 보내면 상대가 다시 조회했을 때 아직 읽은 시각이 반영돼 있지 않고, 브로드캐스트 실패가
 * 읽음 처리 자체를 되돌리면 안 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupportRoomReadEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(SupportRoomReadEvent event) {
        try {
            // 방 토픽으로 보낸다 — 구독 권한은 SupportChatChannelInterceptor가 이미 막고 있으므로
            // 당사자와 관리자만 받는다.
            messagingTemplate.convertAndSend("/topic/support/" + event.roomId(), event);
        } catch (Exception e) {
            log.warn("읽음 표시 브로드캐스트 실패(읽음 처리는 이미 반영됨, 무시): roomId={}, cause={}",
                    event.roomId(), e.toString());
        }
    }
}
