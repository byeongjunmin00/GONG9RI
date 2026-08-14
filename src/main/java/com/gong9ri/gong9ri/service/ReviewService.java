package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;

    public ReviewListResponse list(Long productId) {
        return ReviewListResponse.of(reviewRepository.findByProductIdOrderByCreatedAtDesc(productId));
    }

    @Transactional
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
        return ReviewResponse.from(saved);
    }

    @Transactional
    public ReviewResponse update(MemberUserDetails principal, Long reviewId, ReviewCreateRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
        requireOwner(principal, review);

        review.update(request.rating(), request.content());
        log.info("리뷰 수정 완료: reviewId={}, memberId={}", reviewId, principal.getMember().getId());
        return ReviewResponse.from(review);
    }

    @Transactional
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
