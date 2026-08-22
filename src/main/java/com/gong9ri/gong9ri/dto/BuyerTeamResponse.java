package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.TeamParticipation;
import com.gong9ri.gong9ri.entity.TeamStatus;
import java.time.LocalDateTime;

public record BuyerTeamResponse(
        Long teamId,
        // 공구팀 번호(admin-identifier-codes, 2026-08-22) — teamId와 같은 자리에 노출한다.
        String teamNo,
        Long productId,
        String productName,
        Integer currentCount,
        Integer maxParticipants,
        TeamStatus status,
        LocalDateTime deadline,
        LocalDateTime joinedAt,
        // 썸네일 표시용 대표 이미지 URL(null이면 프론트에서 기본 아이콘으로 대체).
        String imageUrl
) {
    public static BuyerTeamResponse from(TeamParticipation participation) {
        GroupBuyTeam team = participation.getTeam();
        return new BuyerTeamResponse(
                team.getId(),
                team.getTeamNo(),
                team.getProduct().getId(),
                team.getProduct().getName(),
                team.getCurrentCount(),
                team.getMaxParticipants(),
                team.getStatus(),
                team.getDeadline(),
                participation.getJoinedAt(),
                team.getProduct().getImageUrl()
        );
    }
}
