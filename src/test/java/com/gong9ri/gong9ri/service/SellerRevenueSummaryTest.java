package com.gong9ri.gong9ri.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.gong9ri.gong9ri.client.PortOneCancelResult;
import com.gong9ri.gong9ri.client.PortOneClient;
import com.gong9ri.gong9ri.client.PortOnePaymentDetail;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.PaymentCreateRequest;
import com.gong9ri.gong9ri.dto.PaymentResponse;
import com.gong9ri.gong9ri.dto.RevenueResponse;
import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.entity.SellerRevenueSummary;
import com.gong9ri.gong9ri.entity.TeamParticipation;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.NotificationRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.RevenueSummaryProjection;
import com.gong9ri.gong9ri.repository.SellerRevenueSummaryRepository;
import com.gong9ri.gong9ri.repository.TeamParticipationRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * mypage/seller-revenue 컬럼 집계 방식(docs/db/seller_revenue_summary.md, 2026-08-06 upsert 전환) 검증.
 * group_buy_team.current_count와 같은 방식(결제/환불 트랜잭션 안에서 seller_revenue_summary를 즉시
 * 갱신)이지만, 요약 행 생성 시점을 "조회 시(지연 부트스트랩)"에서 "결제 시(upsert)"로 정정했다
 * (docs/dev/mypage/view/changes/004-upsert-fix.md) — revenue()는 이제 순수 읽기라 조회만으로는
 * 어떤 행도 만들지 않는다.
 *
 * <p><b>PortOne 연동 이후(docs/dev/payment/portone/design.md)</b>: {@code incrementPaid}는 이제
 * {@code PaymentService.create()}가 아니라 {@code confirm()}(서버가 PortOne 재조회로 확정한 시점)에서
 * 호출된다 — 그래서 아래 테스트들은 create() 뒤에 반드시 confirm()을 호출한다(PortOneClient는
 * {@code @MockitoBean}으로 대체해 항상 요청 금액 그대로 PAID를 반환하도록 스텁한다). 마찬가지로
 * {@code TeamDeadlineService.processDeadline()}은 더 이상 즉시 환불을 반영하지 않고, 커밋 이후
 * 비동기로 PortOne 취소 API(이 테스트는 목으로 SUCCEEDED 고정)를 거쳐야 반영되므로, 환불이 걸린
 * 테스트는 클래스 레벨 {@code @Transactional}을 쓰지 않고(AFTER_COMMIT 리스너가 실제로 실행돼야 하므로)
 * waitUntil로 완료를 기다린 뒤 직접 정리한다(TeamDeadlineEventFlowTest와 같은 패턴).
 *
 * 동시 다발 결제 정합성(멀티스레드) 검증은 별도 클래스 SellerRevenueSummaryConcurrencyTest에서 한다.
 */
@SpringBootTest
class SellerRevenueSummaryTest {

    private static final long ASYNC_WAIT_TIMEOUT_MS = 5_000L;
    private static final long ASYNC_WAIT_INTERVAL_MS = 100L;

    @Autowired
    private SellerMypageService sellerMypageService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private TeamDeadlineService teamDeadlineService;

    @Autowired
    private SellerRevenueSummaryBackfillService sellerRevenueSummaryBackfillService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private GroupBuyTeamRepository groupBuyTeamRepository;

    @Autowired
    private TeamParticipationRepository teamParticipationRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private SellerRevenueSummaryRepository sellerRevenueSummaryRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @MockitoBean
    private PortOneClient portOneClient;

    private final List<Long> paymentIdsToClean = new ArrayList<>();
    private final List<Long> teamParticipationIdsToClean = new ArrayList<>();
    private final List<Long> teamIdsToClean = new ArrayList<>();
    private final List<Long> productIdsToClean = new ArrayList<>();
    private final List<Long> memberIdsToClean = new ArrayList<>();
    private final List<Long> summaryIdsToClean = new ArrayList<>();

    @BeforeEach
    void stubPortOne() {
        // 기본 동작: 조회한 pgPaymentId에 해당하는 결제를 그 결제의 실제 금액 그대로 PAID 승인한다.
        when(portOneClient.getPayment(anyString())).thenAnswer(invocation -> {
            String pgPaymentId = invocation.getArgument(0);
            Payment payment = paymentRepository.findByPgPaymentId(pgPaymentId).orElseThrow();
            return new PortOnePaymentDetail("PAID", payment.getAmount());
        });
        when(portOneClient.cancelPayment(anyString(), anyString()))
                .thenReturn(new PortOneCancelResult(PortOneCancelResult.SUCCEEDED));
    }

