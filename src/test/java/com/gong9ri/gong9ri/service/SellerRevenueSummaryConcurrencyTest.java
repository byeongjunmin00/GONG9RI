package com.gong9ri.gong9ri.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.gong9ri.gong9ri.client.PortOneClient;
import com.gong9ri.gong9ri.client.PortOnePaymentDetail;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.PaymentCreateRequest;
import com.gong9ri.gong9ri.dto.PaymentResponse;
import com.gong9ri.gong9ri.dto.RevenueResponse;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.entity.SellerRevenueSummary;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.NotificationRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.RevenueSummaryProjection;
import com.gong9ri.gong9ri.repository.SellerRevenueSummaryRepository;
import java.util.ArrayList;
import java.util.Collections;
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
 * 여러 요청이 동시에 같은 판매자에게 결제를 넣어도 seller_revenue_summary의 원자적 upsert
 * (incrementPaid, MySQL INSERT ... ON DUPLICATE KEY UPDATE, 비관적 락 미사용)가 최종 합계를
 * 정확히 유지하는지 검증한다.
 *
 * <p><b>PortOne 연동 이후</b>: {@code incrementPaid}는 이제 {@code confirm()} 시점에 호출된다
 * (docs/dev/payment/portone/design.md) — 그래서 각 워커 스레드는 {@code create()} 직후 곧바로
 * {@code confirm()}까지 호출한다(PortOneClient는 목으로 대체해 항상 요청 금액 그대로 PAID를 반환).
 * 동시성 경쟁 지점 자체(요약 행이 없는 상태에서 여러 upsert가 동시에 도는 것)는 그대로 confirm()
 * 호출 시점으로 옮겨졌을 뿐 동일하다.
 *
 * 특히 이 테스트는 **요약 행이 아직 전혀 없는 상태**(그 판매자의 "첫 결제"들)에서 여러 결제가
 * 동시에 몰리는 경쟁 상황을 정면으로 검증한다(docs/dev/mypage/view/changes/004-upsert-fix.md) —
 * 이전 방식(조건부 UPDATE + 조회 시 지연 부트스트랩)에서는 이 경쟁 상황에서 결제가 유실될 수 있었다.
 * 그래서 스레드를 시작하기 전에 요약 행을 미리 만들어두지 않는다(예전엔 sellerMypageService.revenue()로
 * 0행을 부트스트랩해뒀지만, 이제 그 부트스트랩 자체가 없어졌고, 없어도 되는 게 이번 수정의 핵심이다).
 *
 * TeamConcurrencyTest와 같은 이유로 이 클래스는 의도적으로 @Transactional을 안 쓴다 —
 * 워커 스레드들이 메인 테스트 스레드와 별도의 DB 커넥션/트랜잭션을 쓰기 때문에,
 * 롤백 방식이면 워커 스레드가 메인 스레드가 만든(아직 커밋 안 된) 데이터를 못 본다.
 * 대신 테스트 끝나고 직접 정리(cleanup)한다.
 */
