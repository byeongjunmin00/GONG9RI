package com.gong9ri.gong9ri.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gong9ri.gong9ri.client.PortOneClient;
import com.gong9ri.gong9ri.client.PortOnePaymentDetail;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.PaymentStatus;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.entity.SellerRevenueSummary;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.SellerRevenueSummaryRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * PaymentService.confirm()(클라이언트)과 confirmByPgPaymentId()(웹훅)가 같은 결제를 거의 동시에
 * 확정하려 들어도 정확히 한 번만 확정되는지 검증한다(비관적 락 도입, docs/dev/ongoing/
 * payment-confirm-concurrency-lock.md) — 락 없이는 둘 다 PENDING을 읽고 통과해 판매자 수익이
 * 두 번 증가하고 "결제 발생" 알림도 두 번 발행될 수 있었다.
 *
 * RefundRequestConcurrencyTest와 같은 이유로 @Transactional을 쓰지 않는다(워커 스레드가 메인
 * 스레드와 별도 DB 커넥션/트랜잭션을 쓰므로 롤백 방식이면 서로의 미커밋 데이터를 못 봄). 알림 발행은
 * 목으로 대체해 member FK 정리 문제와 비동기 경합을 없앤다(알림 수신자 검증은 NotificationTypesFlowTest 담당).
 */
@SpringBootTest
class PaymentConfirmConcurrencyTest {

    @MockitoBean
    private NotificationPublisher notificationPublisher;

    @MockitoBean
    private PortOneClient portOneClient;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private SellerRevenueSummaryRepository sellerRevenueSummaryRepository;

    private final List<Long> memberIdsToClean = new ArrayList<>();
    private Long productIdToClean;
    private Long paymentIdToClean;
    private Long summaryIdToClean;

    @BeforeEach
    void stubPortOnePaid() {
        when(portOneClient.getPayment(anyString()))
                .thenReturn(new PortOnePaymentDetail("PAID", 10_000));
    }

    @AfterEach
    void cleanUp() {
        if (paymentIdToClean != null) {
            paymentRepository.deleteById(paymentIdToClean);
        }
        if (summaryIdToClean != null) {
            sellerRevenueSummaryRepository.deleteById(summaryIdToClean);
        }
        if (productIdToClean != null) {
            productRepository.deleteById(productIdToClean);
        }
        memberIdsToClean.forEach(memberRepository::deleteById);
        memberIdsToClean.clear();
    }

    @Test
    @DisplayName("같은 결제를 클라이언트 confirm()과 웹훅 confirmByPgPaymentId()가 동시에 확정 시도해도 정확히 한 번만 확정된다 (비관적 락 검증)")
    void concurrentConfirmAndWebhook_onlyOneSucceeds() throws InterruptedException {
        int attempts = 10;

        Member seller = memberRepository.save(
                new Member("conc-pay-seller", "pw", "판매자", "conc-pay-seller@test.com", Role.SELLER));
        memberIdsToClean.add(seller.getId());

        Member buyer = memberRepository.save(
                new Member("conc-pay-buyer", "pw", "구매자", "conc-pay-buyer@test.com", Role.BUYER));
        memberIdsToClean.add(buyer.getId());

        Product product = productRepository.save(
                new Product(seller, "동시성테스트상품(결제)", "설명", 10_000, 5, null));
        productIdToClean = product.getId();

        String pgPaymentId = "pay_conc_test";
        Payment payment = paymentRepository.save(new Payment(buyer, product, null, 10_000, pgPaymentId));
        paymentIdToClean = payment.getId();

        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch readyLatch = new CountDownLatch(attempts);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(attempts);

        for (int i = 0; i < attempts; i++) {
            boolean viaWebhook = i % 2 == 0;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    if (viaWebhook) {
                        paymentService.confirmByPgPaymentId(pgPaymentId);
                    } else {
                        paymentService.confirm(new MemberUserDetails(buyer), payment.getId());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        boolean finished = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "모든 확정 시도가 제한시간 안에 끝나야 한다");

        Payment finalState = paymentRepository.findById(payment.getId()).orElseThrow();
        assertEquals(PaymentStatus.PAID, finalState.getStatus());

        SellerRevenueSummary summary = sellerRevenueSummaryRepository.findBySellerId(seller.getId()).orElseThrow();
        summaryIdToClean = summary.getId();
        assertEquals(10_000, summary.getTotalRevenue(), "판매자 수익이 정확히 한 번만 반영돼야 한다(중복 확정 방지)");
        assertEquals(1L, summary.getPaidCount(), "결제 확정 건수가 정확히 1이어야 한다(중복 확정 방지)");

        verify(notificationPublisher, times(1))
                .paymentReceived(eq(seller.getId()), eq(buyer.getId()), eq(product.getName()), eq(10_000));
    }
}
