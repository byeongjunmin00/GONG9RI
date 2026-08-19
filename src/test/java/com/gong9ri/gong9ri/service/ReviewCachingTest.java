package com.gong9ri.gong9ri.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.ReviewCreateRequest;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리뷰 작성이 상품 캐시를 무효화하는지 검증한다 — 판매자 신뢰 배지(product/seller-trust)는 리뷰 평균
 * 평점·개수로 판정되는데 상품 목록·상세 응답에 담겨 30분간 캐싱되기 때문에, 리뷰 쪽에서 캐시를 안 날리면
 * 배지 조건을 만족시켜도 최대 30분간 배지가 안 뜬다(2026-08-20 실제로 발견된 버그).
 *
 * ProductCachingTest와 동일하게 spring.cache.type=simple(ConcurrentMapCacheManager)에서 돌아간다.
 */
@SpringBootTest
@Transactional
class ReviewCachingTest {

    // ProductService.TRUSTED_SELLER_MIN_REVIEW_COUNT(3) / MIN_RATING(4.5)을 만족시키기 위한 값.
    private static final int REQUIRED_REVIEW_COUNT = 3;
    private static final int PASSING_RATING = 5;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private ProductService productService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    private Member saveMember(String username, Role role) {
        return memberRepository.save(new Member(username, "pw", "테스트유저", username + "@test.com", role));
    }

    /** 리뷰 작성 자격(해당 상품 PAID 결제 이력)을 갖춘 구매자를 만든다. */
    private Member saveBuyerWithPaidPayment(String username, Product product) {
        Member buyer = saveMember(username, Role.BUYER);
        paymentRepository.save(new Payment(buyer, product, null, product.getBasePrice()));
        return buyer;
    }

    @Test
    @DisplayName("리뷰를 작성해 신뢰 배지 조건을 채우면 상품 상세 캐시가 무효화되어 배지가 즉시 반영된다")
    void createReviewEvictsProductDetailCache() {
        Member seller = saveMember("cache_trust_seller", Role.SELLER);
        Product product = productRepository.save(
                new Product(seller, "신뢰배지 캐시 검증 상품", "설명", 10000, 10, null));
        Long productId = product.getId();

        // 리뷰가 아직 없는 상태의 상세를 한 번 조회해 "배지 false"를 캐시에 올려둔다.
        assertFalse(productService.detail(productId).sellerTrustedBadge(),
                "리뷰가 없으면 신뢰 배지가 붙지 않아야 한다");

        // 서로 다른 구매자 3명이 5점 리뷰를 남겨 배지 조건(평균 4.5 이상 + 3개 이상)을 채운다.
        for (int i = 1; i <= REQUIRED_REVIEW_COUNT; i++) {
            Member buyer = saveBuyerWithPaidPayment("cache_trust_buyer" + i, product);
            reviewService.create(new MemberUserDetails(buyer), productId,
                    new ReviewCreateRequest(PASSING_RATING, "좋아요" + i));
        }

        // 캐시가 무효화되지 않으면 위에서 캐싱된 false가 그대로 돌아온다 — 이 단언이 버그를 잡는다.
        assertTrue(productService.detail(productId).sellerTrustedBadge(),
                "리뷰로 조건을 채웠으면 캐시된 이전 값이 아니라 갱신된 배지(true)가 나와야 한다");
    }
}
