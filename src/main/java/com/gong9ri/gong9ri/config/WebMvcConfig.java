package com.gong9ri.gong9ri.config;

import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 정적 자산(css/js/images) 브라우저 캐싱 — 속도 개선(발제 범위 밖). 프로덕션 응답 헤더를 직접 확인해보니
 * (2026-08-19), Spring Security의 기본 헤더 작성기가 css/js/images 같은 정적 리소스 응답에는
 * {@code Cache-Control: no-cache, no-store, max-age=0, must-revalidate}를 붙이고 있었다(반면 html
 * 페이지는 원래도 이 헤더가 없었음 — 정적 리소스 핸들러 쪽에만 적용되는 것으로 보임, 정확한 내부 메커니즘까지
 * 확인하진 않음). 매 요청마다 브라우저가 무조건 재검증하느라 캐싱 효과가 전혀 없었던 것.
 *
 * 여기서 등록하는 리소스 핸들러가 나중에 실행돼 응답 헤더를 다시 set(추가가 아니라 덮어쓰기)하므로
 * 그 기본값을 덮어쓴다. HTML 페이지(.html)는 원래도 문제 없었으니 이 핸들러 대상에 안 넣는다. 이 프로젝트는
 * 자주 배포되는데(오늘 하루에만 여러 번) 정적 파일에 버전 붙이기(cache busting) 전략이 없어서, 오래
 * 캐싱하면 배포 직후에도 사용자가 옛 화면을 계속 보는 문제가 생긴다. 그래서 짧게(10분)만 캐싱해서 "같은
 * 세션 안 페이지 이동 시 재다운로드 방지" 정도의 실질적 이득만 취하고, 스테일 위험은 최소화한다.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final Duration STATIC_ASSET_CACHE_DURATION = Duration.ofMinutes(10);

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/css/**", "/js/**", "/images/**")
                .addResourceLocations("classpath:/static/css/", "classpath:/static/js/", "classpath:/static/images/")
                .setCacheControl(CacheControl.maxAge(STATIC_ASSET_CACHE_DURATION).cachePublic());
    }
}
