package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.SupportMessage;
import java.time.LocalDateTime;

public record SupportMessageResponse(
        Long messageId,
        Long roomId,
        Long senderId,
        String senderName,
        boolean sentByAdmin,
        String content,
        LocalDateTime createdAt,
        // 보낸 사람 프로필 사진(member/profile-image 노출, 2026-08-21). senderName과 같은 회원 엔티티에서
        // 읽으므로 추가 조회가 없다. 없으면 null → 프론트가 이름 첫 글자 동그라미를 그린다.
        String senderProfileImageUrl
) {
    public static SupportMessageResponse from(SupportMessage message) {
        return new SupportMessageResponse(
                message.getId(),
                message.getRoom().getId(),
                message.getSender().getId(),
                message.getSender().getName(),
                message.isSentByAdmin(),
                message.getContent(),
                message.getCreatedAt(),
                message.getSender().getProfileImageUrl());
    }
}
