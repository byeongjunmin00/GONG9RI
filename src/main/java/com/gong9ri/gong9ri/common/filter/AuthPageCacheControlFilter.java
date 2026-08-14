package com.gong9ri.gong9ri.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 로그인이 필요한 정적 페이지(구매자/판매자 마이페이지, 상품 등록/수정, 결제)에 {@code Cache-Control:
 * no-store}를 붙여 브라우저 뒤로가기 캐시(bfcache)에 남지 않게 한다.
 * <p>이 페이지들은 서버가 직접 인증을 강제하지 않고(정적 리소스라 {@code SecurityConfig}에서 permitAll)
 * 클라이언트 JS(js/header-auth.js 등)가 인증 필요 API(401)를 호출해서 로그인 여부를 판단하는 구조다 —
 * 그래서 로그아웃 후 뒤로가기로 bfcache에 저장된 이전 페이지(이미 로그인 상태로 렌더링된 개인 데이터
 * 포함)가 그대로 보이면, 페이지의 JS가 다시 실행되지 않아 로그아웃 여부를 재확인하지 못한다.
 * {@code no-store}는 최신 브라우저(Chrome/Firefox)가 해당 페이지를 bfcache 대상에서 제외시키는
 * 신호로도 쓰여, 뒤로가기 시 항상 새로 로드되게 만든다.
 */
@Component
public class AuthPageCacheControlFilter extends OncePerRequestFilter {

    private static final Set<String> NO_STORE_PATHS = Set.of(
            "/buyer/mypage.html",
            "/seller/mypage.html",
            "/seller/products/new.html",
            "/seller/products/edit.html",
            "/checkout.html"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (NO_STORE_PATHS.contains(request.getRequestURI())) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        }
        filterChain.doFilter(request, response);
    }
}
