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
        LocalDateTime updatedAt
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
                inquiry.getUpdatedAt()
        );
    }
}
