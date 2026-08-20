package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.dto.SupportMessageResponse;
import com.gong9ri.gong9ri.dto.SupportRoomResponse;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.entity.SupportMessage;
import com.gong9ri.gong9ri.entity.SupportRoom;
import com.gong9ri.gong9ri.entity.SupportRoomStatus;
import com.gong9ri.gong9ri.repository.SupportMessageRepository;
import com.gong9ri.gong9ri.repository.SupportRoomRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 1:1 실시간 상담 (support/chat).
 *
 * <p><b>이 클래스의 본체는 채팅이 아니라 권한 검사다.</b> 상담 내용은 사적인 대화라, 방의 당사자와
 * 관리자만 읽고 쓸 수 있어야 한다. WebSocket 구독·발행과 REST 조회가 <b>같은 판정</b>({@link
 * #requireParticipant})을 쓰도록 한 곳에 모았다 — 경로마다 따로 검사하면 한쪽만 빠뜨린다.
 *
 * <p>기존 WebSocket 채널({@code /topic/products/../teams})은 공개 정보(공구팀 정원)를 브로드캐스트해서
 * 인증이 없었다. 그 구조를 그대로 상담에 쓰면 아무나 남의 상담방을 구독해 훔쳐볼 수 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupportChatService {

    private final SupportRoomRepository supportRoomRepository;
    private final SupportMessageRepository supportMessageRepository;

    /**
     * 상담 시작. <b>이미 열린 방이 있으면 그 방을 그대로 돌려준다</b> — 한 회원이 방을 여러 개 열면
     * 관리자 목록이 지저분해지고 "내 상담"이 하나로 특정되지 않는다.
     */
    @Transactional
    public SupportRoomResponse openRoom(Member member) {
        SupportRoom room = supportRoomRepository
                .findByMember_IdAndStatus(member.getId(), SupportRoomStatus.OPEN)
                .orElseGet(() -> supportRoomRepository.save(new SupportRoom(member)));
        return SupportRoomResponse.of(room, messagesOf(room.getId()));
    }

    /** 내 상담(열린 방). 없으면 만들지 않고 null을 준다 — 조회가 방을 만드는 부작용을 갖지 않게. */
    public SupportRoomResponse myRoom(Member member) {
        return supportRoomRepository.findByMember_IdAndStatus(member.getId(), SupportRoomStatus.OPEN)
                .map(room -> SupportRoomResponse.of(room, messagesOf(room.getId())))
                .orElse(null);
    }

    public Page<SupportRoomResponse> roomsForAdmin(Member admin, int page, int size) {
        requireAdmin(admin);
        if (page < 0 || size < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return supportRoomRepository.findAllForAdmin(PageRequest.of(page, size))
                .map(SupportRoomResponse::summary);
    }

    public long unreadRoomCountForAdmin(Member admin) {
        requireAdmin(admin);
        return supportRoomRepository.countByUnreadForAdminGreaterThan(0);
    }

    public SupportRoomResponse room(Member viewer, Long roomId) {
        SupportRoom room = requireParticipant(viewer, roomId);
        return SupportRoomResponse.of(room, messagesOf(room.getId()));
    }

    /**
     * 메시지 저장. <b>저장이 먼저고 브로드캐스트가 나중이다</b> — 반대로 하면 화면에는 떴는데 DB엔
     * 없는 메시지가 생긴다. 브로드캐스트는 호출부(WebSocket 핸들러)가 이 반환값으로 한다.
     */
    @Transactional
    public SupportMessageResponse send(Member sender, Long roomId, String rawContent) {
        SupportRoom room = requireParticipant(sender, roomId);
        if (!room.isOpen()) {
            throw new BusinessException(ErrorCode.SUPPORT_ROOM_CLOSED);
        }
        String content = rawContent == null ? "" : rawContent.trim();
        if (content.isEmpty() || content.length() > SupportMessage.MAX_CONTENT_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        boolean byAdmin = isAdmin(sender);
        SupportMessage saved = supportMessageRepository.save(
                new SupportMessage(room, sender, byAdmin, content));
        room.onMessageSent(byAdmin);
        // 보낸 쪽은 자기 메시지를 이미 봤으므로 그쪽 미읽음은 0으로 맞춘다.
        room.markReadBy(byAdmin);
        return SupportMessageResponse.from(saved);
    }

    @Transactional
    public void markRead(Member viewer, Long roomId) {
        SupportRoom room = requireParticipant(viewer, roomId);
        room.markReadBy(isAdmin(viewer));
    }

    @Transactional
    public void close(Member actor, Long roomId) {
        SupportRoom room = requireParticipant(actor, roomId);
        room.close();
        log.info("상담 종료: roomId={}, byMemberId={}", roomId, actor.getId());
    }

    /**
     * <b>이 프로젝트에서 상담 접근을 판정하는 유일한 지점.</b> 방의 당사자 본인 또는 관리자만 통과한다.
     *
     * <p>남의 방을 <b>404가 아니라 403</b>으로 막는다 — 존재 여부까지 감출 필요는 없고(방 id는 순번이라
     * 어차피 추측 가능), "권한이 없다"가 사실에 더 가깝다.
     */
    public SupportRoom requireParticipant(Member viewer, Long roomId) {
        SupportRoom room = supportRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUPPORT_ROOM_NOT_FOUND));
        if (!isAdmin(viewer) && !room.isOwnedBy(viewer.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return room;
    }

    private List<SupportMessageResponse> messagesOf(Long roomId) {
        return supportMessageRepository.findByRoomIdWithSender(roomId).stream()
                .map(SupportMessageResponse::from)
                .toList();
    }

    private boolean isAdmin(Member member) {
        return member.getRole() == Role.ADMIN;
    }

    private void requireAdmin(Member member) {
        if (!isAdmin(member)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
