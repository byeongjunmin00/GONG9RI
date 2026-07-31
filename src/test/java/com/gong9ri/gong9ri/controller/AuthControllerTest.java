package com.gong9ri.gong9ri.controller;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gong9ri.gong9ri.repository.MemberRepository;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import tools.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("정상 회원가입 시 201과 회원 정보를 반환하고 비밀번호는 암호화되어 저장된다")
    void signup_success() throws Exception {
        Map<String, Object> request = Map.of(
                "username", "gonguri1",
                "password", "password123",
                "name", "홍길동",
                "email", "gonguri1@test.com",
                "role", "BUYER"
        );

        mockMvc.perform(post("/api/auth/signup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.memberId").value(notNullValue()))
                .andExpect(jsonPath("$.data.username").value("gonguri1"))
                .andExpect(jsonPath("$.data.role").value("BUYER"))
                .andExpect(jsonPath("$.data.password").doesNotExist());

        boolean passwordEncoded = memberRepository.findAll().stream()
                .anyMatch(member -> member.getUsername().equals("gonguri1")
                        && !member.getPassword().equals("password123"));
        assertTrue(passwordEncoded);
    }

    @Test
    @DisplayName("중복된 아이디로 가입하면 409와 DUPLICATE_USERNAME을 반환한다")
    void signup_duplicateUsername() throws Exception {
        Map<String, Object> request = Map.of(
                "username", "gonguri2",
                "password", "password123",
                "name", "홍길동",
                "email", "gonguri2@test.com",
                "role", "SELLER"
        );

        mockMvc.perform(post("/api/auth/signup")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("DUPLICATE_USERNAME"));
    }

    @Test
    @DisplayName("필수값이 비어있으면 400과 VALIDATION_FAILED를 반환한다")
    void signup_validationFailed() throws Exception {
        Map<String, Object> request = Map.of(
                "username", "",
                "password", "password123",
                "name", "홍길동",
                "email", "gonguri3@test.com",
                "role", "BUYER"
        );

        mockMvc.perform(post("/api/auth/signup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
