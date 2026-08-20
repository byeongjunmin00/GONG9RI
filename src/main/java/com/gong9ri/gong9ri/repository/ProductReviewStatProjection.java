package com.gong9ri.gong9ri.repository;

public record ProductReviewStatProjection(
        Long productId,
        Double averageRating,
        Long reviewCount
) {}
