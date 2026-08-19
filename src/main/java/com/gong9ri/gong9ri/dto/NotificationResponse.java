package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.Notification;
import com.gong9ri.gong9ri.entity.NotificationType;
import java.time.LocalDateTime;

public record NotificationResponse(
        Long notificationId,
        NotificationType type,
        String message,
        Long relatedTeamId,
        String linkUrl,
        Boolean isRead,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getMessage(),
                notification.getRelatedTeam() != null ? notification.getRelatedTeam().getId() : null,
                notification.getLinkUrl(),
                notification.getIsRead(),
                notification.getCreatedAt()
        );
    }
}
