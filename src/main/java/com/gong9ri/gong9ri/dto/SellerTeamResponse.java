package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.TeamStatus;
import java.time.LocalDateTime;

public record SellerTeamResponse(
        Long teamId,
        Long productId,
        String productName,
        Integer currentCount,
        Integer maxParticipants,
        TeamStatus status,
        LocalDateTime deadline,
        LocalDateTime createdAt
) {
    public static SellerTeamResponse from(GroupBuyTeam team) {
        return new SellerTeamResponse(
                team.getId(),
                team.getProduct().getId(),
                team.getProduct().getName(),
                team.getCurrentCount(),
                team.getMaxParticipants(),
                team.getStatus(),
                team.getDeadline(),
                team.getCreatedAt()
        );
    }
}
