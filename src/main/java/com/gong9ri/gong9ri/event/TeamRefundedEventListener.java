package com.gong9ri.gong9ri.event;

import com.gong9ri.gong9ri.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@code TeamRefundedEvent} 구독자 — 환불 트랜잭션이 실제로 "커밋된 이후"에만 반응해야 하므로
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}를 쓴다(커밋 전/롤백 시 알림이 남으면
 * 정합성이 깨진다는 리스크를 정확히 이 애노테이션으로 해소한다, docs/dev/ongoing/refund-event-messaging.md).
 * 그 팀의 환불된 결제 구매자 전원 + 상품 판매자에게 Notification을 각각 하나씩 생성한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TeamRefundedEventListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTeamRefunded(TeamRefundedEvent event) {
        notificationService.createTeamRefundedNotifications(event);
    }
}
