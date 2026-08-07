package com.gong9ri.gong9ri.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.PaymentCreateRequest;
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
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.RevenueSummaryProjection;
import com.gong9ri.gong9ri.repository.SellerRevenueSummaryRepository;
import com.gong9ri.gong9ri.repository.TeamParticipationRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * mypage/seller-revenue 컬럼 집계 방식(docs/db/seller_revenue_summary.md, 2026-08-06 upsert 전환) 검증.
 * group_buy_team.current_count와 같은 방식(결제/환불 트랜잭션 안에서 seller_revenue_summary를 즉시
 * 갱신)이지만, 요약 행 생성 시점을 "조회 시(지연 부트스트랩)"에서 "결제 시(upsert)"로 정정했다
 * (docs/dev/mypage/view/changes/004-upsert-fix.md) — revenue()는 이제 순수 읽기라 조회만으로는
 * 어떤 행도 만들지 않는다.
 *
 * 동시 다발 결제 정합성(멀티스레드) 검증은 별도 클래스 SellerRevenueSummaryConcurrencyTest에서 한다
 * (TeamConcurrencyTest와 동일한 이유로 이 클래스는 @Transactional을 쓰지만, 그 클래스는 안 쓴다).
 */
@SpringBootTest
@Transactional
class SellerRevenueSummaryTest {

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

    private Member saveMember(String username, Role role) {
        return memberRepository.save(new Member(username, "pw", "테스트유저", username + "@test.com", role));
    }

    private Product saveProduct(Member seller) {
        return productRepository.save(new Product(seller, "요약테스트상품", "설명", 10000, 10));
    }

    private MemberUserDetails asPrincipal(Member member) {
        return new MemberUserDetails(member);
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
    @DisplayName("결제가 생성되는 순간(첫 결제) seller_revenue_summary 행이 upsert로 즉시 만들어지고, 이후 결제는 정확히 증가한다")
    void paymentCreate_incrementsSummaryExactly() {
        Member seller = saveMember("summarySeller1", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("summaryBuyer1", Role.BUYER);

        assertTrue(sellerRevenueSummaryRepository.findBySellerId(seller.getId()).isEmpty(),
                "첫 결제 전에는 요약 행이 없어야 한다");

        // 첫 결제 — 요약 행이 없는 상태에서 incrementPaid(upsert)가 그 결제 값으로 행을 새로 만든다.
        paymentService.create(asPrincipal(buyer), new PaymentCreateRequest(product.getId(), null));
        // 두 번째 결제 — 이제 있는 행을 원자적으로 증가시킨다.
        paymentService.create(asPrincipal(buyer), new PaymentCreateRequest(product.getId(), null));

        SellerRevenueSummary summary = sellerRevenueSummaryRepository.findBySellerId(seller.getId()).orElseThrow();
        assertEquals(product.getBasePrice() * 2, summary.getTotalRevenue());
        assertEquals(2L, summary.getPaidCount());
        assertEquals(0L, summary.getRefundedCount());
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
        teamParticipationRepository.save(new TeamParticipation(team, leader));

        paymentService.create(asPrincipal(leader), new PaymentCreateRequest(product.getId(), team.getId()));
        paymentService.create(asPrincipal(joiner), new PaymentCreateRequest(product.getId(), team.getId()));

        // 이 팀과 무관한 결제(마감 처리와 별개로 유지돼야 함)
        Member otherBuyer = saveMember("summaryOther2", Role.BUYER);
        paymentService.create(asPrincipal(otherBuyer), new PaymentCreateRequest(product.getId(), null));

        SellerRevenueSummary before = sellerRevenueSummaryRepository.findBySellerId(seller.getId()).orElseThrow();
        assertEquals(product.getBasePrice() * 3, before.getTotalRevenue());
        assertEquals(3L, before.getPaidCount());

        teamDeadlineService.processDeadline(team.getId());

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
        // (PaymentService.create → incrementPaid)를 거치지 않고 레포지토리에 직접 꽂아 넣는다.
        paymentRepository.save(new Payment(buyer, product, null, 20000));
        paymentRepository.save(new Payment(buyer, product, null, 30000));
        Payment refunded = paymentRepository.save(new Payment(buyer, product, null, 15000));
        refunded.refund();

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
            paymentService.create(asPrincipal(buyer), new PaymentCreateRequest(product.getId(), null));
        }

        GroupBuyTeam team = groupBuyTeamRepository.save(
                new GroupBuyTeam(product, buyer, refundedCount + 1, LocalDateTime.now().minusMinutes(1)));
        teamParticipationRepository.save(new TeamParticipation(team, buyer));
        for (int i = 1; i <= refundedCount; i++) {
            Member joiner = saveMember("summaryDrift4Joiner" + i, Role.BUYER);
            paymentService.create(asPrincipal(joiner), new PaymentCreateRequest(product.getId(), team.getId()));
        }
        teamDeadlineService.processDeadline(team.getId());

        SellerRevenueSummary summary = sellerRevenueSummaryRepository.findBySellerId(seller.getId()).orElseThrow();
        RevenueSummaryProjection recomputed = paymentRepository.findRevenueSummaryBySellerId(seller.getId());

        assertEquals(recomputed.getTotalRevenue(), summary.getTotalRevenue());
        assertEquals(recomputed.getPaidCount(), summary.getPaidCount());
        assertEquals(recomputed.getRefundedCount(), summary.getRefundedCount());
        assertEquals((long) paidCount, summary.getPaidCount());
        assertEquals((long) refundedCount, summary.getRefundedCount());
    }
}
