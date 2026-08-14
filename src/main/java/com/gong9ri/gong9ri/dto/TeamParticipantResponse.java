package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.TeamParticipation;
import java.time.LocalDateTime;

/**
 * 공구팀 참여자 목록 표시용 응답 — 실명 원문/memberId는 절대 노출하지 않는다
 * (docs/dev/team/crud/changes/, "공구팀 상세 — 참여자 목록 표시" 참고).
 */
public record TeamParticipantResponse(
        String displayName,
        Boolean isLeader,
        LocalDateTime joinedAt
) {
    public static TeamParticipantResponse from(TeamParticipation participation, boolean isLeader) {
        return new TeamParticipantResponse(
                maskName(participation.getMember().getName()),
                isLeader,
                participation.getJoinedAt()
        );
    }

    /**
     * 첫 글자만 노출하고 나머지는 글자 수만큼 '*'로 마스킹한다(예: "김철수" -> "김**").
     * 이름이 1글자인 극단 케이스는 첫 글자마저 노출하면 마스킹 효과가 없으므로 전체를 '*' 하나로 가린다
     * (최소한의 마스킹 보장).
     */
    private static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        if (name.length() == 1) {
            return "*";
        }
        return name.charAt(0) + "*".repeat(name.length() - 1);
    }
}
