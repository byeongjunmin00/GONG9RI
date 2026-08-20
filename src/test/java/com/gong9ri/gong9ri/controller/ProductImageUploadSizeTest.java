package com.gong9ri.gong9ri.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.repository.MemberRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * 용량 초과 업로드가 500이 아니라 400으로 나가는지 고정한다 (product/image).
 *
 * <p><b>왜 MockMvc가 아니라 실제 톰캣을 띄우는가</b> — 용량 제한은 톰캣 multipart 파서가 강제하는데,
 * MockMvc는 그 파서를 타지 않아 제한이 아예 적용되지 않는다. 즉 MockMvc로 짠 테스트는 핸들러가
 * 없어도 그냥 통과해버려서 회귀를 못 잡는다. 실제로 5.5MB 사진을 올려 500을 재현한 뒤 이 테스트를
 * 만들었다(2026-08-20).
 *
 * <p>인증도 진짜로 해야 한다 — 비로그인 상태에서는 시큐리티가 먼저 401로 끊어서 파서까지 가지 않는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductImageUploadSizeTest {

    /** application.yaml의 max-file-size(5MB)를 확실히 넘기는 크기. */
    private static final int OVERSIZED_BYTES = 6 * 1024 * 1024;

    // 부트 4는 TestRestTemplate 빈을 자동 등록하지 않는다 — 직접 만들고 절대 URL로 호출한다.
    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @LocalServerPort
    private int port;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long memberIdToClean;

    @AfterEach
    void cleanUp() {
        if (memberIdToClean != null) {
            memberRepository.deleteById(memberIdToClean);
            memberIdToClean = null;
        }
    }

    @Test
    @DisplayName("5MB를 넘는 파일을 올리면 500이 아니라 400(IMAGE_FILE_TOO_LARGE)으로 거절한다")
    void oversizedUpload_returnsBadRequestNotServerError() {
        String cookie = loginAsSeller();

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(new byte[OVERSIZED_BYTES]) {
            @Override
            public String getFilename() {
                return "huge.jpg";
            }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.add(HttpHeaders.COOKIE, cookie);

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/api/seller/products/images"), new HttpEntity<>(body, headers), String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                "용량 초과는 사용자 입력 문제다 — 서버 오류(500)로 나가면 안 된다");
        assertTrue(response.getBody() != null && response.getBody().contains("IMAGE_FILE_TOO_LARGE"),
                "프론트가 안내 문구를 띄울 수 있게 에러 코드를 줘야 한다: " + response.getBody());
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /** 로그인 세션 쿠키를 얻는다 — 파서까지 도달하려면 인증을 통과해야 한다. */
    private String loginAsSeller() {
        Member seller = new Member("upload-size-seller", passwordEncoder.encode("password123!"),
                "업로드판매자", "upload-size-seller@test.com", Role.SELLER);
        seller.verifyEmail();
        memberIdToClean = memberRepository.save(seller).getId();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> login = restTemplate.postForEntity(url("/api/auth/login"),
                new HttpEntity<>("{\"username\":\"upload-size-seller\",\"password\":\"password123!\"}", headers),
                String.class);

        assertEquals(HttpStatus.OK, login.getStatusCode(), "로그인이 되어야 업로드 경로를 검증할 수 있다");
        List<String> cookies = login.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertNotNull(cookies, "세션 쿠키가 내려와야 한다");
        return cookies.get(0).split(";", 2)[0];
    }
}
