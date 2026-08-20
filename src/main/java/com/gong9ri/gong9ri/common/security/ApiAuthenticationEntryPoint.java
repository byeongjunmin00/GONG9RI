package com.gong9ri.gong9ri.common.security;

import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.common.response.ApiResponse;
import com.gong9ri.gong9ri.common.web.BrowserRequests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Spring Security 기본 401 응답은 우리 공통 응답 형식({success,code,message})과 다르다.
 * 인증 실패도 다른 에러 응답과 동일한 형식으로 내려주기 위해 직접 구현한다.
 */
@Component
@RequiredArgsConstructor
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    /** 브라우저 탐색일 때 보여줄 에러 페이지 — 상태 코드는 그대로 401이고 표현만 HTML로 바뀐다. */
    private static final String ERROR_PAGE = "/error.html";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        // 사람이 주소창으로 들어온 경우 — 날 JSON 대신 에러 페이지를 보여준다. 인가 규칙은 건드리지
        // 않으므로 어떤 경로가 실재하는지도 그대로 감춰진다(없는 주소든 권한 없는 주소든 같은 화면).
        // 오타로 들어온 사용자에게 "로그인이 필요합니다"만 띄우던 잘못된 안내를 고치기 위함이다
        // (2026-08-20, docs/dev/frontend/error-page/design.md).
        if (BrowserRequests.isBrowserNavigation(request)) {
            response.sendRedirect(ERROR_PAGE);
            return;
        }

        response.setStatus(ErrorCode.UNAUTHORIZED.getHttpStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        ApiResponse<Void> body = ApiResponse.failure(ErrorCode.UNAUTHORIZED.name(), ErrorCode.UNAUTHORIZED.getMessage());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
