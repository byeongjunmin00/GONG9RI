package com.gong9ri.gong9ri.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gong9ri.gong9ri.client.PortOneCancelResult;
import com.gong9ri.gong9ri.client.PortOneClient;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@code PaymentCancellationExecutor} 순수 단위 테스트(Mockito) — 원래 {@code
 * TeamPaymentsRefundRequestedEventListenerTest}에 있던 검증(취소 대상 조회 → PortOne 호출 → 결과
 * 반영, 대상 아님/실패 시 격리)을 그대로 옮겨왔다(docs/dev/ongoing/team-leave-and-refund-request.md —
 * 취소 실행 로직 추출).
 */
@ExtendWith(MockitoExtension.class)
class PaymentCancellationExecutorTest {

    @Mock
    private PaymentRefundService paymentRefundService;

    @Mock
    private PortOneClient portOneClient;

    private PaymentCancellationExecutor executor;

    private void setUp() {
        executor = new PaymentCancellationExecutor(paymentRefundService, portOneClient);
    }

    @Test
    @DisplayName("취소 대상이면 PortOne 취소 API를 호출하고 결과를 반영한다")
    void cancelOne_target_callsPortOneAndAppliesResult() {
        setUp();
        when(paymentRefundService.findCancelTarget(1L))
                .thenReturn(Optional.of(new PaymentRefundService.CancelTarget(1L, "pay_1")));
        PortOneCancelResult succeeded = new PortOneCancelResult(PortOneCancelResult.SUCCEEDED);
        when(portOneClient.cancelPayment("pay_1", "사유")).thenReturn(succeeded);

        executor.cancelOne(1L, "사유");

        verify(portOneClient).cancelPayment("pay_1", "사유");
        verify(paymentRefundService).applyCancelResult(1L, succeeded);
    }

    @Test
    @DisplayName("취소 대상이 아니면(이미 처리됨) PortOne을 호출하지 않는다")
    void cancelOne_noTarget_skipsPortOneCall() {
        setUp();
        when(paymentRefundService.findCancelTarget(1L)).thenReturn(Optional.empty());

        executor.cancelOne(1L, "사유");

        verify(portOneClient, never()).cancelPayment(anyString(), anyString());
        verify(paymentRefundService, never()).applyCancelResult(anyLong(), any());
    }

    @Test
    @DisplayName("PortOne 호출이 실패해도 예외를 전파하지 않고 조용히 로그만 남긴다")
    void cancelOne_portOneThrows_doesNotThrow() {
        setUp();
        when(paymentRefundService.findCancelTarget(1L))
                .thenReturn(Optional.of(new PaymentRefundService.CancelTarget(1L, "pay_1")));
        when(portOneClient.cancelPayment("pay_1", "사유")).thenThrow(new RuntimeException("network error"));

        assertDoesNotThrow(() -> executor.cancelOne(1L, "사유"));

        verify(paymentRefundService, never()).applyCancelResult(anyLong(), any());
    }
}
