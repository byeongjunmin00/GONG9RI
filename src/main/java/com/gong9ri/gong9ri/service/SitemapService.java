package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.repository.ProductRepository;
import java.time.format.DateTimeFormatter;
import java.util.List;
import com.gong9ri.gong9ri.common.config.AppUrlProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 검색엔진 크롤러용 sitemap.xml 생성 — SEO(발제 범위 밖). 정적 페이지 몇 개 + 상품 상세(product.html?id=)를
 * 전부 나열한다. 상품이 많아지면(수만 건 단위) 하나의 sitemap이 5만 URL 상한에 걸릴 수 있는데, 지금
 * 규모(데모/부트캠프 프로젝트)에서는 그 상한을 신경 쓸 필요가 없어 sitemap 인덱스 파일 분할은 하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class SitemapService {

    private final ProductRepository productRepository;
    private final AppUrlProperties appUrl;

    private static final DateTimeFormatter LASTMOD_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    public String buildSitemapXml() {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        appendStaticUrl(xml, "/", "daily", "1.0");
        appendStaticUrl(xml, "/login.html", "monthly", "0.3");
        appendStaticUrl(xml, "/signup.html", "monthly", "0.3");

        List<Product> products = productRepository.findAll();
        for (Product product : products) {
            String loc = appUrl.url("/product.html?id=" + product.getId());
            xml.append("  <url>\n");
            xml.append("    <loc>").append(escapeXml(loc)).append("</loc>\n");
            if (product.getUpdatedAt() != null) {
                xml.append("    <lastmod>").append(product.getUpdatedAt().format(LASTMOD_FORMAT)).append("</lastmod>\n");
            }
            xml.append("    <changefreq>weekly</changefreq>\n");
            xml.append("    <priority>0.8</priority>\n");
            xml.append("  </url>\n");
        }

        xml.append("</urlset>\n");
        return xml.toString();
    }

    private void appendStaticUrl(StringBuilder xml, String path, String changefreq, String priority) {
        xml.append("  <url>\n");
        xml.append("    <loc>").append(escapeXml(appUrl.url(path))).append("</loc>\n");
        xml.append("    <changefreq>").append(changefreq).append("</changefreq>\n");
        xml.append("    <priority>").append(priority).append("</priority>\n");
        xml.append("  </url>\n");
    }

    // URL 자체에 XML 특수문자가 낄 일은 사실상 없지만(숫자 id, 고정 경로), 원칙대로 이스케이프한다.
    private String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
