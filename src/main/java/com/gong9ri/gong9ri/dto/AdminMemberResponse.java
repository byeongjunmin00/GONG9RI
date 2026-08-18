package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Role;
import java.time.LocalDateTime;

public record AdminMemberResponse(
        Long memberId,
        String username,
        String name,
        String email,
        Role role,
        boolean emailVerified,
        boolean suspended,
        LocalDateTime createdAt
) {
    public static AdminMemberResponse from(Member member) {
        return new AdminMemberResponse(
                member.getId(),
                member.getUsername(),
                member.getName(),
                member.getEmail(),
                member.getRole(),
                member.isEmailVerified(),
                member.isSuspended(),
                member.getCreatedAt()
        );
    }
}
