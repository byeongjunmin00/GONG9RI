package com.gong9ri.gong9ri.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.PaymentCreateRequest;
import com.gong9ri.gong9ri.dto.RevenueResponse;
import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.entity.TeamParticipation;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.TeamParticipationRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * mypage/seller-revenue 캐싱 동작 검증 (docs/dev/mypage/view/changes/002-caching.md).
 * 테스트는 src/test/resources/application.yaml에서 spring.cache.type=simple(ConcurrentMapCacheManager)로
 * 오버라이드해, 실제 Redis 연결 없이도 @Cacheable/@CacheEvict/CacheManager 무효화 동작 자체를 검증한다.
 *
 * 각 테스트는 (1) 의미 있는 규모의 더미 결제 데이터로 집계값 자체를 검증하고,
 * (2) 캐시 이용 전/후 응답값을 명시적으로 비교해 "캐시가 실제로 관여했는지"를 증명한다
 * (단순히 "레포지토리가 한 번만 불렸다"에 그치지 않고, 캐시 히트 케이스는 레포지토리를 우회해 데이터를 바꾼 뒤에도
 * 재조회 값이 그 변경을 반영하지 않고 이전 값 그대로임을 확인해 "진짜로 캐시된 값"임을 증명한다).
 */
@SpringBootTest
@Transactional
class SellerRevenueCachingTest {

    private static final int DUMMY_PAID_COUNT = 25;
    private static final int DUMMY_REFUNDED_COUNT = 5;
    private static final int DUMMY_AMOUNT_STEP = 1000;

    @Autowired
    private SellerMypageService sellerMypageService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private TeamDeadlineService teamDeadlineService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private GroupBuyTeamRepository groupBuyTeamRepository;

    @Autowired
    private TeamParticipationRepository teamParticipationRepository;

    @MockitoSpyBean
    private PaymentRepository paymentRepository;

    private Member saveMember(String username, Role role) {
        return memberRepository.save(new Member(username, "pw", "테스트유저", username + "@test.com", role));
    }

    private Product saveProduct(Member seller) {
        return productRepository.save(new Product(seller, "캐싱테스트상품", "설명", 10000, 10));
    }

    private MemberUserDetails asPrincipal(Member member) {
        return new MemberUserDetails(member);
    }

    /**
     * 판매자 상품에 결제 건을 대량으로 채워 넣는다(집계값이 우연히 맞아떨어지지 않도록 규모 있는 더미 데이터 사용).
     * PAID paidCount건은 1000원 단위로 증가하는 금액을 써서 합계를 손으로도 검증 가능하게 한다.
     * REFUNDED refundedCount건은 금액과 무관하게 totalRevenue에서 제외되는지 확인하는 용도.
     *
     * @return PAID 결제 금액 합계 (기대 totalRevenue)
     */
    private int seedDummyPayments(Member buyer, Product product, int paidCount, int refundedCount) {
        int total = 0;
        for (int i = 1; i <= paidCount; i++) {
            int amount = i * DUMMY_AMOUNT_STEP;
            paymentRepository.save(new Payment(buyer, product, null, amount));
            total += amount;
        }
        for (int i = 1; i <= refundedCount; i++) {
            Payment refunded = paymentRepository.save(new Payment(buyer, product, null, i * DUMMY_AMOUNT_STEP));
            refunded.refund();
        }
        return total;
    }

