package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.Inquiry;
import java.time.LocalDateTime;

public record InquiryResponse(
        Long inquiryId,
        Long productId,
        Long memberId,
        String memberName,
        String content,
        boolean answered,
        String answerContent,
        LocalDateTime answeredAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        // 작성자 프로필 사진(member/profile-image 노출, 2026-08-21). memberName과 같은 회원
        // 엔티티에서 읽으므로 추가 조회가 생기지 않는다. 없으면 null → 첫 글자 동그라미.
        String memberProfileImageUrl
) {
    public static InquiryResponse from(Inquiry inquiry) {
        return new InquiryResponse(
                inquiry.getId(),
                inquiry.getProduct().getId(),
                inquiry.getMember().getId(),
                inquiry.getMember().getName(),
                inquiry.getContent(),
                inquiry.isAnswered(),
                inquiry.getAnswerContent(),
                inquiry.getAnsweredAt(),
                inquiry.getCreatedAt(),
                inquiry.getUpdatedAt(),
                inquiry.getMember().getProfileImageUrl()
        );
    }
}
