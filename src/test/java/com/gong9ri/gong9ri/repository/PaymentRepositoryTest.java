package com.gong9ri.gong9ri.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.Role;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

/**
 * PaymentRepository의 QueryDSL 전환 쿼리(docs/dev/ongoing/querydsl-migration.md) 중
 * 서비스 레벨 테스트가 커버하지 않는 findDistinctSellerIdsWithPayments()를 직접 검증한다.
 * (SellerRevenueSummaryBackfillService.backfillMissingSummaries()의 스캔 후보 조회 쿼리 —
 * 그 서비스 메서드 자체를 호출하는 테스트가 없어 이 쿼리만 슬라이스 테스트로 보강한다.)
 * 나머지 커스텀 쿼리(findByIdWithDetails, findAllByMemberIdWithProduct, findRevenueSummaryBySellerId)는
 * PaymentControllerTest / BuyerMypageControllerTest / SellerRevenueSummaryTest에서 이미 검증된다.
 *
 * 이 프로젝트에는 임베디드 DB(H2 등)가 없어 @DataJpaTest가 기본으로 시도하는 DB 교체를 끄고
 * (Replace.NONE) 실제 로컬 MySQL(application.yaml 설정)을 그대로 사용한다 — 다른 테스트들과 동일.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PaymentRepositoryTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Member saveMember(String username, Role role) {
        return memberRepository.save(new Member(username, "pw", "테스트유저", username + "@test.com", role));
    }

    @Test
    @DisplayName("결제 이력이 있는 판매자 id를 중복 없이 조회한다 (DISTINCT 확인)")
    void findDistinctSellerIdsWithPayments_returnsDistinctSellerIds() {
        Member seller = saveMember("repoTestSeller1", Role.SELLER);
        Member buyer = saveMember("repoTestBuyer1", Role.BUYER);
        Product product = productRepository.save(new Product(seller, "레포지토리테스트상품", "설명", 10000, 10, null));

        paymentRepository.save(new Payment(buyer, product, null, 10000));
        paymentRepository.save(new Payment(buyer, product, null, 10000));

        List<Long> sellerIds = paymentRepository.findDistinctSellerIdsWithPayments();

        assertTrue(sellerIds.contains(seller.getId()), "결제가 있는 판매자 id가 포함돼야 한다");
        long occurrences = sellerIds.stream().filter(seller.getId()::equals).count();
        assertEquals(1L, occurrences, "같은 판매자 id가 결제 건수만큼 중복되면 안 된다");
    }

    @Test
    @DisplayName("결제 이력이 없는 판매자는 조회 결과에 포함되지 않는다")
    void findDistinctSellerIdsWithPayments_excludesSellersWithoutPayments() {
        Member sellerWithoutPayments = saveMember("repoTestSeller2", Role.SELLER);
        productRepository.save(new Product(sellerWithoutPayments, "결제없는상품", "설명", 10000, 10, null));

        List<Long> sellerIds = paymentRepository.findDistinctSellerIdsWithPayments();

        assertTrue(!sellerIds.contains(sellerWithoutPayments.getId()),
                "결제가 하나도 없는 판매자는 후보 목록에 없어야 한다");
    }
}
