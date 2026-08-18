package com.gong9ri.gong9ri.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gong9ri.gong9ri.common.mail.EmailSender;
import com.gong9ri.gong9ri.common.security.TokenService;
import com.gong9ri.gong9ri.repository.MemberRepository;
import java.time.Duration;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

    @Autowired
    private TokenService tokenService;

    // 이메일 인증/비밀번호 재설정 테스트가 실제 SendGrid API로 나가지 않도록 목으로 대체한다
    // (CI가 실제 외부 서비스 연결 가능 여부에 의존하면 안 되므로).
    @MockitoBean
    private EmailSender emailSender;

    // 로그인 시도 제한(계정 잠금 + IP) + 이메일 인증/재발송 + 비밀번호 재설정 요청 관련 Redis 카운터는
    // JPA 트랜잭션 롤백 범위 밖이라 직접 정리한다. 이 클래스의 테스트들은 전부 MockMvc 기본 클라이언트
    // IP(127.0.0.1)로 요청하므로, 매 테스트 전후로 이 카운터들을 리셋해야 테스트 간 누적으로 429가
    // 새는 걸 막을 수 있다(실제로 verify-email-resend/password-reset-request 키를 안 지웠다가
    // 연속으로 전체 테스트를 돌렸을 때 429로 깨지는 걸 겪고 추가했다).
    private static final String LOGIN_IP_RATE_LIMIT_KEY = "rate-limit:login:127.0.0.1";
    private static final String VERIFY_EMAIL_RESEND_IP_RATE_LIMIT_KEY = "rate-limit:verify-email-resend:127.0.0.1";
    private static final String PASSWORD_RESET_REQUEST_IP_RATE_LIMIT_KEY = "rate-limit:password-reset-request:127.0.0.1";

    @BeforeEach
    void cleanUpBeforeEach() {
        redisTemplate.delete(LOGIN_IP_RATE_LIMIT_KEY);
        redisTemplate.delete(VERIFY_EMAIL_RESEND_IP_RATE_LIMIT_KEY);
        redisTemplate.delete(PASSWORD_RESET_REQUEST_IP_RATE_LIMIT_KEY);
    }

    @AfterEach
    void cleanUpLoginAttemptKeys() {
        redisTemplate.delete(LOGIN_IP_RATE_LIMIT_KEY);
        redisTemplate.delete(VERIFY_EMAIL_RESEND_IP_RATE_LIMIT_KEY);
        redisTemplate.delete(PASSWORD_RESET_REQUEST_IP_RATE_LIMIT_KEY);
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

    // 이메일 인증(로그인 고도화 2단계) 도입 이후 실제 가입 직후엔 로그인이 막힌다. 이 헬퍼를 쓰는
    // 기존 테스트들은 이메일 인증 자체가 관심사가 아니라 "가입된 회원으로 로그인이 되는지" 그 이후
    // 흐름을 보는 것들이라, 가입 직후 바로 인증 완료 상태로 만들어준다(이메일 인증 자체는 별도
    // 테스트에서 검증). AFTER_COMMIT 이벤트(메일 발송)는 이 클래스가 @Transactional이라 실제로
    // 커밋되지 않아 어차피 발동하지 않는다.
    private void signup(String username, String password) throws Exception {
        signupWithoutVerifying(username, password);
        memberRepository.findByUsername(username).ifPresent(member -> {
            member.verifyEmail();
            memberRepository.save(member);
        });
    }

    private void signupWithoutVerifying(String username, String password) throws Exception {
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

    private MockHttpSession loginAndGetSession(String username, String password) throws Exception {
        Map<String, Object> loginRequest = Map.of("username", username, "password", password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    @DisplayName("정보수정: 이름만 바꾸면 200이고 이메일 인증 상태는 그대로 유지된다")
    void updateMe_success_nameOnly() throws Exception {
        signup("gonguri8", "password123");
        MockHttpSession session = loginAndGetSession("gonguri8", "password123");

        Map<String, Object> request = Map.of("name", "새이름", "email", "gonguri8@test.com");

        mockMvc.perform(patch("/api/auth/me")
                        .session(session)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("새이름"))
                .andExpect(jsonPath("$.data.email").value("gonguri8@test.com"))
                .andExpect(jsonPath("$.data.emailVerified").value(true));

        // 세션의 SecurityContext가 실제로 갱신됐는지 — 같은 세션으로 다시 조회해도 새 값이 보여야 한다.
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("새이름"));
    }

    @Test
    @DisplayName("정보수정: 이메일을 바꾸면 200이고 이메일 인증 상태가 false로 초기화된다")
    void updateMe_success_emailChanged() throws Exception {
        signup("gonguri9", "password123");
        MockHttpSession session = loginAndGetSession("gonguri9", "password123");

        Map<String, Object> request = Map.of("name", "홍길동", "email", "gonguri9-new@test.com");

        mockMvc.perform(patch("/api/auth/me")
                        .session(session)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("gonguri9-new@test.com"))
                .andExpect(jsonPath("$.data.emailVerified").value(false));
    }

    @Test
    @DisplayName("정보수정: 기존과 동일한 이메일로 제출하면(이름만 실질적으로 바뀜) 인증 상태가 초기화되지 않는다")
    void updateMe_sameEmail_emailVerifiedUnchanged() throws Exception {
        signup("gonguri10", "password123");
        MockHttpSession session = loginAndGetSession("gonguri10", "password123");

        Map<String, Object> request = Map.of("name", "새이름", "email", "gonguri10@test.com");

        mockMvc.perform(patch("/api/auth/me")
                        .session(session)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emailVerified").value(true));
    }

    @Test
    @DisplayName("정보수정: 다른 회원이 이미 쓰는 이메일로 바꾸려 하면 409 DUPLICATE_EMAIL")
    void updateMe_duplicateEmail() throws Exception {
        signup("gonguri11", "password123");
        signup("gonguri12", "password123");
        MockHttpSession session = loginAndGetSession("gonguri12", "password123");

        Map<String, Object> request = Map.of("name", "홍길동", "email", "gonguri11@test.com");

        mockMvc.perform(patch("/api/auth/me")
                        .session(session)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    @DisplayName("정보수정: 이름이 비어있으면 400 VALIDATION_FAILED")
    void updateMe_validationFailed() throws Exception {
        signup("gonguri13", "password123");
        MockHttpSession session = loginAndGetSession("gonguri13", "password123");

        Map<String, Object> request = Map.of("name", "", "email", "gonguri13@test.com");

        mockMvc.perform(patch("/api/auth/me")
                        .session(session)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("정보수정: 로그인하지 않은 상태면 401 UNAUTHORIZED")
    void updateMe_unauthorized() throws Exception {
        Map<String, Object> request = Map.of("name", "홍길동", "email", "someone@test.com");

        mockMvc.perform(patch("/api/auth/me")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
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
    @DisplayName("로그아웃 후 같은 세션으로 인증이 필요한 API를 다시 호출하면 401을 반환한다")
    void logout_thenReusingSameSession_isUnauthorized() throws Exception {
        signup("gonguri-logout1", "password123");
        Map<String, Object> loginRequest = Map.of("username", "gonguri-logout1", "password", "password123");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
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

    @Test
    @DisplayName("이미 가입된 이메일로 다른 아이디로 가입하면 409와 DUPLICATE_EMAIL을 반환한다")
    void signup_duplicateEmail() throws Exception {
        Map<String, Object> firstRequest = Map.of(
                "username", "gonguri-dupemail1",
                "password", "password123",
                "name", "홍길동",
                "email", "dup-email@test.com",
                "role", "BUYER"
        );
        mockMvc.perform(post("/api/auth/signup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isCreated());

        Map<String, Object> secondRequest = Map.of(
                "username", "gonguri-dupemail2",
                "password", "password123",
                "name", "홍길동",
                "email", "dup-email@test.com",
                "role", "BUYER"
        );
        mockMvc.perform(post("/api/auth/signup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(secondRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    @DisplayName("이메일 인증을 하지 않은 계정으로 로그인하면 403과 EMAIL_NOT_VERIFIED를 반환한다")
    void login_beforeEmailVerification_isBlocked() throws Exception {
        signupWithoutVerifying("gonguri-unverified", "password123");

        Map<String, Object> request = Map.of("username", "gonguri-unverified", "password", "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    @DisplayName("role=ADMIN으로 공개 회원가입을 시도하면 400과 VALIDATION_FAILED를 반환한다")
    void signup_adminRole_validationFailed() throws Exception {
        Map<String, Object> request = Map.of(
                "username", "gonguri-wannabe-admin",
                "password", "password123",
                "name", "홍길동",
                "email", "gonguri-wannabe-admin@test.com",
                "role", "ADMIN"
        );

        mockMvc.perform(post("/api/auth/signup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertTrue(memberRepository.findByUsername("gonguri-wannabe-admin").isEmpty());
    }

    @Test
    @DisplayName("관리자가 정지시킨 계정으로 로그인하면 403과 ACCOUNT_SUSPENDED를 반환한다")
    void login_suspendedAccount_isBlocked() throws Exception {
        signup("gonguri-suspended", "password123");
        memberRepository.findByUsername("gonguri-suspended").ifPresent(member -> {
            member.suspend();
            memberRepository.save(member);
        });

        Map<String, Object> request = Map.of("username", "gonguri-suspended", "password", "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
    }

    @Test
    @DisplayName("유효한 인증 토큰으로 GET /verify-email을 호출하면 인증이 완료되고 이후 로그인할 수 있다")
    void verifyEmail_success() throws Exception {
        signupWithoutVerifying("gonguri-verify1", "password123");
        Long memberId = memberRepository.findByUsername("gonguri-verify1").orElseThrow().getId();
        String token = tokenService.issue("email-verify", memberId, Duration.ofHours(24));

        mockMvc.perform(get("/api/auth/verify-email").param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("이메일 인증이 완료됐습니다")));

        Map<String, Object> loginRequest = Map.of("username", "gonguri-verify1", "password", "password123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("유효하지 않은 토큰으로 GET /verify-email을 호출하면 400을 반환한다")
    void verifyEmail_invalidToken() throws Exception {
        mockMvc.perform(get("/api/auth/verify-email").param("token", "no-such-token"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("유효하지 않거나 만료된 링크")));
    }

    @Test
    @DisplayName("인증 토큰은 1회 사용 후 재사용할 수 없다")
    void verifyEmail_tokenIsSingleUse() throws Exception {
        signupWithoutVerifying("gonguri-verify2", "password123");
        Long memberId = memberRepository.findByUsername("gonguri-verify2").orElseThrow().getId();
        String token = tokenService.issue("email-verify", memberId, Duration.ofHours(24));

        mockMvc.perform(get("/api/auth/verify-email").param("token", token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/verify-email").param("token", token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("인증 메일 재발송은 계정 존재 여부와 무관하게 항상 동일한 성공 응답을 반환한다")
    void resendVerificationEmail_alwaysReturnsGenericSuccess() throws Exception {
        signupWithoutVerifying("gonguri-resend1", "password123");

        mockMvc.perform(post("/api/auth/verify-email/resend")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("username", "gonguri-resend1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/auth/verify-email/resend")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("username", "no-such-username"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("비밀번호 재설정 요청은 이메일 존재 여부와 무관하게 항상 동일한 성공 응답을 반환한다")
    void requestPasswordReset_alwaysReturnsGenericSuccess() throws Exception {
        signup("gonguri-pwreset1", "password123");

        mockMvc.perform(post("/api/auth/password/reset-request")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("email", "gonguri-pwreset1@test.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/auth/password/reset-request")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("email", "no-such-email@test.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("유효한 재설정 토큰으로 비밀번호를 바꾸면 새 비밀번호로만 로그인할 수 있다")
    void resetPassword_success() throws Exception {
        signup("gonguri-pwreset2", "password123");
        Long memberId = memberRepository.findByUsername("gonguri-pwreset2").orElseThrow().getId();
        String token = tokenService.issue("password-reset", memberId, Duration.ofMinutes(30));

        mockMvc.perform(post("/api/auth/password/reset")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                Map.of("token", token, "newPassword", "new-password456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "gonguri-pwreset2", "password", "password123"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "gonguri-pwreset2", "password", "new-password456"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("유효하지 않은 토큰으로 비밀번호 재설정을 시도하면 400과 INVALID_OR_EXPIRED_TOKEN을 반환한다")
    void resetPassword_invalidToken() throws Exception {
        mockMvc.perform(post("/api/auth/password/reset")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                Map.of("token", "no-such-token", "newPassword", "new-password456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_OR_EXPIRED_TOKEN"));
    }

    @Test
    @DisplayName("재설정 토큰은 1회 사용 후 재사용할 수 없다")
    void resetPassword_tokenIsSingleUse() throws Exception {
        signup("gonguri-pwreset3", "password123");
        Long memberId = memberRepository.findByUsername("gonguri-pwreset3").orElseThrow().getId();
        String token = tokenService.issue("password-reset", memberId, Duration.ofMinutes(30));

        mockMvc.perform(post("/api/auth/password/reset")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                Map.of("token", token, "newPassword", "new-password456"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/password/reset")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                Map.of("token", token, "newPassword", "another-password789"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OR_EXPIRED_TOKEN"));
    }
}
