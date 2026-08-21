package com.gong9ri.gong9ri.controller;

import com.gong9ri.gong9ri.common.config.AppUrlProperties;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * robots.txt — 사이트맵 주소가 배포 주소를 따라가야 해서 정적 파일 대신 여기서 만든다.
 *
 * <p>예전에는 {@code static/robots.txt}에 프로덕션 주소가 <b>하드코딩</b>돼 있었다. 배포 주소가
 * 바뀌면(호스팅 이전 등) 검색엔진에 없는 주소의 사이트맵을 알려주게 되는데, 그건 화면에 아무 증상도
 * 안 나타나서 <b>바뀐 걸 알아채기 어렵다</b>. {@code SitemapService}가 이미 {@code app.base-url}을
 * 쓰고 있어 같은 기준으로 맞춘다(2026-08-21).
 */
@RestController
public class RobotsController {

    private final AppUrlProperties appUrl;

    public RobotsController(AppUrlProperties appUrl) {
        this.appUrl = appUrl;
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> robots() {
        String body = """
                User-agent: *
                Allow: /

                Sitemap: %s
                """.formatted(appUrl.url("/sitemap.xml"));
        return ResponseEntity.ok(body);
    }
}
