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
        LocalDateTime createdAt,
        // 회원의 종합 활동 정보 (구매 결제 수, 공구 참여 수, 등록 상품 수)
        int purchaseCount,
        int teamCount,
        int productCount,
        // 프로필 사진(member/profile-image 노출, 2026-08-21). 이름과 같은 회원 엔티티에서 읽으므로
        // 추가 조회가 없다. 없으면 null → 프론트가 이름 첫 글자 동그라미를 그린다.
        String profileImageUrl
) {
    public static AdminMemberResponse from(Member member) {
        return of(member, 0, 0, 0);
    }

    public static AdminMemberResponse of(Member member, int purchaseCount, int teamCount, int productCount) {
        return new AdminMemberResponse(
                member.getId(),
                member.getUsername(),
                member.getName(),
                member.getEmail(),
                member.getRole(),
                member.isEmailVerified(),
                member.isSuspended(),
                member.getCreatedAt(),
                purchaseCount,
                teamCount,
                productCount,
                member.getProfileImageUrl()
        );
    }
}