    @Test
    @DisplayName("대량 결제 데이터에서 집계값을 검증하고, 그 이후 레포지토리를 우회해 데이터가 바뀌어도 캐시된 이전 값을 그대로 반환한다 (진짜 캐시 히트)")
    void revenue_secondCall_returnsStaleCachedValue_evenAfterUnderlyingDataChangesOutsideOfInvalidation() {
        Member seller = saveMember("cacheSeller1", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("cacheBuyer1", Role.BUYER);
        int expectedTotal = seedDummyPayments(buyer, product, DUMMY_PAID_COUNT, DUMMY_REFUNDED_COUNT);

        RevenueResponse first = sellerMypageService.revenue(asPrincipal(seller));
        assertEquals(expectedTotal, first.totalRevenue());
        assertEquals((long) DUMMY_PAID_COUNT, first.paidCount());
        assertEquals((long) DUMMY_REFUNDED_COUNT, first.refundedCount());

        // 캐시 무효화 경로(PaymentService.create/TeamDeadlineService.processDeadline)를 거치지 않고
        // 레포지토리에 직접 결제를 꽂아 넣는다 — 캐시가 진짜로 이전 값을 들고 있는지 증명하기 위한 대조군.
        paymentRepository.save(new Payment(buyer, product, null, 999_000));

        RevenueResponse second = sellerMypageService.revenue(asPrincipal(seller));

        // 캐시가 아니라 매번 새로 쿼리했다면 999_000이 반영돼 first와 달라야 한다.
        // 캐시 히트라면 데이터가 실제로 바뀌었음에도 이전 값 그대로 나온다.
        assertEquals(first, second);
        assertNotEquals(expectedTotal + 999_000, second.totalRevenue());
        verify(paymentRepository, times(1)).findRevenueSummaryBySellerId(seller.getId());
    }

    @Test
    @DisplayName("기존 대량 결제 위에 결제가 새로 생성되면 캐시가 무효화되어, 재조회 시 이전 값과 다른 최신 합계가 정확히 반영된다")
    void revenue_afterPaymentCreate_cacheEvictedAndReflectsLatest() {
        Member seller = saveMember("cacheSeller2", Role.SELLER);
        Product product = saveProduct(seller);
        Member existingBuyer = saveMember("cacheBuyerExisting2", Role.BUYER);
        Member newBuyer = saveMember("cacheBuyerNew2", Role.BUYER);
        int existingTotal = seedDummyPayments(existingBuyer, product, DUMMY_PAID_COUNT, DUMMY_REFUNDED_COUNT);

        RevenueResponse before = sellerMypageService.revenue(asPrincipal(seller));
        assertEquals(existingTotal, before.totalRevenue());
        assertEquals((long) DUMMY_PAID_COUNT, before.paidCount());

        paymentService.create(asPrincipal(newBuyer), new PaymentCreateRequest(product.getId(), null));

        RevenueResponse after = sellerMypageService.revenue(asPrincipal(seller));
        assertNotEquals(before, after);
        assertEquals(existingTotal + product.getBasePrice(), after.totalRevenue());
        assertEquals((long) DUMMY_PAID_COUNT + 1, after.paidCount());

        verify(paymentRepository, times(2)).findRevenueSummaryBySellerId(seller.getId());
    }

    @Test
    @DisplayName("대량 결제 중 특정 팀분만 마감 환불되면, 그 팀의 결제만 정확히 차감되고 캐시가 무효화되어 재조회 시 반영된다")
    void revenue_afterDeadlineRefund_cacheEvictedAndReflectsLatest() {
        Member seller = saveMember("cacheSeller3", Role.SELLER);
        Product product = saveProduct(seller);
        Member otherBuyer = saveMember("cacheBuyerOther3", Role.BUYER);
        Member leader = saveMember("cacheLeader3", Role.BUYER);

        // 이 팀과 무관한 기존 결제(환불 대상 아님) — 팀 단위로만 정확히 차감되는지 구분하기 위한 베이스라인.
        int unrelatedTotal = seedDummyPayments(otherBuyer, product, DUMMY_PAID_COUNT, DUMMY_REFUNDED_COUNT);

        GroupBuyTeam team = groupBuyTeamRepository.save(
                new GroupBuyTeam(product, leader, 10, LocalDateTime.now().minusMinutes(1)));
        teamParticipationRepository.save(new TeamParticipation(team, leader));
        int teamPaymentCount = 4;
        int teamTotal = 0;
        for (int i = 1; i <= teamPaymentCount; i++) {
            int amount = i * 10_000;
            paymentRepository.save(new Payment(leader, product, team, amount));
            teamTotal += amount;
        }

        RevenueResponse before = sellerMypageService.revenue(asPrincipal(seller));
        assertEquals(unrelatedTotal + teamTotal, before.totalRevenue());
        assertEquals((long) DUMMY_PAID_COUNT + teamPaymentCount, before.paidCount());
        assertEquals((long) DUMMY_REFUNDED_COUNT, before.refundedCount());

        teamDeadlineService.processDeadline(team.getId());

        RevenueResponse after = sellerMypageService.revenue(asPrincipal(seller));
        assertNotEquals(before, after);
        assertEquals(unrelatedTotal, after.totalRevenue());
        assertEquals((long) DUMMY_PAID_COUNT, after.paidCount());
        assertEquals((long) DUMMY_REFUNDED_COUNT + teamPaymentCount, after.refundedCount());

        verify(paymentRepository, times(2)).findRevenueSummaryBySellerId(seller.getId());
    }

    @Test
    @DisplayName("환불이 발생하지 않은 마감 처리는 캐시를 무효화하지 않아, 재조회해도 이전 값과 완전히 동일하다")
    void revenue_deadlineWithoutPayments_doesNotEvictCache() {
        Member seller = saveMember("cacheSeller4", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("cacheBuyer4", Role.BUYER);
        Member leader = saveMember("cacheLeader4", Role.BUYER);
        int expectedTotal = seedDummyPayments(buyer, product, DUMMY_PAID_COUNT, DUMMY_REFUNDED_COUNT);

        GroupBuyTeam team = groupBuyTeamRepository.save(
                new GroupBuyTeam(product, leader, 10, LocalDateTime.now().minusMinutes(1)));
        teamParticipationRepository.save(new TeamParticipation(team, leader));
        // 이 팀엔 결제가 없다 — processDeadline이 FAILED 전환은 해도 환불 건이 없어 무효화가 일어나지 않아야 한다.

        RevenueResponse before = sellerMypageService.revenue(asPrincipal(seller));
        assertEquals(expectedTotal, before.totalRevenue());

        teamDeadlineService.processDeadline(team.getId());

        RevenueResponse after = sellerMypageService.revenue(asPrincipal(seller));
        assertEquals(before, after);

        verify(paymentRepository, times(1)).findRevenueSummaryBySellerId(seller.getId());
    }
}
