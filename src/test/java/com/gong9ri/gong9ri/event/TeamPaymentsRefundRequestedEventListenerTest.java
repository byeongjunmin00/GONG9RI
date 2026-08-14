package com.gong9ri.gong9ri.event;

import static org.mockito.Mockito.verify;

import com.gong9ri.gong9ri.service.PaymentCancellationExecutor;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@code TeamPaymentsRefundRequestedEventListener} 순수 단위 테스트(Mockito) — 이제 실제 PortOne 취소
 * 호출/결과 반영은 {@code PaymentCancellationExecutor}로 추출됐으므로(docs/dev/ongoing/
 * team-leave-and-refund-request.md), 이 리스너는 "대상 결제마다 고정된 사유 문구로 그 실행기를
 * 호출하는 라우팅"만 검증한다. 실행기 자체의 대상 없음/실패 격리 로직은
 * {@code PaymentCancellationExecutorTest}가 검증한다. {@code @Async}/{@code @TransactionalEventListener}
 * 자체의 스프링 배선은 event/TeamDeadlineEventFlowTest에서 end-to-end로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class TeamPaymentsRefundRequestedEventListenerTest {

    @Mock
    private PaymentCancellationExecutor paymentCancellationExecutor;

    private TeamPaymentsRefundRequestedEventListener listener;

    private void setUp() {
        listener = new TeamPaymentsRefundRequestedEventListener(paymentCancellationExecutor);
    }

    @Test
    @DisplayName("대상 결제마다 공구팀 미성사 사유로 PaymentCancellationExecutor를 호출한다")
    void handle_callsExecutorForEachPaymentWithDeadlineReason() {
        setUp();

        listener.handle(new TeamPaymentsRefundRequestedEvent(10L, List.of(1L, 2L)));

        verify(paymentCancellationExecutor).cancelOne(1L, "공구팀 미성사로 인한 환불");
        verify(paymentCancellationExecutor).cancelOne(2L, "공구팀 미성사로 인한 환불");
    }
}
