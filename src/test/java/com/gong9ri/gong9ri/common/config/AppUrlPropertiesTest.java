package com.gong9ri.gong9ri.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@code app.base-url}의 끝 슬래시 정규화.
 *
 * <p><b>왜 중요한가</b> — 이 값으로 카카오 로그인 Redirect URI를 만든다
 * ({@code baseUrl + "/api/auth/kakao/callback"}). 끝에 슬래시가 붙으면
 * {@code https://x.app//api/auth/kakao/callback}이 되어 <b>카카오 콘솔에 등록한 주소와 문자열이
 * 어긋나 로그인이 실패한다.</b> 이메일 인증 링크·sitemap·공유 링크도 같은 방식으로 이어붙인다.
 *
 * <p>사람이 직접 넣는 환경변수라 슬래시가 붙는 건 흔한 실수다 — 실제로 호스팅을 옮기며
 * {@code https://.../} 형태가 들어왔다(2026-08-21). 문서로 당부하는 대신 코드가 흡수한다.
 */
class AppUrlPropertiesTest {

    private AppUrlProperties withRaw(String raw) {
        AppUrlProperties props = new AppUrlProperties();
        ReflectionTestUtils.setField(props, "rawBaseUrl", raw);
        ReflectionTestUtils.invokeMethod(props, "normalize");
        return props;
    }

    @Test
    @DisplayName("끝 슬래시를 떼어낸다 — 붙어 있어도 카카오 콜백 주소가 어긋나지 않게")
    void stripsTrailingSlash() {
        assertEquals("https://gong9ri.up.railway.app",
                withRaw("https://gong9ri.up.railway.app/").getBaseUrl());
        assertEquals("https://gong9ri.up.railway.app/api/auth/kakao/callback",
                withRaw("https://gong9ri.up.railway.app/").url("/api/auth/kakao/callback"));
    }

    @Test
    @DisplayName("슬래시가 여러 개여도, 앞뒤 공백이 있어도 처리한다")
    void handlesMultipleSlashesAndWhitespace() {
        assertEquals("https://x.app", withRaw("https://x.app///").getBaseUrl());
        assertEquals("https://x.app", withRaw("  https://x.app/  ").getBaseUrl());
    }

    @Test
    @DisplayName("슬래시가 없으면 그대로 둔다 — 정상값을 건드리지 않는다")
    void keepsNormalValueUnchanged() {
        assertEquals("https://x.app", withRaw("https://x.app").getBaseUrl());
        assertEquals("http://localhost:8080/product.html?id=1",
                withRaw("http://localhost:8080").url("/product.html?id=1"));
    }
}
