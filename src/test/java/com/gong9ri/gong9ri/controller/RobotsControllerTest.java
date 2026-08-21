package com.gong9ri.gong9ri.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * robots.txt가 <b>배포 주소를 따라가는지</b> 고정한다.
 *
 * <p>예전에는 정적 파일에 프로덕션 주소가 하드코딩돼 있었다. 호스팅을 옮기면 검색엔진에 없는 주소의
 * 사이트맵을 알려주게 되는데, <b>화면에는 아무 증상도 안 나타나서</b> 바뀐 걸 알아채기 어렵다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RobotsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("robots.txt의 사이트맵 주소가 설정된 base-url을 따라간다")
    void robots_usesConfiguredBaseUrl() throws Exception {
        // 테스트 설정의 app.base-url은 http://localhost:8080 이다.
        mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Sitemap: http://localhost:8080/sitemap.xml")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("User-agent: *")));
    }

    @Test
    @DisplayName("[회귀 방지] robots.txt에 특정 배포 주소가 박혀 있으면 안 된다")
    void robots_hasNoHardcodedHost() throws Exception {
        String body = mockMvc.perform(get("/robots.txt"))
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertFalse(body.contains("railway.app"),
                "배포 주소가 하드코딩되면 호스팅을 옮겼을 때 조용히 틀린 주소를 알려주게 된다");
    }
}