@SpringBootTest
class SellerRevenueSummaryConcurrencyTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private SellerMypageService sellerMypageService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private SellerRevenueSummaryRepository sellerRevenueSummaryRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @MockitoBean
    private PortOneClient portOneClient;

    @BeforeEach
    void stubPortOnePaid() {
        when(portOneClient.getPayment(anyString())).thenAnswer(invocation -> {
            String pgPaymentId = invocation.getArgument(0);
            Payment payment = paymentRepository.findByPgPaymentId(pgPaymentId).orElseThrow();
            return new PortOnePaymentDetail("PAID", payment.getAmount());
        });
    }

    private static final long ASYNC_WAIT_TIMEOUT_MS = 5_000L;
    private static final long ASYNC_WAIT_INTERVAL_MS = 50L;

    private final List<Long> memberIdsToClean = new ArrayList<>();
    private final List<Long> paymentIdsToClean = new ArrayList<>();
    private Long productIdToClean;
    private Long summaryIdToClean;
    private Long sellerIdForAsyncWait;
    private int expectedNotificationCount;

    @AfterEach
    void cleanUp() {
        // 결제가 확정되면 판매자에게 "결제 발생" 알림이 생긴다(notification) — member를 FK로 참조하므로
        // 회원보다 먼저 지운다. 알림 생성이 @Async라 아직 안 왔을 수 있어서, 실제로 다 들어올 때까지
        // 폴링한다(NotificationTypesFlowTest.waitUntil과 동일 패턴 — 고정 sleep은 부하가 크면 놓칠 수
        // 있어 간헐적 FK 위반 실패의 원인이었다, 코드리뷰 2026-08-20 발견). 이 테스트는 알림 내용 자체를
        // 검증하지 않는다 — NotificationTypesFlowTest 담당.
        waitForAsyncNotifications();
        memberIdsToClean.forEach(memberId ->
                notificationRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId)
                        .forEach(n -> notificationRepository.deleteById(n.getId())));
        paymentIdsToClean.forEach(paymentRepository::deleteById);
        paymentIdsToClean.clear();
        if (summaryIdToClean != null) {
            sellerRevenueSummaryRepository.deleteById(summaryIdToClean);
        }
        if (productIdToClean != null) {
            productRepository.deleteById(productIdToClean);
        }
        memberIdsToClean.forEach(memberRepository::deleteById);
        memberIdsToClean.clear();
    }

    // @Async 알림 리스너가 정리 도중에 뒤늦게 INSERT해서 FK 위반을 일으키지 않도록, 판매자에게
    // 기대한 알림 건수가 실제로 다 들어올 때까지 폴링한다. 아직 결제를 만들지 않은 상태(예: 테스트
    // 준비 단계에서 실패)에서는 기다릴 대상이 없으므로 곧바로 반환한다.
    private void waitForAsyncNotifications() {
        if (sellerIdForAsyncWait == null) {
            return;
        }
        long deadline = System.currentTimeMillis() + ASYNC_WAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            int actual = notificationRepository.findAllByMemberIdOrderByCreatedAtDesc(sellerIdForAsyncWait).size();
            if (actual >= expectedNotificationCount) {
                return;
            }
            try {
                Thread.sleep(ASYNC_WAIT_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("알림 대기 중 인터럽트됨");
            }
        }
        fail("판매자 알림이 제한시간 안에 기대한 건수(" + expectedNotificationCount + ")만큼 도착하지 않음");
    }

    @Test
    @DisplayName("요약 행이 아직 없는 판매자에게 동시에 여러 '첫 결제'가 들어와도 최종 합계가 정확하다 (upsert 경쟁 상태 검증)")
    void concurrentFirstPayments_summaryStaysConsistentEvenWithoutExistingRow() throws InterruptedException {
        int threadCount = 20;

        Member seller = memberRepository.save(
                new Member("concRevenueSeller", "pw", "판매자", "concRevenueSeller@test.com", Role.SELLER));
        memberIdsToClean.add(seller.getId());
        sellerIdForAsyncWait = seller.getId();
        expectedNotificationCount = threadCount; // 결제(스레드) 건수만큼 판매자에게 PAYMENT_RECEIVED가 하나씩 간다.

        Product product = productRepository.save(
                new Product(seller, "동시성매출테스트상품", "설명", 10000, 100, null));
        productIdToClean = product.getId();

        // 이번 검증의 핵심: 요약 행을 미리 만들어두지 않는다(부트스트랩 없음). incrementPaid가 upsert라
        // 아래 스레드들이 동시에 쏘는 결제 중 "첫 결제"가 행을 만들고, 나머지는 그 행을 원자적으로
        // 증가시켜야 한다 — 유실 없이 정확히 합산돼야 한다.
        RevenueResponse initial = sellerMypageService.revenue(new MemberUserDetails(seller));
        assertEquals(0, initial.totalRevenue());
        assertTrue(sellerRevenueSummaryRepository.findBySellerId(seller.getId()).isEmpty(),
                "조회만으로는 요약 행이 생성되지 않아야 한다(지연 부트스트랩 제거 확인)");

        List<Member> buyers = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            Member buyer = memberRepository.save(new Member(
                    "concRevenueBuyer" + i, "pw", "구매자" + i, "concRevenueBuyer" + i + "@test.com", Role.BUYER));
            memberIdsToClean.add(buyer.getId());
            buyers.add(buyer);
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<Long> createdPaymentIds = Collections.synchronizedList(new ArrayList<>());

        for (Member buyer : buyers) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    PaymentResponse created = paymentService.create(
                            new MemberUserDetails(buyer), new PaymentCreateRequest(product.getId(), null));
                    paymentService.confirm(new MemberUserDetails(buyer), created.paymentId());
                    createdPaymentIds.add(created.paymentId());
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
        paymentIdsToClean.addAll(createdPaymentIds);

        assertTrue(finished, "모든 결제 요청이 제한시간 안에 끝나야 한다");
        assertEquals(threadCount, createdPaymentIds.size(), "모든 결제가 성공해야 한다(정원 제약 없음)");

        SellerRevenueSummary summary = sellerRevenueSummaryRepository.findBySellerId(seller.getId()).orElseThrow();
        summaryIdToClean = summary.getId(); // 요약 행이 동시 결제 중 하나의 upsert insert로 만들어졌으므로 여기서 캡처한다.
        assertEquals(product.getBasePrice() * threadCount, summary.getTotalRevenue(),
                "동시 결제 총액이 유실·중복 없이 정확히 반영돼야 한다(요약 행이 아예 없던 상태에서 시작한 경쟁 상황)");
        assertEquals((long) threadCount, summary.getPaidCount(), "동시 결제 건수가 정확히 반영돼야 한다");

        RevenueSummaryProjection recomputed = paymentRepository.findRevenueSummaryBySellerId(seller.getId());
        assertEquals(recomputed.getTotalRevenue(), summary.getTotalRevenue(),
                "원본 payment 재계산 값과 정확히 일치해야 한다(드리프트 없음)");
        assertEquals(recomputed.getPaidCount(), summary.getPaidCount());
    }
}
