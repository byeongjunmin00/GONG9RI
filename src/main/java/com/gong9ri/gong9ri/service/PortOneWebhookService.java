package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.client.PortOneWebhookVerifier;
import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * PortOne 웹훅 수신 처리 — 서명 검증이 곧 이 엔드포인트의 "인증"이다(세션 인증을 요구할 수 없는 PG
 * 콜백이라 {@code SecurityConfig}에서 permitAll, 대신 여기서 반드시 서명을 검증한다,
 * docs/dev/payment/portone/design.md).
 *
 * <p>처리 순서: 서명 검증(fail-closed) → 타임스탬프 신선도(리플레이 방지) → 멱등성(webhook-id, Redis,
 * fail-open) → 타입별 라우팅. 모르는 {@code type}은 에러 없이 무시한다(PortOne 공식 문서의 하위 호환
 * 원칙).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortOneWebhookService {

    // PortOne 웹훅 재전송 정책(최대 5회)을 감안한 여유 있는 멱등성 TTL.
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    // 리플레이 공격 방지 — webhook-timestamp가 현재 시각과 이보다 더 벌어지면 거부.
    private static final Duration TIMESTAMP_TOLERANCE = Duration.ofMinutes(5);
    private static final String IDEMPOTENCY_KEY_PREFIX = "portone-webhook:";

    private final PortOneWebhookVerifier verifier;
    private final StringRedisTemplate redisTemplate;
    private final PaymentService paymentService;
    private final PaymentRefundService paymentRefundService;
    private final ObjectMapper objectMapper;

    public void handle(String webhookId, String webhookTimestamp, String webhookSignature, String rawBody) {
        if (!verifier.isValid(webhookId, webhookTimestamp, webhookSignature, rawBody)) {
            log.warn("포트원 웹훅 서명 검증 실패: webhookId={}", webhookId);
            throw new BusinessException(ErrorCode.WEBHOOK_VERIFICATION_FAILED);
        }
        if (!isFreshTimestamp(webhookTimestamp)) {
            log.warn("포트원 웹훅 타임스탬프가 허용 범위를 벗어남(리플레이 의심): webhookId={}, webhookTimestamp={}",
                    webhookId, webhookTimestamp);
            throw new BusinessException(ErrorCode.WEBHOOK_VERIFICATION_FAILED);
        }
        if (!markProcessedIfAbsent(webhookId)) {
            log.info("포트원 웹훅 중복 수신(이미 처리됨), 무시: webhookId={}", webhookId);
            return;
        }

        WebhookPayload payload = parsePayload(rawBody);
        route(payload);
    }

    private void route(WebhookPayload payload) {
        String pgPaymentId = payload.data() != null ? payload.data().paymentId() : null;
        if (pgPaymentId == null) {
            log.warn("포트원 웹훅 payload에 paymentId가 없음: type={}", payload.type());
            return;
        }

        switch (payload.type()) {
            case "Transaction.Paid", "Transaction.Failed" -> paymentService.confirmByPgPaymentId(pgPaymentId);
            case "Transaction.Cancelled" -> paymentRefundService.confirmRefundedByPgPaymentId(pgPaymentId);
            case "Transaction.CancelPending" ->
                    log.info("포트원 결제취소 비동기 처리 중(웹훅 CancelPending): pgPaymentId={}", pgPaymentId);
            default -> log.info("처리하지 않는 포트원 웹훅 타입(무시): type={}, pgPaymentId={}", payload.type(), pgPaymentId);
        }
    }

    private boolean isFreshTimestamp(String webhookTimestamp) {
        try {
            long epochSeconds = Long.parseLong(webhookTimestamp);
            long diffSeconds = Math.abs(Instant.now().getEpochSecond() - epochSeconds);
            return diffSeconds <= TIMESTAMP_TOLERANCE.getSeconds();
        } catch (NumberFormatException | NullPointerException e) {
            return false;
        }
    }

    // fail-open: Redis 장애 시 멱등성 체크 없이 처리를 계속 진행한다 — 아래 confirmByPgPaymentId /
    // confirmRefundedByPgPaymentId 자체가 결제 상태 기반으로 멱등하게 구현돼 있어 최악의 경우도
    // "한 번 더 확인하고 끝"일 뿐, 중복 반영으로 이어지지 않는다.
    private boolean markProcessedIfAbsent(String webhookId) {
        try {
            Boolean firstTime = redisTemplate.opsForValue()
                    .setIfAbsent(IDEMPOTENCY_KEY_PREFIX + webhookId, "1", IDEMPOTENCY_TTL);
            return Boolean.TRUE.equals(firstTime);
        } catch (Exception e) {
            log.warn("웹훅 멱등성 체크용 Redis 호출 실패, 중복 방지 없이 처리 진행: webhookId={}, error={}",
                    webhookId, e.getMessage());
            return true;
        }
    }

    private WebhookPayload parsePayload(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, WebhookPayload.class);
        } catch (Exception e) {
            log.warn("포트원 웹훅 payload 파싱 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }

    private record WebhookPayload(String type, Data data) {
        private record Data(String paymentId) {
        }
    }
}
