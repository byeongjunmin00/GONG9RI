package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.Review;
import java.time.LocalDateTime;

public record ReviewResponse(
        Long reviewId,
        Long memberId,
        String memberName,
        Integer rating,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getMember().getId(),
                review.getMember().getName(),
                review.getRating(),
                review.getContent(),
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }
}
