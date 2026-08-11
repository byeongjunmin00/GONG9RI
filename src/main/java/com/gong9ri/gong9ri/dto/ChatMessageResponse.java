package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.ChatMessage;
import com.gong9ri.gong9ri.entity.ChatRole;
import java.time.LocalDateTime;

public record ChatMessageResponse(
        Long messageId,
        ChatRole role,
        String content,
        LocalDateTime createdAt
) {
    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
