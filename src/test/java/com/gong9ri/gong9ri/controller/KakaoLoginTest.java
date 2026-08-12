package com.gong9ri.gong9ri.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gong9ri.gong9ri.client.KakaoClient;
import com.gong9ri.gong9ri.client.KakaoUserInfo;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 고도화 3단계 — 카카오 로그인. 전체 페이지 리다이렉트 흐름이라 JSON 응답이 아니라 302 +
 * {@code Location} 헤더로 검증한다. {@code KakaoClient}는 항상 {@code @MockitoBean}으로 대체해 실제
 * 카카오 API를 호출하지 않는다(docs/dev/auth/social-login/design.md).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class KakaoLoginTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @MockitoBean
    private KakaoClient kakaoClient;

    private MockHttpSession startAuthorizeFlowAndGetSession() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/kakao/login"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String extractState(MockHttpSession session) {
        return (String) session.getAttribute("kakao_oauth_state");
    }

    @Test
    @DisplayName("처음 카카오로 로그인하면 새 회원이 생성되고 로그인된다")
    void kakaoCallback_newUser_createsAccountAndLogsIn() throws Exception {
        MockHttpSession session = startAuthorizeFlowAndGetSession();
        String state = extractState(session);

        when(kakaoClient.exchangeCodeForAccessToken(anyString(), anyString())).thenReturn("test-access-token");
        when(kakaoClient.getUserInfo("test-access-token"))
                .thenReturn(new KakaoUserInfo(111111L, null, "카카오테스터"));

        MvcResult result = mockMvc.perform(get("/api/auth/kakao/callback")
                        .param("code", "test-code")
                        .param("state", state)
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        assertNotNull(result.getRequest().getSession(false));
        Member created = memberRepository.findByKakaoId("111111").orElseThrow();
        assertEquals("kakao_111111", created.getUsername());
        assertEquals(Role.BUYER, created.getRole());
        assertTrue(created.isEmailVerified());
        assertEquals("kakao_111111@kakao.local", created.getEmail());
    }

    @Test
    @DisplayName("이미 연동된 카카오 계정으로 다시 로그인하면 회원을 새로 만들지 않고 그대로 로그인된다")
    void kakaoCallback_existingKakaoAccount_reusesSameMember() throws Exception {
        when(kakaoClient.exchangeCodeForAccessToken(anyString(), anyString())).thenReturn("test-access-token");
        when(kakaoClient.getUserInfo("test-access-token"))
                .thenReturn(new KakaoUserInfo(222222L, null, "재로그인테스터"));

        MockHttpSession session1 = startAuthorizeFlowAndGetSession();
        mockMvc.perform(get("/api/auth/kakao/callback")
                        .param("code", "code-1")
                        .param("state", extractState(session1))
                        .session(session1))
                .andExpect(status().is3xxRedirection());
        Long firstMemberId = memberRepository.findByKakaoId("222222").orElseThrow().getId();

        MockHttpSession session2 = startAuthorizeFlowAndGetSession();
        MvcResult result2 = mockMvc.perform(get("/api/auth/kakao/callback")
                        .param("code", "code-2")
                        .param("state", extractState(session2))
                        .session(session2))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        assertNotNull(result2.getRequest().getSession(false));
        Long secondMemberId = memberRepository.findByKakaoId("222222").orElseThrow().getId();
        assertEquals(firstMemberId, secondMemberId);
    }

    @Test
    @DisplayName("카카오 이메일이 이미 다른 계정에서 쓰이고 있으면 로그인 실패 페이지로 리다이렉트된다")
    void kakaoCallback_emailAlreadyRegistered_redirectsToError() throws Exception {
        Member existing = new Member("existing-user", "encoded", "기존회원", "shared@test.com", Role.BUYER);
        memberRepository.save(existing);

        MockHttpSession session = startAuthorizeFlowAndGetSession();
        String state = extractState(session);

        when(kakaoClient.exchangeCodeForAccessToken(anyString(), anyString())).thenReturn("test-access-token");
        when(kakaoClient.getUserInfo("test-access-token"))
                .thenReturn(new KakaoUserInfo(333333L, "shared@test.com", "충돌테스터"));

        mockMvc.perform(get("/api/auth/kakao/callback")
                        .param("code", "test-code")
                        .param("state", state)
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login.html?error=kakao"));

        assertTrue(memberRepository.findByKakaoId("333333").isEmpty());
    }

    @Test
    @DisplayName("state가 일치하지 않으면(위조/세션 없음) 로그인 실패 페이지로 리다이렉트되고 카카오 API를 호출하지 않는다")
    void kakaoCallback_stateMismatch_redirectsToErrorWithoutCallingKakao() throws Exception {
        mockMvc.perform(get("/api/auth/kakao/callback")
                        .param("code", "test-code")
                        .param("state", "wrong-or-missing-state"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login.html?error=kakao"));

        org.mockito.Mockito.verify(kakaoClient, org.mockito.Mockito.never())
                .exchangeCodeForAccessToken(anyString(), any());
    }
}
