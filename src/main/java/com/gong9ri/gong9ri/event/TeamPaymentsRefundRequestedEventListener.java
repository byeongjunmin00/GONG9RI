package com.gong9ri.gong9ri.event;

import com.gong9ri.gong9ri.client.PortOneCancelResult;
import com.gong9ri.gong9ri.client.PortOneClient;
import com.gong9ri.gong9ri.service.PaymentRefundService;
import java.util.Optional;
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
 * API(외부 HTTP)를 호출해도 락을 붙잡는 문제가 없다 — 이 메서드 자체도 트랜잭션을 열지 않는다
 * ({@code PaymentRefundService.findCancelTarget}은 짧은 읽기전용 조회, {@code applyCancelResult}만
 * 별도 트랜잭션으로 실제 DB 반영을 한다).
 *
 * <p>{@code @Async}로 커밋 스레드를 막지 않는다({@code AsyncConfig} 전용 스레드풀 재사용,
 * {@code TeamDeadlineEventListener}와 동일 패턴). 결제 건 하나의 취소 실패가 같은 팀의 다른 결제
 * 처리를 막지 않도록 건별로 예외를 잡는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TeamPaymentsRefundRequestedEventListener {

    // 공구팀 미성사 자동환불 전용 사유 — 자발적 취소 기능은 이번 스코프에 없다(docs/dev/ongoing/payment-portone.md).
    private static final String DEADLINE_REFUND_REASON = "공구팀 미성사로 인한 환불";

    private final PaymentRefundService paymentRefundService;
    private final PortOneClient portOneClient;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(TeamPaymentsRefundRequestedEvent event) {
        for (Long paymentId : event.paymentIds()) {
            cancelOne(event.teamId(), paymentId);
        }
    }

    private void cancelOne(Long teamId, Long paymentId) {
        Optional<PaymentRefundService.CancelTarget> target = paymentRefundService.findCancelTarget(paymentId);
        if (target.isEmpty()) {
            log.warn("환불취소 대상 아님(이미 처리됐거나 존재하지 않음): teamId={}, paymentId={}", teamId, paymentId);
            return;
        }

        try {
            PortOneCancelResult result = portOneClient.cancelPayment(target.get().pgPaymentId(), DEADLINE_REFUND_REASON);
            paymentRefundService.applyCancelResult(paymentId, result);
        } catch (Exception e) {
            log.error("포트원 결제취소 API 호출 실패: teamId={}, paymentId={}, pgPaymentId={}, error={}",
                    teamId, paymentId, target.get().pgPaymentId(), e.getMessage(), e);
        }
    }
}
