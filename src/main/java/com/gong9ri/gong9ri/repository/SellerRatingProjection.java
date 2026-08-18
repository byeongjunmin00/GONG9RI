package com.gong9ri.gong9ri.repository;

public interface SellerRatingProjection {

    Long getSellerId();

    Double getAverageRating();

    Long getReviewCount();
}
