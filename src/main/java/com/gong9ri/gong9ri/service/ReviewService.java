package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.config.CacheConfig;
import com.gong9ri.gong9ri.dto.ReviewCreateRequest;
import com.gong9ri.gong9ri.dto.ReviewListResponse;
import com.gong9ri.gong9ri.dto.ReviewResponse;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.PaymentStatus;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.Review;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리뷰 작성·수정·삭제는 상품 응답의 {@code sellerTrustedBadge}(판매자 신뢰 배지, product/seller-trust)를
 * 바꾸기 때문에 상품 목록·상세 캐시(TTL 30분)를 함께 무효화해야 한다. 이게 없어서 리뷰로 배지 조건을
 * 만족시켜도 최대 30분간 배지가 안 뜨는 버그가 있었다(2026-08-20 발견).
 *
 * 상세 캐시를 {@code key = "#productId"}가 아니라 {@code allEntries = true}로 날리는 이유 — 배지는 그
 * 판매자의 <b>전체 상품</b> 리뷰를 합산해서 판정하므로, 상품 A에 리뷰가 하나 달리면 같은 판매자의 상품
 * B·C의 배지까지 같이 바뀐다. 리뷰가 달린 상품 하나만 날리면 나머지가 낡은 값으로 남는다.
 * (리뷰 작성은 상품 조회에 비해 훨씬 드문 작업이라 전체 무효화 비용은 문제되지 않는다.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final NotificationPublisher notificationPublisher;

    public ReviewListResponse list(Long productId) {
        return ReviewListResponse.of(reviewRepository.findByProductIdOrderByCreatedAtDesc(productId));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.PRODUCT_DETAIL_CACHE, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.PRODUCT_LIST_CACHE, allEntries = true)
    })
    public ReviewResponse create(MemberUserDetails principal, Long productId, ReviewCreateRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        Member member = principal.getMember();

        // 이 상품을 실제로 결제 완료(PAID)한 적이 있어야 리뷰를 쓸 수 있다 — 구매 안 한 사람의
        // 무분별한 리뷰(경쟁사 비방, 스팸 등)를 막는 최소한의 장치. 이후 그 결제가 취소·환불되더라도
        // 이미 작성된 리뷰까지 소급해서 막지는 않는다(작성 시점 자격만 확인).
        if (!paymentRepository.existsByMemberIdAndProductIdAndStatus(member.getId(), productId, PaymentStatus.PAID)) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_ELIGIBLE);
        }
        if (reviewRepository.existsByProductIdAndMemberId(productId, member.getId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_REVIEW);
        }

        Review saved = reviewRepository.save(new Review(product, member, request.rating(), request.content()));
        log.info("리뷰 작성 완료: reviewId={}, productId={}, memberId={}", saved.getId(), productId, member.getId());
        notificationPublisher.reviewCreated(product.getSeller().getId(), member.getId(),
                productId, product.getName(), request.rating());
        return ReviewResponse.from(saved);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.PRODUCT_DETAIL_CACHE, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.PRODUCT_LIST_CACHE, allEntries = true)
    })
    public ReviewResponse update(MemberUserDetails principal, Long reviewId, ReviewCreateRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
        requireOwner(principal, review);

        review.update(request.rating(), request.content());
        log.info("리뷰 수정 완료: reviewId={}, memberId={}", reviewId, principal.getMember().getId());
        return ReviewResponse.from(review);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.PRODUCT_DETAIL_CACHE, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.PRODUCT_LIST_CACHE, allEntries = true)
    })
    public void delete(MemberUserDetails principal, Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
        requireOwner(principal, review);

        reviewRepository.delete(review);
        log.info("리뷰 삭제 완료: reviewId={}, memberId={}", reviewId, principal.getMember().getId());
    }

    private void requireOwner(MemberUserDetails principal, Review review) {
        if (!review.getMember().getId().equals(principal.getMember().getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
