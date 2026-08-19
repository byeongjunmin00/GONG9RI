package com.gong9ri.gong9ri.controller;

import com.gong9ri.gong9ri.service.SitemapService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SEO(발제 범위 밖) — sitemap.xml만 별도 컨트롤러로 둔다. 다른 API처럼 공통 응답 형식
 * ({@link com.gong9ri.gong9ri.common.response.ApiResponse})을 쓰지 않는다 — 이건 검색엔진 크롤러가
 * 읽는 표준 XML 규격(sitemaps.org)이라 그 형식 그대로 응답해야 한다.
 */
@RestController
@RequiredArgsConstructor
public class SitemapController {

    private final SitemapService sitemapService;

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        return ResponseEntity.ok(sitemapService.buildSitemapXml());
    }
}
