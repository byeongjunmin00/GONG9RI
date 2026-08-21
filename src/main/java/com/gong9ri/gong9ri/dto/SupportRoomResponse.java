package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.SupportRoom;
import com.gong9ri.gong9ri.entity.SupportRoomStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 상담방 + 대화 (support/chat).
 *
 * <p>{@code messages}는 방 상세 조회에서만 채운다 — 관리자 목록은 방이 여러 개라 전부 실으면
 * 응답이 커지고 쿼리도 방 수만큼 늘어난다(목록에서는 null).
 */
public record SupportRoomResponse(
        Long roomId,
        Long memberId,
        String memberName,
        SupportRoomStatus status,
        LocalDateTime createdAt,
        LocalDateTime lastMessageAt,
        int unreadForMember,
        int unreadForAdmin,
        List<SupportMessageResponse> messages,
        // 프로필 사진(member/profile-image 노출, 2026-08-21). 이름과 같은 회원 엔티티에서 읽으므로
        // 추가 조회가 없다. 없으면 null → 프론트가 이름 첫 글자 동그라미를 그린다.
        String memberProfileImageUrl
) {
    public static SupportRoomResponse of(SupportRoom room, List<SupportMessageResponse> messages) {
        return new SupportRoomResponse(
                room.getId(),
                room.getMember().getId(),
                room.getMember().getName(),
                room.getStatus(),
                room.getCreatedAt(),
                room.getLastMessageAt(),
                room.getUnreadForMember(),
                room.getUnreadForAdmin(),
                messages,
                room.getMember().getProfileImageUrl());
    }

    /** 목록용 — 대화 내용 없이 방 정보만. */
    public static SupportRoomResponse summary(SupportRoom room) {
        return of(room, null);
    }
}
