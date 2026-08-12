package com.gong9ri.gong9ri.controller;

import com.gong9ri.gong9ri.service.PortOneWebhookService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PortOne 웹훅 수신 엔드포인트 — PG가 직접 호출하므로 세션 인증을 요구할 수 없다({@code SecurityConfig}에서
 * permitAll). 대신 {@code PortOneWebhookService}가 서명(Standard Webhooks HMAC)을 검증하는 것이 곧
 * 이 엔드포인트의 인증 역할을 한다(docs/dev/payment/portone/design.md).
 *
 * <p>서명 검증 대상 문자열은 실제 수신 바이트 그대로여야 하므로, {@code @RequestBody}로 DTO
 * 역직렬화를 하지 않고 {@code HttpServletRequest}에서 raw body를 직접 읽는다 — 역직렬화 후 재직렬화한
 * 문자열을 쓰면 공백 등의 차이로 서명이 깨진다.
 */
@RestController
@RequestMapping("/api/webhooks/portone")
@RequiredArgsConstructor
public class PortOneWebhookController {

    private final PortOneWebhookService portOneWebhookService;

    @PostMapping
    public ResponseEntity<Void> handle(HttpServletRequest request) throws IOException {
        String rawBody = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        portOneWebhookService.handle(
                request.getHeader("webhook-id"),
                request.getHeader("webhook-timestamp"),
                request.getHeader("webhook-signature"),
                rawBody);
        return ResponseEntity.ok().build();
    }
}
