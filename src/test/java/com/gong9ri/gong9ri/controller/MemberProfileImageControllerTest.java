package com.gong9ri.gong9ri.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.repository.MemberRepository;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MemberProfileImageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.upload.dir}")
    private String uploadDir;

    private Member saveMember(String username) {
        Member member = new Member(username, "encoded-password", "테스트회원", username + "@test.com", Role.BUYER);
        return memberRepository.save(member);
    }

    private RequestPostProcessor asUser(Member member) {
        MemberUserDetails principal = new MemberUserDetails(member);
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return authentication(token);
    }

    // asUser()는 요청 1건에만 인증을 주입할 뿐 실제 HttpSession을 만들지 않는다 — 세션에 걸쳐
    // SecurityContext가 실제로 갱신되는지(재로그인 없이 GET /api/auth/me가 새 값을 보는지) 보려면
    // 진짜 로그인 세션이 필요해서, 회원가입+이메일 인증+로그인을 거쳐 MockHttpSession을 받는다
    // (AuthControllerTest.loginAndGetSession()과 동일한 패턴).
    private MockHttpSession signupAndLogin(String username, String password) throws Exception {
        Map<String, Object> signupRequest = Map.of(
                "username", username, "password", password, "name", "리뷰회원",
                "email", username + "@test.com", "role", "BUYER");
        mockMvc.perform(post("/api/auth/signup")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(signupRequest)));
        memberRepository.findByUsername(username).ifPresent(member -> {
            member.verifyEmail();
            memberRepository.save(member);
        });

        Map<String, Object> loginRequest = Map.of("username", username, "password", password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private byte[] createValidJpegBytes() throws Exception {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 100, 100);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("회원이 정상적인 프로필 이미지 파일을 업로드하면 profileImageUrl이 변경되고 200 OK를 반환한다")
    void uploadProfileImage_success() throws Exception {
        Member member = saveMember("profileUser1");
        byte[] imageBytes = createValidJpegBytes();
        MockMultipartFile file = new MockMultipartFile("file", "profile.jpg", "image/jpeg", imageBytes);

        mockMvc.perform(multipart("/api/member/profile-image")
                        .file(file)
                        .with(asUser(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.profileImageUrl").exists());
    }

    @Test
    @DisplayName("회원이 프로필 이미지를 삭제하면 profileImageUrl이 null로 변경된다")
    void deleteProfileImage_success() throws Exception {
        Member member = saveMember("profileUser2");
        member.updateProfileImage("/uploads/test.jpg");
        memberRepository.save(member);

        mockMvc.perform(delete("/api/member/profile-image")
                        .with(asUser(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.profileImageUrl").isEmpty());
    }

    @Test
    @DisplayName("프로필 이미지를 다시 업로드하면 이전 이미지 파일이 디스크에서 삭제된다")
    // 이 테스트만 클래스의 @Transactional에서 빠진다. 옛 파일 삭제는 **커밋 이후**에 일어나는데
    // (롤백 시 파일만 사라지는 상태를 막기 위해, MemberService.deleteFileAfterCommit 참고),
    // 롤백되는 테스트 트랜잭션 안에서는 커밋이 없어 삭제가 영영 실행되지 않는다.
    // 롤백이 없으므로 만든 데이터는 끝에서 직접 치운다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void uploadProfileImage_replacingDeletesOldFile() throws Exception {
        Member member = saveMember("profileUser3");

        MockMultipartFile firstFile = new MockMultipartFile("file", "first.jpg", "image/jpeg", createValidJpegBytes());
        MvcResult firstResult = mockMvc.perform(multipart("/api/member/profile-image")
                        .file(firstFile)
                        .with(asUser(member)))
                .andExpect(status().isOk())
                .andReturn();
        String firstUrl = objectMapper.readTree(firstResult.getResponse().getContentAsString())
                .get("data").get("profileImageUrl").asText();
        Path firstSaved = Path.of(uploadDir).resolve(firstUrl.substring("/uploads/".length()));
        assertTrue(Files.exists(firstSaved), "첫 업로드 파일은 디스크에 존재해야 한다");

        MockMultipartFile secondFile = new MockMultipartFile("file", "second.jpg", "image/jpeg", createValidJpegBytes());
        mockMvc.perform(multipart("/api/member/profile-image")
                        .file(secondFile)
                        .with(asUser(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImageUrl").value(org.hamcrest.Matchers.not(firstUrl)));

        assertFalse(Files.exists(firstSaved), "두 번째 업로드 후엔 첫 번째 파일이 지워져야 한다");

        // 롤백이 없으므로 직접 정리한다(남은 두 번째 파일 포함).
        memberRepository.findById(member.getId())
                .ifPresent(m -> {
                    if (m.getProfileImageUrl() != null) {
                        try {
                            Files.deleteIfExists(Path.of(uploadDir)
                                    .resolve(m.getProfileImageUrl().substring("/uploads/".length())));
                        } catch (Exception ignored) {
                            // 정리 실패는 테스트 결과와 무관하다.
                        }
                    }
                    memberRepository.delete(m);
                });
    }

    @Test
    @DisplayName("프로필 사진 업로드 직후 같은 세션에서 GET /api/auth/me를 호출해도 새 profileImageUrl이 바로 보인다(재로그인 불필요)")
    void uploadProfileImage_refreshesSessionPrincipalImmediately() throws Exception {
        MockHttpSession session = signupAndLogin("profileUser4", "Test1234!");
        MockMultipartFile file = new MockMultipartFile("file", "profile.jpg", "image/jpeg", createValidJpegBytes());

        MvcResult uploadResult = mockMvc.perform(multipart("/api/member/profile-image")
                        .file(file)
                        .session(session))
                .andExpect(status().isOk())
                .andReturn();
        String uploadedUrl = objectMapper.readTree(uploadResult.getResponse().getContentAsString())
                .get("data").get("profileImageUrl").asText();

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImageUrl").value(uploadedUrl));
    }

    @Test
    @DisplayName("프로필 사진 삭제 직후 같은 세션에서 GET /api/auth/me를 호출해도 profileImageUrl이 바로 null로 보인다(재로그인 불필요)")
    void deleteProfileImage_refreshesSessionPrincipalImmediately() throws Exception {
        MockHttpSession session = signupAndLogin("profileUser5", "Test1234!");
        MockMultipartFile file = new MockMultipartFile("file", "profile.jpg", "image/jpeg", createValidJpegBytes());
        mockMvc.perform(multipart("/api/member/profile-image").file(file).session(session))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/member/profile-image").session(session))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImageUrl").isEmpty());
    }

    @Test
    @DisplayName("비로그인으로 프로필 이미지를 업로드하면 401 UNAUTHORIZED")
    void uploadProfileImage_unauthorized() throws Exception {
        byte[] imageBytes = createValidJpegBytes();
        MockMultipartFile file = new MockMultipartFile("file", "profile.jpg", "image/jpeg", imageBytes);

        mockMvc.perform(multipart("/api/member/profile-image").file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("비로그인으로 프로필 이미지를 삭제하면 401 UNAUTHORIZED")
    void deleteProfileImage_unauthorized() throws Exception {
        mockMvc.perform(delete("/api/member/profile-image"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
