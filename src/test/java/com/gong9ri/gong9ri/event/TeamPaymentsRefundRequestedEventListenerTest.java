package com.gong9ri.gong9ri.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gong9ri.gong9ri.client.PortOneCancelResult;
import com.gong9ri.gong9ri.client.PortOneClient;
import com.gong9ri.gong9ri.service.PaymentRefundService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@code TeamPaymentsRefundRequestedEventListener} 순수 단위 테스트(Mockito) — 취소 대상 결제마다
 * PortOne 취소 API를 호출하고 그 결과를 반영하는지, 대상이 아니거나 호출이 실패해도 다른 결제 처리를
 * 막지 않는지 검증한다(docs/dev/payment/portone/design.md). {@code @Async}/{@code
 * @TransactionalEventListener} 자체의 스프링 배선은 event/TeamDeadlineEventFlowTest에서 end-to-end로
 * 검증한다 — 여기서는 handle() 메서드의 순수 로직만 본다.
 */
@ExtendWith(MockitoExtension.class)
class TeamPaymentsRefundRequestedEventListenerTest {

    @Mock
    private PaymentRefundService paymentRefundService;

    @Mock
    private PortOneClient portOneClient;

    private TeamPaymentsRefundRequestedEventListener listener;

    private void setUp() {
        listener = new TeamPaymentsRefundRequestedEventListener(paymentRefundService, portOneClient);
    }

    @Test
    @DisplayName("취소 대상인 결제마다 PortOne 취소 API를 호출하고 결과를 반영한다")
    void handle_callsCancelAndAppliesResultForEachTarget() {
        setUp();
        when(paymentRefundService.findCancelTarget(1L))
                .thenReturn(Optional.of(new PaymentRefundService.CancelTarget(1L, "pay_1")));
        when(paymentRefundService.findCancelTarget(2L))
                .thenReturn(Optional.of(new PaymentRefundService.CancelTarget(2L, "pay_2")));
        PortOneCancelResult succeeded = new PortOneCancelResult(PortOneCancelResult.SUCCEEDED);
        when(portOneClient.cancelPayment(anyString(), anyString())).thenReturn(succeeded);

        listener.handle(new TeamPaymentsRefundRequestedEvent(10L, List.of(1L, 2L)));

        verify(portOneClient).cancelPayment("pay_1", "공구팀 미성사로 인한 환불");
        verify(portOneClient).cancelPayment("pay_2", "공구팀 미성사로 인한 환불");
        verify(paymentRefundService).applyCancelResult(1L, succeeded);
        verify(paymentRefundService).applyCancelResult(2L, succeeded);
    }

    @Test
    @DisplayName("취소 대상이 아니면(이미 처리됨) PortOne을 호출하지 않는다")
    void handle_noCancelTarget_skipsPortOneCall() {
        setUp();
        when(paymentRefundService.findCancelTarget(1L)).thenReturn(Optional.empty());

        listener.handle(new TeamPaymentsRefundRequestedEvent(10L, List.of(1L)));

        verify(portOneClient, never()).cancelPayment(anyString(), anyString());
        verify(paymentRefundService, never()).applyCancelResult(anyLong(), any());
    }

    @Test
    @DisplayName("한 결제의 PortOne 호출이 실패해도 예외를 전파하지 않고(다른 결제 처리를 막지 않음) 조용히 로그만 남긴다")
    void handle_oneCallFails_doesNotThrowAndContinues() {
        setUp();
        when(paymentRefundService.findCancelTarget(1L))
                .thenReturn(Optional.of(new PaymentRefundService.CancelTarget(1L, "pay_1")));
        when(paymentRefundService.findCancelTarget(2L))
                .thenReturn(Optional.of(new PaymentRefundService.CancelTarget(2L, "pay_2")));
        when(portOneClient.cancelPayment("pay_1", "공구팀 미성사로 인한 환불"))
                .thenThrow(new RuntimeException("network error"));
        PortOneCancelResult succeeded = new PortOneCancelResult(PortOneCancelResult.SUCCEEDED);
        when(portOneClient.cancelPayment("pay_2", "공구팀 미성사로 인한 환불")).thenReturn(succeeded);

        assertDoesNotThrow(() -> listener.handle(new TeamPaymentsRefundRequestedEvent(10L, List.of(1L, 2L))));

        verify(paymentRefundService, never()).applyCancelResult(eq(1L), any());
        verify(paymentRefundService).applyCancelResult(2L, succeeded);
    }
}
