package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.TeamStatus;
import java.time.LocalDateTime;

public record TeamResponse(
        Long teamId,
        Long productId,
        Long leaderId,
        Integer currentCount,
        Integer maxParticipants,
        TeamStatus status,
        LocalDateTime deadline,
        LocalDateTime createdAt
) {
    public static TeamResponse from(GroupBuyTeam team) {
        return new TeamResponse(
                team.getId(),
                team.getProduct().getId(),
                team.getLeader().getId(),
                team.getCurrentCount(),
                team.getMaxParticipants(),
                team.getStatus(),
                team.getDeadline(),
                team.getCreatedAt()
        );
    }
}
