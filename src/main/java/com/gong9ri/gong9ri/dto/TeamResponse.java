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
        LocalDateTime createdAt,
        // 이 응답을 요청한 로그인 사용자 자신이 이 팀의 현재 참여자인지 여부(team-payment-enforcement).
        // 다른 참여자의 신원은 여전히 비공개 — 오직 "나 자신의 참여 여부"만 노출한다. 비로그인 요청이면
        // 항상 false. 프론트(product.js)가 이 값으로 "참가하기" 대신 "참여 취소" 버튼을 보여줄지 판단한다.
        Boolean joinedByCurrentMember
) {
    public static TeamResponse from(GroupBuyTeam team, boolean joinedByCurrentMember) {
        return new TeamResponse(
                team.getId(),
                team.getProduct().getId(),
                team.getLeader().getId(),
                team.getCurrentCount(),
                team.getMaxParticipants(),
                team.getStatus(),
                team.getDeadline(),
                team.getCreatedAt(),
                joinedByCurrentMember
        );
    }
}
