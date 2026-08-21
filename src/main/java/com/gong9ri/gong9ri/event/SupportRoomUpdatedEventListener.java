package com.gong9ri.gong9ri.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 상담방 변화를 관리자 화면에 브로드캐스트한다 (support/chat).
 *
 * <p><b>AFTER_COMMIT인 이유</b>: 커밋 전에 보내면 관리자 화면이 목록을 다시 불러왔을 때 아직 그
 * 메시지가 DB에 없어서 "신호는 왔는데 아무 변화가 없는" 상태가 된다(TeamCapacityChangedEventListener와
 * 같은 이유).
 *
 * <p>브로드캐스트 실패가 원래 작업(메시지 저장)을 되돌리면 안 되므로 예외를 삼킨다 — 목록 갱신은
 * 놓쳐도 새로고침하면 복구되지만, 저장된 메시지가 사라지면 복구할 방법이 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupportRoomUpdatedEventListener {

    /** 관리자만 구독할 수 있다 — SupportChatChannelInterceptor가 막는다. */
    public static final String ADMIN_TOPIC = "/topic/admin/support";

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(SupportRoomUpdatedEvent event) {
        try {
            messagingTemplate.convertAndSend(ADMIN_TOPIC, event);
        } catch (Exception e) {
            log.warn("상담 목록 갱신 브로드캐스트 실패(메시지는 이미 저장됨, 무시): roomId={}, cause={}",
                    event.roomId(), e.toString());
        }
    }
}
