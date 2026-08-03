package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.TeamStatus;

public record TeamJoinResponse(
        Long teamId,
        Integer currentCount,
        Integer maxParticipants,
        TeamStatus status
) {
    public static TeamJoinResponse from(GroupBuyTeam team) {
        return new TeamJoinResponse(team.getId(), team.getCurrentCount(), team.getMaxParticipants(), team.getStatus());
    }
}
