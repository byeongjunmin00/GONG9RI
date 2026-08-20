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
        LocalDateTime createdAt
) {
    public static SupportMessageResponse from(SupportMessage message) {
        return new SupportMessageResponse(
                message.getId(),
                message.getRoom().getId(),
                message.getSender().getId(),
                message.getSender().getName(),
                message.isSentByAdmin(),
                message.getContent(),
                message.getCreatedAt());
    }
}
