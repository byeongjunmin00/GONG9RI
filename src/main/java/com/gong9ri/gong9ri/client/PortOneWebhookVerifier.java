package com.gong9ri.gong9ri.client;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * PortOne 웹훅 서명 검증 — Standard Webhooks 스펙(PortOne 공식 문서, {@code webhook-id}/{@code
 * webhook-timestamp}/{@code webhook-signature} 헤더) 순수 HMAC-SHA256 구현. 이 검증이 곧 웹훅
 * 엔드포인트의 "인증" 역할을 한다(세션 인증을 쓸 수 없는 대신 서명으로 PortOne이 보낸 요청임을 확인,
 * docs/dev/payment/portone/design.md).
 *
 * <p>서명 대상 문자열은 {@code "{webhook-id}.{webhook-timestamp}.{raw_body}"}이고, {@code raw_body}는
 * 실제 수신 바이트 그대로여야 한다(재직렬화된 문자열을 쓰면 서명이 깨진다) — 호출부(컨트롤러)가
 * {@code HttpServletRequest.getInputStream()}으로 직접 읽은 원문을 그대로 넘겨야 한다.
 *
 * <p>Maven Central에 {@code io.portone:server-sdk}가 실제로 존재하는지 확인되지 않아, 검증 없이 그
 * 의존성을 추가하지 않고 {@code javax.crypto.Mac}으로 직접 구현한다.
 */
@Slf4j
@Component
public class PortOneWebhookVerifier {

    private static final String SIGNATURE_PREFIX = "v1,";
    private static final String SECRET_PREFIX = "whsec_";

    private final byte[] secretKeyBytes;

    public PortOneWebhookVerifier(@Value("${portone.webhook-secret}") String webhookSecret) {
        this.secretKeyBytes = decodeSecret(webhookSecret);
    }

    /**
     * @return 서명이 유효하면(등록된 시크릿으로 계산한 HMAC과 헤더의 서명 후보 중 하나라도 일치) true.
     *     시크릿이 아예 설정돼 있지 않으면(운영 설정 누락) 무조건 false — 서명 검증=인증이라 fail-closed다
     *     (RateLimitFilter의 fail-open과 다른 원칙: 여기서 실패를 통과시키면 인증 우회가 된다).
     */
    public boolean isValid(String webhookId, String webhookTimestamp, String webhookSignatureHeader, String rawBody) {
        if (secretKeyBytes.length == 0) {
            log.error("PortOne 웹훅 시크릿이 설정되지 않아 서명 검증을 통과시킬 수 없습니다(fail-closed).");
            return false;
        }
        if (webhookId == null || webhookTimestamp == null || webhookSignatureHeader == null) {
            return false;
        }

        byte[] expected = hmacSha256(webhookId + "." + webhookTimestamp + "." + rawBody);

        for (String token : webhookSignatureHeader.trim().split("\\s+")) {
            if (!token.startsWith(SIGNATURE_PREFIX)) {
                continue;
            }
            byte[] candidate = decodeBase64Silently(token.substring(SIGNATURE_PREFIX.length()));
            if (candidate != null && MessageDigest.isEqual(candidate, expected)) {
                return true;
            }
        }
        return false;
    }

    private byte[] decodeSecret(String webhookSecret) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return new byte[0];
        }
        String base64Part = webhookSecret.startsWith(SECRET_PREFIX)
                ? webhookSecret.substring(SECRET_PREFIX.length())
                : webhookSecret;
        try {
            return Base64.getDecoder().decode(base64Part);
        } catch (IllegalArgumentException e) {
            log.error("PortOne 웹훅 시크릿이 올바른 base64 형식이 아닙니다.");
            return new byte[0];
        }
    }

    private byte[] decodeBase64Silently(String value) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private byte[] hmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKeyBytes, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PortOne 웹훅 서명 HMAC 계산 실패", e);
        }
    }
}
