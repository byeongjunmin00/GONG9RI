package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.TeamStatus;
import java.time.LocalDateTime;
import java.util.List;

public record SellerTeamResponse(
        Long teamId,
        // 공구팀 번호(admin-identifier-codes, 2026-08-22) — teamId와 같은 자리에 노출한다.
        String teamNo,
        Long productId,
        String productName,
        Integer currentCount,
        Integer maxParticipants,
        TeamStatus status,
        // 누가 팀을 열었고 누가 들어와 있는지. 판매자 마이페이지가 상품명과 인원 수만 보여줘서
        // "누가 참여했는지" 알 수 없었다(2026-08-20 사용자 리포트).
        String leaderName,
        // 참여한 순서(joinedAt 오름차순). 리더도 참여자이므로 이 목록에 포함된다.
        List<String> participantNames,
        LocalDateTime deadline,
        LocalDateTime createdAt,
        // 썸네일 표시용 대표 이미지 URL(null이면 프론트에서 기본 아이콘으로 대체).
        String imageUrl
) {
    public static SellerTeamResponse from(GroupBuyTeam team, List<String> participantNames) {
        return new SellerTeamResponse(
                team.getId(),
                team.getTeamNo(),
                team.getProduct().getId(),
                team.getProduct().getName(),
                team.getCurrentCount(),
                team.getMaxParticipants(),
                team.getStatus(),
                team.getLeader().getName(),
                participantNames,
                team.getDeadline(),
                team.getCreatedAt(),
                team.getProduct().getImageUrl()
        );
    }
}
