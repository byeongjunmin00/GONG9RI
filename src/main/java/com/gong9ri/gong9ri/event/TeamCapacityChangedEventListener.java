package com.gong9ri.gong9ri.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@code TeamCapacityChangedEvent} 구독자 — 참가 트랜잭션이 실제로 커밋된 이후에만 브로드캐스트해야
 * 롤백된 참가가 화면에 유령처럼 나타나지 않는다({@code TeamRefundedEventListener}와 동일한 이유로
 * {@code AFTER_COMMIT}을 쓴다). 이 콜백에서 예외가 나도 Spring이 로그만 남기고 호출자(참가 요청)에게
 * 전파하지 않는다 — 실시간 브로드캐스트 실패가 참가 자체를 실패시키면 안 된다는 원칙과 정확히 맞아떨어짐
 * (실제 재현 검증: docs/logs/team/crud/006-realtime-messaging.md).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TeamCapacityChangedEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTeamCapacityChanged(TeamCapacityChangedEvent event) {
        messagingTemplate.convertAndSend("/topic/products/" + event.productId() + "/teams", event.payload());
    }
}