    @AfterEach
    void cleanUp() {
        for (Long memberId : memberIdsToClean) {
            notificationRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId)
                    .forEach(notification -> notificationRepository.deleteById(notification.getId()));
        }
        paymentIdsToClean.forEach(paymentRepository::deleteById);
        paymentIdsToClean.clear();
        teamParticipationIdsToClean.forEach(teamParticipationRepository::deleteById);
        teamParticipationIdsToClean.clear();
        teamIdsToClean.forEach(groupBuyTeamRepository::deleteById);
        teamIdsToClean.clear();
        summaryIdsToClean.forEach(sellerRevenueSummaryRepository::deleteById);
        summaryIdsToClean.clear();
        productIdsToClean.forEach(productRepository::deleteById);
        productIdsToClean.clear();
        memberIdsToClean.forEach(memberRepository::deleteById);
        memberIdsToClean.clear();
    }

    private Member saveMember(String username, Role role) {
        Member member = memberRepository.save(new Member(username, "pw", "테스트유저", username + "@test.com", role));
        memberIdsToClean.add(member.getId());
        return member;
    }

    private Product saveProduct(Member seller) {
        Product product = productRepository.save(new Product(seller, "요약테스트상품", "설명", 10000, 10, null));
        productIdsToClean.add(product.getId());
        return product;
    }

    private MemberUserDetails asPrincipal(Member member) {
        return new MemberUserDetails(member);
    }

    // 결제 요청 접수(create) 후 서버 확정(confirm)까지 한 번에 수행한다 — PortOne 연동 이후 결제
    // 완료는 이 두 단계를 거쳐야 한다(위 클래스 Javadoc 참고).
    private PaymentResponse createAndConfirm(Member buyer, Long productId, Long teamId) {
        PaymentResponse created = paymentService.create(asPrincipal(buyer), new PaymentCreateRequest(productId, teamId));
        paymentIdsToClean.add(created.paymentId());
        return paymentService.confirm(asPrincipal(buyer), created.paymentId());
    }

    private void waitUntil(BooleanSupplier condition, String failureMessage) {
        long deadline = System.currentTimeMillis() + ASYNC_WAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(ASYNC_WAIT_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("대기 중 인터럽트됨");
            }
        }
        fail(failureMessage);
    }

    @Test
    @DisplayName("결제가 하나도 없는 판매자는 요약 행 없이도 조회 시 정확히 0을 반환한다(순수 읽기, 부트스트랩 없음)")
    void revenue_returnsZeroWhenNoPaymentsEverHappened() {
        Member seller = saveMember("summarySeller0", Role.SELLER);
        saveProduct(seller);

        RevenueResponse response = sellerMypageService.revenue(asPrincipal(seller));

        assertEquals(0, response.totalRevenue());
        assertEquals(0L, response.paidCount());
        assertEquals(0L, response.refundedCount());
        assertTrue(sellerRevenueSummaryRepository.findBySellerId(seller.getId()).isEmpty(),
                "조회만으로는 요약 행이 생성되지 않아야 한다(지연 부트스트랩 제거 확인)");
    }

    @Test
    @DisplayName("결제가 확정되는 순간(첫 확정) seller_revenue_summary 행이 upsert로 즉시 만들어지고, 이후 확정은 정확히 증가한다")
    void paymentConfirm_incrementsSummaryExactly() {
        Member seller = saveMember("summarySeller1", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("summaryBuyer1", Role.BUYER);

        assertTrue(sellerRevenueSummaryRepository.findBySellerId(seller.getId()).isEmpty(),
                "첫 결제 전에는 요약 행이 없어야 한다");

        // 첫 결제 확정 — 요약 행이 없는 상태에서 incrementPaid(upsert)가 그 결제 값으로 행을 새로 만든다.
        createAndConfirm(buyer, product.getId(), null);
        // 두 번째 결제 확정 — 이제 있는 행을 원자적으로 증가시킨다.
        createAndConfirm(buyer, product.getId(), null);

        SellerRevenueSummary summary = sellerRevenueSummaryRepository.findBySellerId(seller.getId()).orElseThrow();
        summaryIdsToClean.add(summary.getId());
        assertEquals(product.getBasePrice() * 2, summary.getTotalRevenue());
        assertEquals(2L, summary.getPaidCount());
        assertEquals(0L, summary.getRefundedCount());
    }

    @Test
    @DisplayName("결제 생성만으로는(확정 전) seller_revenue_summary가 증가하지 않는다")
    void paymentCreate_withoutConfirm_doesNotIncrementSummary() {
        Member seller = saveMember("summarySeller1b", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("summaryBuyer1b", Role.BUYER);

        PaymentResponse created = paymentService.create(asPrincipal(buyer), new PaymentCreateRequest(product.getId(), null));
        paymentIdsToClean.add(created.paymentId());

        assertEquals("PENDING", created.status());
        assertTrue(sellerRevenueSummaryRepository.findBySellerId(seller.getId()).isEmpty(),
                "확정(confirm) 전에는 요약 행이 생기면 안 된다");
    }

    @Test
    @DisplayName("공구팀 마감으로 환불이 발생하면 seller_revenue_summary가 환불금액·건수만큼 정확히 감소한다")
    void teamDeadlineRefund_decrementsSummaryExactly() {
        Member seller = saveMember("summarySeller2", Role.SELLER);
        Product product = saveProduct(seller);
        Member leader = saveMember("summaryLeader2", Role.BUYER);
        Member joiner = saveMember("summaryJoiner2", Role.BUYER);

        GroupBuyTeam team = groupBuyTeamRepository.save(
                new GroupBuyTeam(product, leader, 10, LocalDateTime.now().minusMinutes(1)));
        teamIdsToClean.add(team.getId());
        teamParticipationIdsToClean.add(teamParticipationRepository.save(new TeamParticipation(team, leader)).getId());

        createAndConfirm(leader, product.getId(), team.getId());
        createAndConfirm(joiner, product.getId(), team.getId());

        // 이 팀과 무관한 결제(마감 처리와 별개로 유지돼야 함)
        Member otherBuyer = saveMember("summaryOther2", Role.BUYER);
        createAndConfirm(otherBuyer, product.getId(), null);

        SellerRevenueSummary before = sellerRevenueSummaryRepository.findBySellerId(seller.getId()).orElseThrow();
        summaryIdsToClean.add(before.getId());
        assertEquals(product.getBasePrice() * 3, before.getTotalRevenue());
        assertEquals(3L, before.getPaidCount());

        teamDeadlineService.processDeadline(team.getId());

        waitUntil(() -> sellerRevenueSummaryRepository.findBySellerId(seller.getId()).orElseThrow().getRefundedCount() == 2L,
                "5초 내에 환불이 요약에 반영되지 않았다(비동기 PortOne 취소 확인 지연 가능성)");

        SellerRevenueSummary after = sellerRevenueSummaryRepository.findBySellerId(seller.getId()).orElseThrow();
        assertEquals(product.getBasePrice(), after.getTotalRevenue(), "무관한 결제 1건만 남아야 한다");
        assertEquals(1L, after.getPaidCount());
        assertEquals(2L, after.getRefundedCount());
    }

    @Test
    @DisplayName("요약 행이 아직 없는(upsert 전환 이전부터 있던 과거 결제만 있는) 판매자는 1회성 백필로 정확한 값이 채워진다")
    void backfill_fillsSummaryFromExistingPaymentsWhenRowMissing() {
        Member seller = saveMember("summarySeller3", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("summaryBuyer3", Role.BUYER);

        // 이번 upsert 전환 이전부터 존재하던 과거 결제 데이터를 재현한다 — 요약 갱신 경로
        // (PaymentService.create/confirm → incrementPaid)를 거치지 않고 레포지토리에 직접 꽂아 넣는다.
        paymentIdsToClean.add(paymentRepository.save(new Payment(buyer, product, null, 20000)).getId());
        paymentIdsToClean.add(paymentRepository.save(new Payment(buyer, product, null, 30000)).getId());
        Payment refunded = paymentRepository.save(new Payment(buyer, product, null, 15000));
        refunded.refund();
        // 이 테스트 클래스는 (환불 확정 대기 테스트들 때문에) 클래스 레벨 @Transactional을 쓰지 않는다
        // — save() 각각이 독립된 트랜잭션이라 위 refund() 호출은 저장된 뒤 detach된 엔티티를 메모리에서만
        // 바꾼 것이라 명시적으로 다시 save()해야 DB에 반영된다(더티 체킹 자동 flush를 기대할 수 없음).
        paymentRepository.save(refunded);
        paymentIdsToClean.add(refunded.getId());

        assertTrue(sellerRevenueSummaryRepository.findBySellerId(seller.getId()).isEmpty(),
                "백필 전에는 요약 행이 없어야 한다");

        // 조회(revenue())는 더 이상 이 행을 만들지 않는다 — 그대로 0을 반환해야 한다.
        RevenueResponse beforeBackfill = sellerMypageService.revenue(asPrincipal(seller));
        assertEquals(0, beforeBackfill.totalRevenue(), "백필 전 조회는 순수 읽기이므로 0이어야 한다(부트스트랩 없음)");
        assertTrue(sellerRevenueSummaryRepository.findBySellerId(seller.getId()).isEmpty(),
                "조회만으로는 여전히 요약 행이 생성되지 않아야 한다");

        // 배포 시점에 한 번 실행되는 백필(SellerRevenueSummaryBackfillRunner가 호출하는 것과 동일한 서비스).
        boolean created = sellerRevenueSummaryBackfillService.backfillOneIfMissing(seller.getId());
        assertTrue(created, "백필이 실제로 새 행을 만들었어야 한다");
        sellerRevenueSummaryRepository.findBySellerId(seller.getId()).ifPresent(s -> summaryIdsToClean.add(s.getId()));

        RevenueResponse afterBackfill = sellerMypageService.revenue(asPrincipal(seller));
        assertEquals(50000, afterBackfill.totalRevenue());
        assertEquals(2L, afterBackfill.paidCount());
        assertEquals(1L, afterBackfill.refundedCount());

        // 같은 판매자에 대해 다시 백필을 돌려도(재실행 가능성) 이미 있는 행은 건드리지 않는다(멱등).
        boolean createdAgain = sellerRevenueSummaryBackfillService.backfillOneIfMissing(seller.getId());
        assertFalse(createdAgain, "이미 요약 행이 있으면 백필이 다시 만들지 않아야 한다");
    }

    @Test
    @DisplayName("대량 더미 결제 데이터에서 요약 테이블 값과 원본 payment 재계산 값이 정확히 일치한다 (드리프트 방지)")
    void summaryMatchesRecomputedAggregate_forBulkDummyData() {
        Member seller = saveMember("summarySeller4", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("summaryBuyer4", Role.BUYER);

        int paidCount = 40;
        int refundedCount = 7;
        for (int i = 1; i <= paidCount; i++) {
            createAndConfirm(buyer, product.getId(), null);
        }

        GroupBuyTeam team = groupBuyTeamRepository.save(
                new GroupBuyTeam(product, buyer, refundedCount + 1, LocalDateTime.now().minusMinutes(1)));
        teamIdsToClean.add(team.getId());
        teamParticipationIdsToClean.add(teamParticipationRepository.save(new TeamParticipation(team, buyer)).getId());
        for (int i = 1; i <= refundedCount; i++) {
            Member joiner = saveMember("summaryDrift4Joiner" + i, Role.BUYER);
            createAndConfirm(joiner, product.getId(), team.getId());
        }
        teamDeadlineService.processDeadline(team.getId());

        waitUntil(() -> sellerRevenueSummaryRepository.findBySellerId(seller.getId()).orElseThrow().getRefundedCount()
                        == (long) refundedCount,
                "5초 내에 대량 환불이 요약에 전부 반영되지 않았다");

        SellerRevenueSummary summary = sellerRevenueSummaryRepository.findBySellerId(seller.getId()).orElseThrow();
        summaryIdsToClean.add(summary.getId());
        RevenueSummaryProjection recomputed = paymentRepository.findRevenueSummaryBySellerId(seller.getId());

        assertEquals(recomputed.getTotalRevenue(), summary.getTotalRevenue());
        assertEquals(recomputed.getPaidCount(), summary.getPaidCount());
        assertEquals(recomputed.getRefundedCount(), summary.getRefundedCount());
        assertEquals((long) paidCount, summary.getPaidCount());
        assertEquals((long) refundedCount, summary.getRefundedCount());
    }
}
