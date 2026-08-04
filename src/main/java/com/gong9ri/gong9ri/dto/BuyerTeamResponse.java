package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.TeamParticipation;
import com.gong9ri.gong9ri.entity.TeamStatus;
import java.time.LocalDateTime;

public record BuyerTeamResponse(
        Long teamId,
        Long productId,
        String productName,
        Integer currentCount,
        Integer maxParticipants,
        TeamStatus status,
        LocalDateTime deadline,
        LocalDateTime joinedAt
) {
    public static BuyerTeamResponse from(TeamParticipation participation) {
        GroupBuyTeam team = participation.getTeam();
        return new BuyerTeamResponse(
                team.getId(),
                team.getProduct().getId(),
                team.getProduct().getName(),
                team.getCurrentCount(),
                team.getMaxParticipants(),
                team.getStatus(),
                team.getDeadline(),
                participation.getJoinedAt()
        );
    }
}
