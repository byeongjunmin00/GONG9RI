package com.gong9ri.gong9ri.event;

import com.gong9ri.gong9ri.service.PaymentCancellationExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@code RefundRequestApprovedEvent} 구독자 — {@code RefundRequestService.approve}(판매자 수동 승인)
 * 또는 {@code TeamService.leave}(상품별 자동환불 설정이 켜진 경우)의 트랜잭션이 커밋된 이후에만
 * 반응한다. {@code TeamService.leave}는 팀 row에 비관적 락을 잡고 있으므로, 그 락이 풀린 뒤(AFTER_COMMIT)
 * 에만 실제 PortOne 결제취소 API(외부 HTTP)를 호출해야 한다 — {@code TeamPaymentsRefundRequestedEventListener}
 * 와 동일한 원칙.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundRequestApprovedEventListener {

    private final PaymentCancellationExecutor paymentCancellationExecutor;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(RefundRequestApprovedEvent event) {
        log.info("환불 요청 승인에 따른 결제취소 실행: refundRequestId={}, paymentId={}",
                event.refundRequestId(), event.paymentId());
        paymentCancellationExecutor.cancelOne(event.paymentId(), event.reason());
    }
}
