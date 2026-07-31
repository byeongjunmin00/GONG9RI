package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Role;

public record MemberResponse(
        Long memberId,
        String username,
        String name,
        Role role
) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getId(), member.getUsername(), member.getName(), member.getRole());
    }
}
