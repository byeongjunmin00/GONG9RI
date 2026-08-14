package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.Review;
import java.util.List;

public record ReviewListResponse(
        Double averageRating,
        int count,
        List<ReviewResponse> reviews
) {
    public static ReviewListResponse of(List<Review> reviews) {
        int count = reviews.size();
        Double average = count == 0
                ? null
                : reviews.stream().mapToInt(Review::getRating).average().orElseThrow();
        return new ReviewListResponse(average, count, reviews.stream().map(ReviewResponse::from).toList());
    }
}
