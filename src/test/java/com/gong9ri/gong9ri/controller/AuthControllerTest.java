package com.gong9ri.gong9ri.controller;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gong9ri.gong9ri.repository.MemberRepository;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

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

    @Autowired
    private StringRedisTemplate redisTemplate;

    // 로그인 시도 제한(계정 잠금 + IP) 관련 Redis 카운터는 JPA 트랜잭션 롤백 범위 밖이라 직접 정리한다.
    // 이 클래스의 테스트들은 전부 MockMvc 기본 클라이언트 IP(127.0.0.1)로 /api/auth/login을 호출하므로,
    // 매 테스트 전후로 IP 레이어 카운터도 리셋해야 테스트 간 누적으로 429가 새는 걸 막을 수 있다.
    private static final String LOGIN_IP_RATE_LIMIT_KEY = "rate-limit:login:127.0.0.1";

    @BeforeEach
    void cleanUpBeforeEach() {
        redisTemplate.delete(LOGIN_IP_RATE_LIMIT_KEY);
    }

    @AfterEach
    void cleanUpLoginAttemptKeys() {
        redisTemplate.delete(LOGIN_IP_RATE_LIMIT_KEY);
        redisTemplate.delete("login-fail:gonguri-lockout1");
        redisTemplate.delete("login-fail:gonguri-lockout2");
    }

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

    @Test
    @DisplayName("문법이 깨진 JSON 요청 본문은 500이 아니라 400 VALIDATION_FAILED를 반환한다")
    void signup_malformedJson_returnsBadRequestNotServerError() throws Exception {
        String malformedJson = "{\"username\": \"gonguri4\", \"password\": \"password123\"";

        mockMvc.perform(post("/api/auth/signup")
                        .contentType("application/json")
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private void signup(String username, String password) throws Exception {
        Map<String, Object> request = Map.of(
                "username", username,
                "password", password,
                "name", "홍길동",
                "email", username + "@test.com",
                "role", "BUYER"
        );
        mockMvc.perform(post("/api/auth/signup")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)));
    }

    @Test
    @DisplayName("가입된 회원이 정상 로그인하면 200과 회원 정보, 세션 쿠키를 반환한다")
    void login_success() throws Exception {
        signup("gonguri4", "password123");

        Map<String, Object> request = Map.of("username", "gonguri4", "password", "password123");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("gonguri4"))
                .andReturn();

        // MockMvc는 실제 서블릿 컨테이너가 아니라 Set-Cookie 헤더를 자동으로 안 만들어줘서,
        // 세션이 실제로 생성됐는지는 request의 HttpSession 존재 여부로 직접 확인한다.
        assertNotNull(result.getRequest().getSession(false));
    }

    @Test
    @DisplayName("존재하지 않는 아이디로 로그인하면 401과 LOGIN_FAILED를 반환한다")
    void login_usernameNotFound() throws Exception {
        Map<String, Object> request = Map.of("username", "no-such-user", "password", "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    @Test
    @DisplayName("비밀번호가 틀리면 401과 LOGIN_FAILED를 반환한다 (아이디 없음과 동일 응답)")
    void login_wrongPassword() throws Exception {
        signup("gonguri5", "password123");

        Map<String, Object> request = Map.of("username", "gonguri5", "password", "wrong-password");

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    @Test
    @DisplayName("로그인 필수값이 비어있으면 400과 VALIDATION_FAILED를 반환한다")
    void login_validationFailed() throws Exception {
        Map<String, Object> request = Map.of("username", "", "password", "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("로그인한 상태에서 /api/auth/me를 조회하면 200과 회원 정보를 반환한다")
    void me_success() throws Exception {
        signup("gonguri7", "password123");
        Map<String, Object> loginRequest = Map.of("username", "gonguri7", "password", "password123");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.memberId").value(notNullValue()))
                .andExpect(jsonPath("$.data.username").value("gonguri7"))
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.role").value("BUYER"));
    }

    @Test
    @DisplayName("로그인하지 않은 상태에서 /api/auth/me를 조회하면 401과 UNAUTHORIZED를 반환한다")
    void me_unauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("로그인한 상태에서 로그아웃하면 204를 반환하고 세션이 무효화된다")
    void logout_success() throws Exception {
        signup("gonguri6", "password123");
        Map<String, Object> loginRequest = Map.of("username", "gonguri6", "password", "password123");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isNoContent());

        assertTrue(session.isInvalid());
    }

    @Test
    @DisplayName("로그인하지 않은 상태에서 로그아웃하면 401과 UNAUTHORIZED를 반환한다")
    void logout_unauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("같은 계정으로 5회 연속 로그인 실패하면 6번째부터 맞는 비밀번호를 넣어도 잠긴다(LOGIN_ATTEMPTS_EXCEEDED)")
    void login_repeatedFailures_locksAccount() throws Exception {
        signup("gonguri-lockout1", "password123");
        Map<String, Object> wrongRequest = Map.of("username", "gonguri-lockout1", "password", "wrong-password");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(wrongRequest)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        }

        // 6번째부터는 맞는 비밀번호를 넣어도 계정 잠금 자체가 authenticate() 호출을 막는다.
        Map<String, Object> correctRequest = Map.of("username", "gonguri-lockout1", "password", "password123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(correctRequest)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_ATTEMPTS_EXCEEDED"));
    }

    @Test
    @DisplayName("로그인에 성공하면 실패 카운터가 리셋되어 그 뒤로는 다시 정상적으로 실패/성공을 셀 수 있다")
    void login_success_resetsFailureCounter() throws Exception {
        signup("gonguri-lockout2", "password123");
        Map<String, Object> wrongRequest = Map.of("username", "gonguri-lockout2", "password", "wrong-password");
        Map<String, Object> correctRequest = Map.of("username", "gonguri-lockout2", "password", "password123");

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(wrongRequest)))
                    .andExpect(status().isUnauthorized());
        }

        // 임계값(5회) 전에 성공 — 카운터가 리셋돼야 한다.
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(correctRequest)))
                .andExpect(status().isOk());

        // 리셋 안 됐으면 이전 실패 4회 + 이번 시도로 잠길 수 있는 경계 — 정상적으로 다시 로그인 성공해야 한다.
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(correctRequest)))
                .andExpect(status().isOk());
    }
}
