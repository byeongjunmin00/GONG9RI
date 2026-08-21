package com.gong9ri.gong9ri.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gong9ri.gong9ri.dto.SupportMessageResponse;
import com.gong9ri.gong9ri.dto.SupportRoomResponse;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.NotificationRepository;
import com.gong9ri.gong9ri.repository.SupportMessageRepository;
import com.gong9ri.gong9ri.repository.SupportRoomRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 상담 읽음 표시 (support/chat, 2026-08-21).
 *
 * <p>고정하려는 것은 <b>"내가 보낸 메시지를 상대가 읽었는가"의 판정</b>이다. 메시지마다 읽음 행을
 * 쌓지 않고 방에 "각 측이 마지막으로 읽은 시각"만 두기 때문에, 그 시각 비교가 맞아야 표시가 맞는다.
 *
 * <p>특히 <b>읽기 전에는 false여야 한다</b> — 읽지 않았는데 읽음으로 보이는 것이 반대보다 나쁘다.
 */
@SpringBootTest
class SupportChatReadReceiptTest {

    @Autowired
    private SupportChatService supportChatService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private SupportRoomRepository supportRoomRepository;
    @Autowired
    private SupportMessageRepository supportMessageRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private static final String BUYER_USERNAME = "readReceiptBuyer";
    private static final String ADMIN_USERNAME = "readReceiptAdmin";

    private Long roomId;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status ->
                memberRepository.findByUsername(BUYER_USERNAME).ifPresent(buyer ->
                        supportRoomRepository.findAll().stream()
                                .filter(room -> room.getMember().getId().equals(buyer.getId()))
                                .forEach(room -> {
                                    supportMessageRepository.deleteByRoom_Id(room.getId());
                                    supportRoomRepository.delete(room);
                                })));
        for (String username : new String[] {BUYER_USERNAME, ADMIN_USERNAME}) {
            deleteMemberWithNotifications(username);
        }
    }

    /** 상담 알림이 AFTER_COMMIT + @Async라 정리 뒤에 도착할 수 있다 — 도착하면 FK가 삭제를 막는다. */
    private void deleteMemberWithNotifications(String username) {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                transactionTemplate.executeWithoutResult(status ->
                        memberRepository.findByUsername(username).ifPresent(member -> {
                            notificationRepository.findAllByMemberIdOrderByCreatedAtDesc(member.getId())
                                    .forEach(n -> notificationRepository.deleteById(n.getId()));
                            memberRepository.deleteById(member.getId());
                            memberRepository.flush();
                        }));
                return;
            } catch (Exception e) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    @Test
    @DisplayName("관리자가 읽기 전에는 읽음이 아니고, 읽은 뒤에 읽음이 된다")
    void adminRead_flipsReadFlagOnBuyerMessage() {
        Member buyer = save(BUYER_USERNAME, Role.BUYER);
        Member admin = save(ADMIN_USERNAME, Role.ADMIN);

        SupportRoomResponse room = transactionTemplate.execute(s -> supportChatService.openRoom(buyer));
        roomId = room.roomId();
        transactionTemplate.executeWithoutResult(s ->
                supportChatService.send(buyer, roomId, "환불이 안 돼요"));

        assertFalse(firstMessage(buyer).readByCounterpart(),
                "관리자가 아직 안 읽었는데 읽음으로 표시되면 안 된다");

        transactionTemplate.executeWithoutResult(s -> supportChatService.markRead(admin, roomId));

        assertTrue(firstMessage(buyer).readByCounterpart(),
                "관리자가 읽은 뒤에는 구매자가 보낸 메시지가 읽음이어야 한다");
    }

    @Test
    @DisplayName("내가 읽어도 내 메시지가 읽음이 되지는 않는다 — 읽어야 하는 건 상대다")
    void ownRead_doesNotMarkOwnMessage() {
        Member buyer = save(BUYER_USERNAME, Role.BUYER);
        save(ADMIN_USERNAME, Role.ADMIN);

        SupportRoomResponse room = transactionTemplate.execute(s -> supportChatService.openRoom(buyer));
        roomId = room.roomId();
        transactionTemplate.executeWithoutResult(s ->
                supportChatService.send(buyer, roomId, "안녕하세요"));

        transactionTemplate.executeWithoutResult(s -> supportChatService.markRead(buyer, roomId));

        assertFalse(firstMessage(buyer).readByCounterpart(),
                "본인이 읽은 것으로 자기 메시지가 읽음이 되면 읽음 표시가 항상 켜져 무의미해진다");
    }

    private SupportMessageResponse firstMessage(Member viewer) {
        return transactionTemplate.execute(s -> {
            List<SupportMessageResponse> messages = supportChatService.room(viewer, roomId).messages();
            return messages.get(0);
        });
    }

    private Member save(String username, Role role) {
        return transactionTemplate.execute(s -> memberRepository.save(
                new Member(username, "pw", username, username + "@test.com", role)));
    }
}
