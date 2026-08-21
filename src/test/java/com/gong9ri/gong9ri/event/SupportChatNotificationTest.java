package com.gong9ri.gong9ri.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Notification;
import com.gong9ri.gong9ri.entity.NotificationType;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.NotificationRepository;
import com.gong9ri.gong9ri.repository.SupportMessageRepository;
import com.gong9ri.gong9ri.repository.SupportRoomRepository;
import com.gong9ri.gong9ri.service.SupportChatService;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 고객센터 상담 알림 (support/chat).
 *
 * <p><b>{@code @Transactional}을 붙이면 안 된다.</b> 알림은 {@code @TransactionalEventListener(AFTER_COMMIT)}
 * + {@code @Async}로 발행되므로, 롤백되는 테스트 트랜잭션 안에서는 커밋이 없어 알림이 아예 생기지 않는다
 * ({@code NotificationTypesFlowTest}와 같은 이유). 그래서 실제로 커밋하고 {@code @AfterEach}에서 직접 치운다.
 *
 * <p>이 테스트의 핵심은 "알림이 온다"가 아니라 <b>"메시지마다 오지는 않는다"</b>이다 — 사용자가 세 줄로
 * 나눠 쓰면 알림이 세 개 오는 게 흔한 실수라, 그 경계를 고정한다.
 */
@SpringBootTest
class SupportChatNotificationTest {

    private static final long ASYNC_WAIT_TIMEOUT_MS = 5000;
    private static final long ASYNC_WAIT_INTERVAL_MS = 50;

    @Autowired
    private SupportChatService supportChatService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private SupportRoomRepository supportRoomRepository;
    @Autowired
    private SupportMessageRepository supportMessageRepository;

    private final List<Long> memberIdsToClean = new ArrayList<>();
    private final List<Long> roomIdsToClean = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        roomIdsToClean.forEach(roomId -> {
            supportMessageRepository.deleteByRoom_Id(roomId);
            supportRoomRepository.deleteById(roomId);
        });
        roomIdsToClean.clear();
        for (Long memberId : memberIdsToClean) {
            notificationRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId)
                    .forEach(n -> notificationRepository.deleteById(n.getId()));
        }
        memberIdsToClean.forEach(memberRepository::deleteById);
        memberIdsToClean.clear();
    }

    private Member saveMember(String username, Role role) {
        Member saved = memberRepository.save(
                new Member(username, "pw", username + "이름", username + "@test.com", role));
        memberIdsToClean.add(saved.getId());
        return saved;
    }

    private Long openRoom(Member member) {
        Long roomId = supportChatService.openRoom(member).roomId();
        roomIdsToClean.add(roomId);
        return roomId;
    }

    private List<Notification> supportNotificationsOf(Member member) {
        return notificationRepository.findAllByMemberIdOrderByCreatedAtDesc(member.getId()).stream()
                .filter(n -> n.getType() == NotificationType.SUPPORT_MESSAGE_RECEIVED)
                .toList();
    }

    /** 리스너가 @Async라 서비스 호출 직후엔 아직 알림이 없을 수 있다 — 폴링으로 기다린다. */
    private void waitUntil(BooleanSupplier condition, String failureMessage) {
        long deadline = System.currentTimeMillis() + ASYNC_WAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(ASYNC_WAIT_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("대기 중 인터럽트됨");
            }
        }
        fail(failureMessage);
    }

    @Test
    @DisplayName("상담 메시지가 오면 관리자에게 알림이 간다 — 대시보드를 안 봐도 알 수 있게")
    void send_notifiesAdmin() {
        Member buyer = saveMember("sc-noti-buyer", Role.BUYER);
        Member admin = saveMember("sc-noti-admin", Role.ADMIN);
        Long roomId = openRoom(buyer);

        supportChatService.send(buyer, roomId, "결제가 안 돼요");

        waitUntil(() -> supportNotificationsOf(admin).size() == 1, "관리자에게 알림이 가야 한다");
    }

    @Test
    @DisplayName("연속으로 보내도 알림은 한 번만 — 세 줄로 나눠 쓰면 알림 세 개는 곤란하다")
    void send_doesNotSpamAdmin() {
        Member buyer = saveMember("sc-noti-buyer2", Role.BUYER);
        Member admin = saveMember("sc-noti-admin2", Role.ADMIN);
        Long roomId = openRoom(buyer);

        supportChatService.send(buyer, roomId, "안녕하세요");
        supportChatService.send(buyer, roomId, "결제가");
        supportChatService.send(buyer, roomId, "안 돼요");

        // 먼저 한 건이 도착하는 걸 확인한 뒤, 나머지가 오지 않는지 본다 — 비동기라 "아직 안 온 것"과
        // "영영 안 오는 것"을 즉시 구분할 수 없기 때문이다.
        waitUntil(() -> supportNotificationsOf(admin).size() >= 1, "첫 알림은 와야 한다");
        assertEquals(1, supportNotificationsOf(admin).size(),
                "미읽음이 0→1로 바뀔 때만 알린다. 메시지마다 보내면 알림 폭탄이 된다");
    }

    @Test
    @DisplayName("관리자가 읽은 뒤 새 메시지가 오면 다시 알림이 간다")
    void send_notifiesAgainAfterRead() {
        Member buyer = saveMember("sc-noti-buyer3", Role.BUYER);
        Member admin = saveMember("sc-noti-admin3", Role.ADMIN);
        Long roomId = openRoom(buyer);

        supportChatService.send(buyer, roomId, "첫 문의");
        waitUntil(() -> supportNotificationsOf(admin).size() == 1, "첫 알림이 와야 한다");

        supportChatService.markRead(admin, roomId);
        supportChatService.send(buyer, roomId, "추가 문의");

        waitUntil(() -> supportNotificationsOf(admin).size() == 2,
                "읽고 나면 다시 0이 되므로 그다음 메시지에는 또 알려야 한다");
    }

    @Test
    @DisplayName("관리자가 보낸 답변으로는 관리자 자신에게 알림이 가지 않는다")
    void adminReply_doesNotNotifyAdmin() {
        Member buyer = saveMember("sc-noti-buyer4", Role.BUYER);
        Member admin = saveMember("sc-noti-admin4", Role.ADMIN);
        Long roomId = openRoom(buyer);

        supportChatService.send(admin, roomId, "안녕하세요, 무엇을 도와드릴까요?");
        supportChatService.send(buyer, roomId, "네 문의드려요");

        // 구매자 메시지로 생긴 알림이 도착한 뒤에 세면, 관리자 답변분이 없다는 게 확인된다.
        waitUntil(() -> supportNotificationsOf(admin).size() == 1, "구매자 메시지 알림은 와야 한다");
        assertEquals(1, supportNotificationsOf(admin).size(),
                "관리자 답변으로 자기 자신에게 알림이 가면 안 된다");
    }
}
