package com.gong9ri.gong9ri.common.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 브라우저로 들어온 404/401은 사람이 읽을 에러 페이지로, 프로그램 호출은 기존 JSON으로 나가는지 검증한다
 * (docs/dev/frontend/error-page/design.md).
 *
 * <p>예전에는 주소창에 오타를 내고 들어오면 브라우저에
 * {@code {"success":false,"code":"UNAUTHORIZED","message":"로그인이 필요합니다."}} 같은 날 JSON이 그대로
 * 보였고, 없는 주소인데 "로그인이 필요합니다"라고 안내하는 건 사실과 달랐다(2026-08-20 수정).
 *
 * <p><b>이 테스트에서 제일 중요한 건 "JSON 응답이 그대로인지"</b>다 — 프론트 전체가 에러 코드·메시지를
 * 파싱해서 분기하고 있어서, 응답 형태가 바뀌면 로그인 배너·결제 실패 안내 등이 한꺼번에 망가진다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ErrorPageResponseTest {

    private static final String BROWSER_ACCEPT =
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8";
    /** 브라우저 fetch()가 Accept를 지정하지 않았을 때 보내는 값. */
    private static final String FETCH_ACCEPT = "*/*";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("브라우저로 없는 .html 주소에 들어가면 에러 페이지로 보낸다")
    void browser_notFoundHtml_redirectsToErrorPage() throws Exception {
        mockMvc.perform(get("/존재하지-않는-페이지.html").header(HttpHeaders.ACCEPT, BROWSER_ACCEPT))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error.html"));
    }

    @Test
    @DisplayName("브라우저로 인증이 필요한 주소에 들어가면 에러 페이지로 보낸다(상태 노출 없이)")
    void browser_unauthorized_redirectsToErrorPage() throws Exception {
        mockMvc.perform(get("/nonexistent-path").header(HttpHeaders.ACCEPT, BROWSER_ACCEPT))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.LOCATION, "/error.html"));
    }

    @Test
    @DisplayName("[회귀 방지] fetch 호출은 404에서 기존 JSON(NOT_FOUND)을 그대로 받는다")
    void fetch_notFound_stillReturnsJson() throws Exception {
        mockMvc.perform(get("/존재하지-않는-페이지.html").header(HttpHeaders.ACCEPT, FETCH_ACCEPT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("[회귀 방지] fetch 호출은 401에서 기존 JSON(UNAUTHORIZED)을 그대로 받는다")
    void fetch_unauthorized_stillReturnsJson() throws Exception {
        // 프론트 전체가 이 code 값으로 로그인 배너 노출 여부를 판단한다(buyer-mypage.js, checkout.js 등).
        mockMvc.perform(get("/api/buyer/mypage/notifications").header(HttpHeaders.ACCEPT, FETCH_ACCEPT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("[회귀 방지] Accept 헤더가 아예 없는 호출도 JSON을 받는다")
    void noAcceptHeader_stillReturnsJson() throws Exception {
        mockMvc.perform(get("/api/buyer/mypage/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("에러 페이지 자체는 비로그인으로도 열려야 한다")
    void errorPage_isPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/error.html").header(HttpHeaders.ACCEPT, BROWSER_ACCEPT))
                .andExpect(status().isOk());
    }
}
