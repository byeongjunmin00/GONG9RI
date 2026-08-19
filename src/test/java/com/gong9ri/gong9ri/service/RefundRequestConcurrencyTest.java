package com.gong9ri.gong9ri.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.gong9ri.gong9ri.client.PortOneCancelResult;
import com.gong9ri.gong9ri.client.PortOneClient;
import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.RefundRequest;
import com.gong9ri.gong9ri.entity.RefundRequestStatus;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.RefundRequestRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * RefundRequestService.approve()/reject()의 비관적 락(lockAndVerifyOwner) 검증 — 실제 동시 요청
 * 상황을 재현해야 해서 TeamConcurrencyTest와 동일하게 의도적으로 @Transactional을 안 쓴다(워커
 * 스레드가 메인 스레드와 별도 DB 커넥션/트랜잭션을 쓰므로 롤백 방식이면 서로의 미커밋 데이터를 못 봄).
 * 승인 성공 시 AFTER_COMMIT으로 실제 PortOne 취소 API를 호출하려 들기 때문에 PortOneClient를 목으로
 * 대체한다(다른 결제/환불 테스트와 동일 원칙 — 자동 테스트에 실제 외부 API 호출을 넣지 않음).
 */
@SpringBootTest
class RefundRequestConcurrencyTest {

    // 이 테스트는 동시성만 검증한다 — 알림 발행을 목으로 끊어서 (1) 알림 row가 team/member를 FK로
    // 참조해 정리(@AfterEach)를 막는 문제와 (2) 알림 생성이 비동기라 정리 시점과 경합하는 문제를
    // 아예 없앤다. 알림 수신자 검증은 NotificationTypesFlowTest가 따로 한다.
    @MockitoBean
    private NotificationPublisher notificationPublisher;

    @Autowired
    private RefundRequestService refundRequestService;

    @MockitoBean
    private PortOneClient portOneClient;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RefundRequestRepository refundRequestRepository;

    private final List<Long> memberIdsToClean = new ArrayList<>();
    private Long productIdToClean;
    private Long paymentIdToClean;
    private Long refundRequestIdToClean;

    @BeforeEach
    void stubPortOneCancelSucceeded() {
        when(portOneClient.cancelPayment(anyString(), anyString()))
                .thenReturn(new PortOneCancelResult(PortOneCancelResult.SUCCEEDED));
    }

    @AfterEach
    void cleanUp() {
        if (refundRequestIdToClean != null) {
            refundRequestRepository.deleteById(refundRequestIdToClean);
        }
        if (paymentIdToClean != null) {
            paymentRepository.deleteById(paymentIdToClean);
        }
        if (productIdToClean != null) {
            productRepository.deleteById(productIdToClean);
        }
        memberIdsToClean.forEach(memberRepository::deleteById);
        memberIdsToClean.clear();
    }

    @Test
    @DisplayName("같은 환불 요청을 동시에 여러 번 승인해도 정확히 한 번만 처리된다 (비관적 락 검증)")
    void concurrentApprove_onlyOneSucceeds() throws InterruptedException {
        int attempts = 8;

        Member seller = memberRepository.save(
                new Member("conc-refund-seller", "pw", "판매자", "conc-refund-seller@test.com", Role.SELLER));
        memberIdsToClean.add(seller.getId());

        Member buyer = memberRepository.save(
                new Member("conc-refund-buyer", "pw", "구매자", "conc-refund-buyer@test.com", Role.BUYER));
        memberIdsToClean.add(buyer.getId());

        Product product = productRepository.save(
                new Product(seller, "동시성테스트상품(환불)", "설명", 10000, 5, null));
        productIdToClean = product.getId();

        Payment payment = paymentRepository.save(new Payment(buyer, product, null, 10000));
        paymentIdToClean = payment.getId();

        RefundRequest refundRequest = refundRequestRepository.save(new RefundRequest(payment, buyer, "단순 변심"));
        refundRequestIdToClean = refundRequest.getId();

        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch readyLatch = new CountDownLatch(attempts);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(attempts);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger alreadyDecidedCount = new AtomicInteger();

        for (int i = 0; i < attempts; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    refundRequestService.approve(new MemberUserDetails(seller), refundRequest.getId());
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    alreadyDecidedCount.incrementAndGet();
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

        assertEquals(true, finished, "모든 승인 요청이 제한시간 안에 끝나야 한다");
        assertEquals(1, successCount.get(), "정확히 한 번만 승인 처리돼야 한다");
        assertEquals(attempts - 1, alreadyDecidedCount.get(), "나머지는 REFUND_REQUEST_ALREADY_DECIDED로 막혀야 한다");

        RefundRequest finalState = refundRequestRepository.findById(refundRequest.getId()).orElseThrow();
        assertEquals(RefundRequestStatus.APPROVED, finalState.getStatus());
    }
}
