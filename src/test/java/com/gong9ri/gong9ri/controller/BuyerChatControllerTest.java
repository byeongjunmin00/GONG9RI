package com.gong9ri.gong9ri.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.entity.ChatMessage;
import com.gong9ri.gong9ri.entity.ChatRole;
import com.gong9ri.gong9ri.entity.ChatSession;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.repository.ChatMessageRepository;
import com.gong9ri.gong9ri.repository.ChatSessionRepository;
import com.gong9ri.gong9ri.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BuyerChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    private Member saveMember(String username, Role role) {
        return memberRepository.save(new Member(username, "encoded-password", "테스트유저", username + "@test.com",
                role));
    }

    private RequestPostProcessor asUser(Member member) {
        MemberUserDetails principal = new MemberUserDetails(member);
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return authentication(token);
    }

    @Test
    @DisplayName("본인 세션의 대화 이력을 조회할 수 있다")
    void history_success() throws Exception {
        Member buyer = saveMember("chatCtrlBuyer1", Role.BUYER);
        ChatSession session = chatSessionRepository.save(new ChatSession(buyer));
        chatMessageRepository.save(new ChatMessage(session, ChatRole.USER, "안녕"));
        chatMessageRepository.save(new ChatMessage(session, ChatRole.ASSISTANT, "안녕하세요"));

        mockMvc.perform(get("/api/buyer/chat/sessions/" + session.getId() + "/messages").with(asUser(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].role").value("USER"))
                .andExpect(jsonPath("$.data[1].role").value("ASSISTANT"));
    }

    @Test
    @DisplayName("다른 구매자의 세션 이력을 조회하면 403 FORBIDDEN")
    void history_forbidden_notOwner() throws Exception {
        Member owner = saveMember("chatCtrlOwner1", Role.BUYER);
        Member other = saveMember("chatCtrlOther1", Role.BUYER);
        ChatSession session = chatSessionRepository.save(new ChatSession(owner));

        mockMvc.perform(get("/api/buyer/chat/sessions/" + session.getId() + "/messages").with(asUser(other)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("존재하지 않는 세션을 조회하면 404 CHAT_SESSION_NOT_FOUND")
    void history_notFound_unknownSession() throws Exception {
        Member buyer = saveMember("chatCtrlBuyer2", Role.BUYER);

        mockMvc.perform(get("/api/buyer/chat/sessions/999999999/messages").with(asUser(buyer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT_SESSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("비로그인으로 대화 이력을 조회하면 401 UNAUTHORIZED")
    void history_unauthorized() throws Exception {
        Member buyer = saveMember("chatCtrlBuyer3", Role.BUYER);
        ChatSession session = chatSessionRepository.save(new ChatSession(buyer));

        mockMvc.perform(get("/api/buyer/chat/sessions/" + session.getId() + "/messages"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("세션 토큰 사용량을 조회할 수 있다")
    void sessionUsage_success() throws Exception {
        Member buyer = saveMember("chatCtrlBuyer4", Role.BUYER);
        ChatSession session = chatSessionRepository.save(new ChatSession(buyer));

        mockMvc.perform(get("/api/buyer/chat/sessions/" + session.getId() + "/usage").with(asUser(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(session.getId()))
                .andExpect(jsonPath("$.data.totalTokens").value(0));
    }

    @Test
    @DisplayName("모델별 통계 대시보드는 로그인만 하면 조회할 수 있다")
    void stats_authenticated_returnsOk() throws Exception {
        Member buyer = saveMember("chatCtrlBuyer5", Role.BUYER);

        mockMvc.perform(get("/api/chat/stats").with(asUser(buyer)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("비로그인으로 통계 대시보드를 조회하면 401 UNAUTHORIZED")
    void stats_unauthorized() throws Exception {
        mockMvc.perform(get("/api/chat/stats"))
                .andExpect(status().isUnauthorized());
    }
}
