package com.gong9ri.gong9ri.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PortOne 웹훅 서명(Standard Webhooks HMAC-SHA256) 검증 순수 단위 테스트 — 실제 네트워크·Spring
 * 컨텍스트 없이 알고리즘 자체를 검증한다(docs/dev/payment/portone/design.md).
 */
class PortOneWebhookVerifierTest {

    // whsec_ 접두사를 뗀 나머지를 base64 디코드하면 실제 HMAC 키가 나온다 — "test-secret-key-bytes"를 인코딩.
    private static final String WEBHOOK_SECRET = "whsec_" + Base64.getEncoder()
            .encodeToString("test-secret-key-bytes".getBytes(StandardCharsets.UTF_8));

    private final PortOneWebhookVerifier verifier = new PortOneWebhookVerifier(WEBHOOK_SECRET);

    private String computeValidSignatureHeader(String webhookId, String webhookTimestamp, String rawBody) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(WEBHOOK_SECRET.substring("whsec_".length()));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
            byte[] signature = mac.doFinal((webhookId + "." + webhookTimestamp + "." + rawBody)
                    .getBytes(StandardCharsets.UTF_8));
            return "v1," + Base64.getEncoder().encodeToString(signature);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("올바른 시크릿으로 계산한 서명은 유효하다")
    void isValid_correctSignature_returnsTrue() {
        String webhookId = "wh_1";
        String webhookTimestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String rawBody = "{\"type\":\"Transaction.Paid\"}";
        String signatureHeader = computeValidSignatureHeader(webhookId, webhookTimestamp, rawBody);

        assertTrue(verifier.isValid(webhookId, webhookTimestamp, signatureHeader, rawBody));
    }

    @Test
    @DisplayName("여러 서명 후보 중 하나라도 일치하면 유효하다(v1, 여러 개 공백 구분)")
    void isValid_multipleSignatureCandidates_oneMatches_returnsTrue() {
        String webhookId = "wh_2";
        String webhookTimestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String rawBody = "{\"type\":\"Transaction.Paid\"}";
        String validSignature = computeValidSignatureHeader(webhookId, webhookTimestamp, rawBody);
        String signatureHeader = "v1,bm90LXRoZS1yaWdodC1zaWc= " + validSignature;

        assertTrue(verifier.isValid(webhookId, webhookTimestamp, signatureHeader, rawBody));
    }

    @Test
    @DisplayName("본문(raw body)이 조금이라도 다르면(재직렬화 등) 서명이 깨진다")
    void isValid_tamperedBody_returnsFalse() {
        String webhookId = "wh_3";
        String webhookTimestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String originalBody = "{\"type\":\"Transaction.Paid\"}";
        String signatureHeader = computeValidSignatureHeader(webhookId, webhookTimestamp, originalBody);

        String tamperedBody = "{\"type\": \"Transaction.Paid\"}"; // 공백 하나 차이
        assertFalse(verifier.isValid(webhookId, webhookTimestamp, signatureHeader, tamperedBody));
    }

    @Test
    @DisplayName("다른 시크릿으로 계산한 서명(위조)은 거부된다")
    void isValid_wrongSecret_returnsFalse() {
        String webhookId = "wh_4";
        String webhookTimestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String rawBody = "{\"type\":\"Transaction.Paid\"}";

        // 공격자가 우리 시크릿을 모른 채 임의의(무작위) 서명 바이트를 만들어 보내는 상황을 재현한다.
        String forgedSignatureHeader = "v1," + Base64.getEncoder().encodeToString(new byte[32]);

        assertFalse(verifier.isValid(webhookId, webhookTimestamp, forgedSignatureHeader, rawBody));
    }

    @Test
    @DisplayName("시크릿이 설정되지 않으면(빈 값) 무조건 거부한다(fail-closed)")
    void isValid_noSecretConfigured_alwaysReturnsFalse() {
        PortOneWebhookVerifier unconfigured = new PortOneWebhookVerifier("");
        String webhookId = "wh_5";
        String webhookTimestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String rawBody = "{}";

        assertFalse(unconfigured.isValid(webhookId, webhookTimestamp, "v1,anything", rawBody));
    }

    @Test
    @DisplayName("필수 헤더가 없으면 거부한다")
    void isValid_missingHeaders_returnsFalse() {
        assertFalse(verifier.isValid(null, "123", "v1,abc", "{}"));
        assertFalse(verifier.isValid("wh_6", null, "v1,abc", "{}"));
        assertFalse(verifier.isValid("wh_6", "123", null, "{}"));
    }
}
