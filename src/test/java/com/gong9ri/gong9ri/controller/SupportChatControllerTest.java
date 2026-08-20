package com.gong9ri.gong9ri.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.SupportRoomRepository;
import com.gong9ri.gong9ri.service.SupportChatService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 1:1 상담 (support/chat).
 *
 * <p><b>이 테스트의 무게중심은 권한이다.</b> 상담은 사적인 대화라, "남의 방을 못 본다"가 깨지면
 * 기능이 동작하는지와 무관하게 사고다. 그래서 정상 흐름보다 거절 경로를 먼저·더 많이 고정한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SupportChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private SupportRoomRepository supportRoomRepository;

    @Autowired
    private SupportChatService supportChatService;

    private Member saveMember(String username, Role role) {
        return memberRepository.save(
                new Member(username, "encoded-password", username + "이름", username + "@test.com", role));
    }

    private RequestPostProcessor asUser(Member member) {
        MemberUserDetails principal = new MemberUserDetails(member);
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
    }

    private Long openRoomFor(Member member) {
        return supportChatService.openRoom(member).roomId();
    }

    @Test
    @DisplayName("[권한] 남의 상담방은 조회할 수 없다 (403)")
    void room_forbidden_forOtherMember() throws Exception {
        Member owner = saveMember("sc-owner1", Role.BUYER);
        Member stranger = saveMember("sc-stranger1", Role.BUYER);
        Long roomId = openRoomFor(owner);

        mockMvc.perform(get("/api/support/rooms/" + roomId).with(asUser(stranger)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("[권한] 남의 상담방을 읽음처리할 수 없다 (403)")
    void markRead_forbidden_forOtherMember() throws Exception {
        Member owner = saveMember("sc-owner2", Role.BUYER);
        Member stranger = saveMember("sc-stranger2", Role.SELLER);
        Long roomId = openRoomFor(owner);

        mockMvc.perform(post("/api/support/rooms/" + roomId + "/read").with(asUser(stranger)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[권한] 비로그인은 상담 API에 접근할 수 없다 (401)")
    void unauthorized_withoutLogin() throws Exception {
        mockMvc.perform(get("/api/support/rooms/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[권한] 관리자는 남의 상담방도 볼 수 있다 — 상담을 받는 쪽이라 당연히 필요하다")
    void room_allowed_forAdmin() throws Exception {
        Member owner = saveMember("sc-owner3", Role.BUYER);
        Member admin = saveMember("sc-admin3", Role.ADMIN);
        Long roomId = openRoomFor(owner);

        mockMvc.perform(get("/api/support/rooms/" + roomId).with(asUser(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roomId").value(roomId));
    }

    @Test
    @DisplayName("[권한] 관리자가 아니면 상담 목록을 볼 수 없다 (403)")
    void adminRooms_forbidden_forNonAdmin() throws Exception {
        Member buyer = saveMember("sc-buyer4", Role.BUYER);

        mockMvc.perform(get("/api/admin/support/rooms").with(asUser(buyer)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("상담을 두 번 열어도 방은 하나다 — 관리자 목록이 지저분해지지 않게")
    void openRoom_isIdempotent_whileOpen() throws Exception {
        Member buyer = saveMember("sc-buyer5", Role.BUYER);

        Long first = openRoomFor(buyer);
        Long second = openRoomFor(buyer);

        org.junit.jupiter.api.Assertions.assertEquals(first, second);
        org.junit.jupiter.api.Assertions.assertEquals(1, supportRoomRepository.findAll().stream()
                .filter(r -> r.getMember().getId().equals(buyer.getId())).count());
    }

    @Test
    @DisplayName("내 상담 조회는 방을 만들지 않는다 — 조회에 부작용이 없어야 한다")
    void myRoom_doesNotCreateRoom() throws Exception {
        Member buyer = saveMember("sc-buyer6", Role.BUYER);

        mockMvc.perform(get("/api/support/rooms/me").with(asUser(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        org.junit.jupiter.api.Assertions.assertTrue(supportRoomRepository.findAll().stream()
                .noneMatch(r -> r.getMember().getId().equals(buyer.getId())));
    }

    @Test
    @DisplayName("메시지를 보내면 상대편 미읽음만 올라간다 (보낸 쪽은 0)")
    void send_increasesUnreadForOtherSideOnly() throws Exception {
        Member buyer = saveMember("sc-buyer7", Role.BUYER);
        Long roomId = openRoomFor(buyer);

        supportChatService.send(buyer, roomId, "결제가 안 돼요");

        var room = supportRoomRepository.findById(roomId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(1, room.getUnreadForAdmin(),
                "관리자가 확인해야 할 메시지가 쌓여야 한다");
        org.junit.jupiter.api.Assertions.assertEquals(0, room.getUnreadForMember(),
                "보낸 사람 쪽 미읽음이 올라가면 자기 메시지를 안 읽은 것으로 표시된다");
    }

    @Test
    @DisplayName("빈 메시지·길이 초과는 거절한다")
    void send_rejectsInvalidContent() throws Exception {
        Member buyer = saveMember("sc-buyer8", Role.BUYER);
        Long roomId = openRoomFor(buyer);

        org.junit.jupiter.api.Assertions.assertThrows(
                com.gong9ri.gong9ri.common.exception.BusinessException.class,
                () -> supportChatService.send(buyer, roomId, "   "));
        org.junit.jupiter.api.Assertions.assertThrows(
                com.gong9ri.gong9ri.common.exception.BusinessException.class,
                () -> supportChatService.send(buyer, roomId, "가".repeat(1001)));
    }

    @Test
    @DisplayName("종료된 상담에는 메시지를 보낼 수 없다")
    void send_rejectedAfterClose() throws Exception {
        Member buyer = saveMember("sc-buyer9", Role.BUYER);
        Long roomId = openRoomFor(buyer);
        supportChatService.close(buyer, roomId);

        org.junit.jupiter.api.Assertions.assertThrows(
                com.gong9ri.gong9ri.common.exception.BusinessException.class,
                () -> supportChatService.send(buyer, roomId, "추가 문의"));
    }
}
