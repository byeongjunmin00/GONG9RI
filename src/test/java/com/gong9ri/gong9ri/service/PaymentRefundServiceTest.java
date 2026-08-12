package com.gong9ri.gong9ri.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gong9ri.gong9ri.client.PortOneCancelResult;
import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.PaymentStatus;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.event.TeamRefundedEvent;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.SellerRevenueSummaryRepository;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * {@code PaymentRefundService} 순수 단위 테스트(Mockito) — PortOne 취소 응답별 상태 전환(성공/비동기
 * 대기/실패)과 웹훅 기반 최종 확정의 멱등성을 검증한다(docs/dev/payment/portone/design.md). 실제
 * PortOne 호출은 이 서비스 밖(TeamPaymentsRefundRequestedEventListener)에서 일어나므로 여기서는
 * {@code PortOneCancelResult}를 직접 주입해 결과 반영 로직만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PaymentRefundServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private SellerRevenueSummaryRepository sellerRevenueSummaryRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PaymentRefundService paymentRefundService;

    @BeforeEach
    void setUp() {
        paymentRefundService = new PaymentRefundService(paymentRepository, sellerRevenueSummaryRepository, eventPublisher);
    }

    // Payment는 protected no-args 생성자 + package-private setter 없이 리플렉션으로 id를 주입해야
    // findCancelTarget()의 CancelTarget.paymentId() 값을 결정론적으로 검증할 수 있다.
    private void setId(Payment payment, Long id) {
        try {
            Field field = Payment.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(payment, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Payment paidPayment(Member member, Product product, GroupBuyTeam team, Integer amount, String pgPaymentId, Long id) {
        Payment payment = new Payment(member, product, team, amount, pgPaymentId);
        payment.confirm(); // PENDING -> PAID
        setId(payment, id);
        return payment;
    }

    @Test
    @DisplayName("findCancelTarget: PAID 결제만 취소 대상으로 반환한다")
    void findCancelTarget_onlyReturnsForPaidPayments() {
        Member member = mock(Member.class);
        Product product = mock(Product.class);
        Payment payment = paidPayment(member, product, null, 10000, "pay_1", 1L);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        Optional<PaymentRefundService.CancelTarget> target = paymentRefundService.findCancelTarget(1L);

        assertTrue(target.isPresent());
        assertEquals("pay_1", target.get().pgPaymentId());
    }

    @Test
    @DisplayName("findCancelTarget: 이미 REFUNDED인 결제는 대상이 아니다")
    void findCancelTarget_alreadyRefunded_returnsEmpty() {
        Member member = mock(Member.class);
        Product product = mock(Product.class);
        Payment payment = paidPayment(member, product, null, 10000, "pay_2", 2L);
        payment.refund();
        when(paymentRepository.findById(2L)).thenReturn(Optional.of(payment));

        assertTrue(paymentRefundService.findCancelTarget(2L).isEmpty());
    }

    @Test
    @DisplayName("applyCancelResult: SUCCEEDED면 REFUNDED로 확정하고 매출 요약을 감소시키며 TeamRefundedEvent를 발행한다")
    void applyCancelResult_succeeded_refundsAndPublishesEvent() {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(100L);
        Product product = mock(Product.class);
        Member seller = mock(Member.class);
        when(seller.getId()).thenReturn(200L);
        when(product.getSeller()).thenReturn(seller);
        GroupBuyTeam team = mock(GroupBuyTeam.class);
        when(team.getId()).thenReturn(300L);

        Payment payment = paidPayment(member, product, team, 10000, "pay_3", 3L);
        when(paymentRepository.findById(3L)).thenReturn(Optional.of(payment));
        when(sellerRevenueSummaryRepository.applyRefund(anyLong(), anyInt(), any())).thenReturn(1);

        paymentRefundService.applyCancelResult(3L, new PortOneCancelResult(PortOneCancelResult.SUCCEEDED));

        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
        verify(sellerRevenueSummaryRepository).applyRefund(200L, 10000, 1L);
        verify(eventPublisher).publishEvent(new TeamRefundedEvent(300L, 200L, java.util.List.of(100L)));
    }

    @Test
    @DisplayName("applyCancelResult: REQUESTED면 REFUND_PENDING으로 대기하고 매출 요약은 건드리지 않는다")
    void applyCancelResult_requested_marksRefundPendingWithoutTouchingSummary() {
        Member member = mock(Member.class);
        Product product = mock(Product.class);
        Payment payment = paidPayment(member, product, null, 10000, "pay_4", 4L);
        when(paymentRepository.findById(4L)).thenReturn(Optional.of(payment));

        paymentRefundService.applyCancelResult(4L, new PortOneCancelResult(PortOneCancelResult.REQUESTED));

        assertEquals(PaymentStatus.REFUND_PENDING, payment.getStatus());
        verify(sellerRevenueSummaryRepository, never()).applyRefund(anyLong(), anyInt(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("applyCancelResult: FAILED 응답이면 PAID 상태를 그대로 유지한다(수동 확인 필요)")
    void applyCancelResult_failed_keepsPaidStatus() {
        Member member = mock(Member.class);
        Product product = mock(Product.class);
        Payment payment = paidPayment(member, product, null, 10000, "pay_5", 5L);
        when(paymentRepository.findById(5L)).thenReturn(Optional.of(payment));

        paymentRefundService.applyCancelResult(5L, new PortOneCancelResult(PortOneCancelResult.FAILED));

        assertEquals(PaymentStatus.PAID, payment.getStatus());
        verify(sellerRevenueSummaryRepository, never()).applyRefund(anyLong(), anyInt(), any());
    }

    @Test
    @DisplayName("applyCancelResult: 이미 PAID가 아닌 결제(중복 처리)는 아무 것도 하지 않는다(멱등)")
    void applyCancelResult_notPaidAnymore_isIdempotentNoOp() {
        Member member = mock(Member.class);
        Product product = mock(Product.class);
        Payment payment = paidPayment(member, product, null, 10000, "pay_6", 6L);
        payment.refund(); // 이미 REFUNDED
        when(paymentRepository.findById(6L)).thenReturn(Optional.of(payment));

        paymentRefundService.applyCancelResult(6L, new PortOneCancelResult(PortOneCancelResult.SUCCEEDED));

        verify(sellerRevenueSummaryRepository, never()).applyRefund(anyLong(), anyInt(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("confirmRefundedByPgPaymentId: 웹훅으로 최종 확정되면 REFUNDED로 전환된다")
    void confirmRefundedByPgPaymentId_confirmsRefund() {
        // team이 null(혼자구매)이라 TeamRefundedEvent 발행 분기(payment.getMember().getId() 사용)를
        // 타지 않는다 — member.getId() 스텁은 불필요하므로 두지 않는다(strict stub 위반 방지).
        Member member = mock(Member.class);
        Product product = mock(Product.class);
        Member seller = mock(Member.class);
        when(seller.getId()).thenReturn(200L);
        when(product.getSeller()).thenReturn(seller);

        Payment payment = paidPayment(member, product, null, 10000, "pay_7", 7L);
        payment.markRefundPending(); // PortOne이 REQUESTED로 응답했던 상태를 재현
        when(paymentRepository.findByPgPaymentId("pay_7")).thenReturn(Optional.of(payment));
        when(sellerRevenueSummaryRepository.applyRefund(anyLong(), anyInt(), any())).thenReturn(1);

        paymentRefundService.confirmRefundedByPgPaymentId("pay_7");

        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
    }

    @Test
    @DisplayName("confirmRefundedByPgPaymentId: 이미 REFUNDED면 중복 웹훅을 무시한다(멱등)")
    void confirmRefundedByPgPaymentId_alreadyRefunded_isIdempotent() {
        Member member = mock(Member.class);
        Product product = mock(Product.class);
        Payment payment = paidPayment(member, product, null, 10000, "pay_8", 8L);
        payment.refund();
        when(paymentRepository.findByPgPaymentId("pay_8")).thenReturn(Optional.of(payment));

        paymentRefundService.confirmRefundedByPgPaymentId("pay_8");

        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
        verify(sellerRevenueSummaryRepository, never()).applyRefund(anyLong(), anyInt(), any());
    }
}
