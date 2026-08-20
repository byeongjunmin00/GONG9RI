package com.gong9ri.gong9ri.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

/**
 * "이 요청이 사람이 브라우저 주소창으로 들어온 것인가"를 판정한다.
 *
 * <p>에러 응답의 <b>표현만</b> 나누기 위한 것이다 — 상태 코드와 인가 규칙은 그대로 두고, 브라우저 탐색에는
 * 사람이 읽을 HTML 페이지를, 프로그램 호출에는 기존 JSON을 그대로 준다.
 *
 * <p>판정 기준은 {@code Accept} 헤더다. 브라우저가 주소창으로 문서를 요청할 때는 {@code text/html}을
 * 명시적으로 보내지만, 우리 프론트가 쓰는 {@code fetch}는 별도 지정이 없으면 모든 타입을 받겠다고 보낸다.
 * 그래서 이 기준으로 나누면 <b>기존 JS의 에러 처리(코드·메시지 파싱)는 전혀 영향을 받지 않는다</b> —
 * 이게 이 클래스의 존재 이유이자 가장 중요한 제약이다.
 */
public final class BrowserRequests {

    private BrowserRequests() {
    }

    public static boolean isBrowserNavigation(HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return accept != null && accept.contains("text/html");
    }
}
