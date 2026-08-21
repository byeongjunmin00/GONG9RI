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
        LocalDateTime updatedAt,
        // 작성자 프로필 사진(member/profile-image 노출, 2026-08-21). memberName과 같은 회원
        // 엔티티에서 읽으므로 추가 조회가 생기지 않는다. 없으면 null → 첫 글자 동그라미.
        String memberProfileImageUrl
) {
    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getMember().getId(),
                review.getMember().getName(),
                review.getRating(),
                review.getContent(),
                review.getCreatedAt(),
                review.getUpdatedAt(),
                review.getMember().getProfileImageUrl()
        );
    }
}
