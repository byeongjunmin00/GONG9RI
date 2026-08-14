package com.gong9ri.gong9ri.event;

import com.gong9ri.gong9ri.service.PaymentCancellationExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@code TeamPaymentsRefundRequestedEvent} 구독자 — {@code TeamDeadlineService.processDeadline}의
 * 비관적 락 트랜잭션이 실제로 "커밋된 이후"에만 반응한다({@code TeamRefundedEventListener}와 같은
 * AFTER_COMMIT 원칙). 이 지점부터는 그 트랜잭션의 락이 이미 풀린 뒤이므로, 여기서 실제 PortOne 결제취소
 * API(외부 HTTP)를 호출해도 락을 붙잡는 문제가 없다.
 *
 * <p>실제 취소 호출·결과 반영은 {@code PaymentCancellationExecutor}로 추출돼 있다({@code
 * RefundRequestApprovedEventListener}와 공유, docs/dev/ongoing/team-leave-and-refund-request.md) —
 * 이 리스너는 "공구팀 미성사"라는 고정 사유 문구로 그 실행기를 호출하는 라우팅만 담당한다.
 *
 * <p>{@code @Async}로 커밋 스레드를 막지 않는다({@code AsyncConfig} 전용 스레드풀 재사용,
 * {@code TeamDeadlineEventListener}와 동일 패턴). 결제 건 하나의 취소 실패가 같은 팀의 다른 결제
 * 처리를 막지 않도록 {@code PaymentCancellationExecutor}가 건별로 예외를 잡는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TeamPaymentsRefundRequestedEventListener {

    // 공구팀 미성사 자동환불 전용 사유 — 참여 취소/판매자 승인 환불은 RefundRequestApprovedEventListener가 담당.
    private static final String DEADLINE_REFUND_REASON = "공구팀 미성사로 인한 환불";

    private final PaymentCancellationExecutor paymentCancellationExecutor;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(TeamPaymentsRefundRequestedEvent event) {
        for (Long paymentId : event.paymentIds()) {
            paymentCancellationExecutor.cancelOne(paymentId, DEADLINE_REFUND_REASON);
        }
    }
}
