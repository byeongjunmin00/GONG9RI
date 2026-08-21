package com.gong9ri.gong9ri.common.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 서비스 공개 주소({@code app.base-url}) — <b>끝의 슬래시를 한 곳에서 떼어낸다.</b>
 *
 * <p>이 값은 이메일 인증 링크·비밀번호 재설정 링크·sitemap·robots.txt·카카오 공유 링크에 쓰인다.
 * 전부 {@code baseUrl + "/경로"} 형태로 이어붙이는데, 환경변수에 끝 슬래시가 들어가 있으면
 * {@code https://example.com//api/auth/verify-email} 처럼 슬래시가 두 개가 된다. 대부분의 서버는
 * 그래도 처리하지만, <b>카카오 공유 링크 도메인 검증이나 canonical 주소 비교처럼 문자열을 그대로
 * 보는 곳에서는 어긋난다.</b>
 *
 * <p>사람이 값을 넣는 자리라 슬래시가 붙는 건 흔한 실수다. "넣지 마세요"라고 문서에 적는 대신
 * 코드가 흡수한다 — 실제로 호스팅을 옮기며 끝에 슬래시가 붙은 값이 들어왔다(2026-08-21).
 */
@Getter
@Component
public class AppUrlProperties {

    @Value("${app.base-url}")
    private String rawBaseUrl;

    private String baseUrl;

    @PostConstruct
    void normalize() {
        this.baseUrl = rawBaseUrl == null ? "" : rawBaseUrl.trim().replaceAll("/+$", "");
    }

    /** {@code baseUrl + path} 형태로 이어붙인다. path는 {@code /}로 시작해야 한다. */
    public String url(String path) {
        return baseUrl + path;
    }
}
