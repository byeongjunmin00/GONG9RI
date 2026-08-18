package com.gong9ri.gong9ri.repository;

/**
 * {@link SellerRatingProjection}의 QueryDSL {@code Projections.constructor} 대상 구현체.
 * QueryDSL은 인터페이스 프로젝션(bean binding)을 직접 지원하지 않아, 생성자 프로젝션이 바인딩할
 * 구체 클래스가 필요하다 — {@link ReviewRepositoryImpl} 참고.
 */
public class SellerRatingProjectionImpl implements SellerRatingProjection {

    private final Long sellerId;
    private final Double averageRating;
    private final Long reviewCount;

    public SellerRatingProjectionImpl(Long sellerId, Double averageRating, Long reviewCount) {
        this.sellerId = sellerId;
        this.averageRating = averageRating;
        this.reviewCount = reviewCount;
    }

    @Override
    public Long getSellerId() {
        return sellerId;
    }

    @Override
    public Double getAverageRating() {
        return averageRating;
    }

    @Override
    public Long getReviewCount() {
        return reviewCount;
    }
}
