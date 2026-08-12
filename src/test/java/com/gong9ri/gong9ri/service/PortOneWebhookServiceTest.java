package com.gong9ri.gong9ri.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gong9ri.gong9ri.client.PortOneWebhookVerifier;
import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code PortOneWebhookService} 순수 단위 테스트(Mockito) — 서명 검증 실패 거부, 타임스탬프 만료 거부,
 * 멱등성(중복 webhook-id 무시), 타입별 라우팅, 모르는 타입 무시를 검증한다(docs/dev/payment/portone/design.md).
 * 실제 Spring 컨텍스트·Redis·PortOne 호출 없음.
 */
@ExtendWith(MockitoExtension.class)
class PortOneWebhookServiceTest {

    @Mock
    private PortOneWebhookVerifier verifier;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentRefundService paymentRefundService;

    private PortOneWebhookService portOneWebhookService;

    private static final String WEBHOOK_ID = "wh_test_1";
    private static final String PG_PAYMENT_ID = "pay_abc123";

    private String freshTimestamp() {
        return String.valueOf(Instant.now().getEpochSecond());
    }

    private String rawBody(String type) {
        return "{\"type\":\"" + type + "\",\"data\":{\"paymentId\":\"" + PG_PAYMENT_ID + "\"}}";
    }

    @BeforeEach
    void setUp() {
        portOneWebhookService = new PortOneWebhookService(
                verifier, redisTemplate, paymentService, paymentRefundService, new ObjectMapper());
        // 서명 검증 실패/타임스탬프 만료 테스트는 이 지점까지 도달하지 않고 먼저 예외를 던지므로
        // 아래 기본 스텁들은 그 테스트들 입장에서 "쓰이지 않는 스텁"이 된다 — lenient로 strict-stub
        // 위반(UnnecessaryStubbingException)을 피한다.
        lenient().when(verifier.isValid(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
    }

    @Test
    @DisplayName("서명 검증에 실패하면 WEBHOOK_VERIFICATION_FAILED를 던지고 어떤 결제 서비스도 호출하지 않는다")
    void handle_invalidSignature_throwsAndSkipsProcessing() {
        when(verifier.isValid(anyString(), anyString(), anyString(), anyString())).thenReturn(false);

        BusinessException e = assertThrows(BusinessException.class,
                () -> portOneWebhookService.handle(WEBHOOK_ID, freshTimestamp(), "v1,bad", rawBody("Transaction.Paid")));

        assertEquals(ErrorCode.WEBHOOK_VERIFICATION_FAILED, e.getErrorCode());
        verify(paymentService, never()).confirmByPgPaymentId(anyString());
        verify(paymentRefundService, never()).confirmRefundedByPgPaymentId(anyString());
    }

    @Test
    @DisplayName("타임스탬프가 허용 범위(5분)를 벗어나면 리플레이 의심으로 거부한다")
    void handle_staleTimestamp_throwsVerificationFailed() {
        String staleTimestamp = String.valueOf(Instant.now().minus(Duration.ofMinutes(10)).getEpochSecond());

        BusinessException e = assertThrows(BusinessException.class,
                () -> portOneWebhookService.handle(WEBHOOK_ID, staleTimestamp, "v1,sig", rawBody("Transaction.Paid")));

        assertEquals(ErrorCode.WEBHOOK_VERIFICATION_FAILED, e.getErrorCode());
        verify(paymentService, never()).confirmByPgPaymentId(anyString());
    }

    @Test
    @DisplayName("이미 처리된 webhook-id(멱등성 키 존재)면 조용히 무시하고 아무 서비스도 호출하지 않는다")
    void handle_duplicateWebhookId_isIgnored() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        portOneWebhookService.handle(WEBHOOK_ID, freshTimestamp(), "v1,sig", rawBody("Transaction.Paid"));

        verify(paymentService, never()).confirmByPgPaymentId(anyString());
    }

    @Test
    @DisplayName("Transaction.Paid는 PaymentService.confirmByPgPaymentId로 라우팅된다")
    void handle_transactionPaid_routesToPaymentServiceConfirm() {
        portOneWebhookService.handle(WEBHOOK_ID, freshTimestamp(), "v1,sig", rawBody("Transaction.Paid"));

        verify(paymentService, times(1)).confirmByPgPaymentId(eq(PG_PAYMENT_ID));
        verify(paymentRefundService, never()).confirmRefundedByPgPaymentId(anyString());
    }

    @Test
    @DisplayName("Transaction.Failed도 재검증을 위해 PaymentService.confirmByPgPaymentId로 라우팅된다")
    void handle_transactionFailed_routesToPaymentServiceConfirm() {
        portOneWebhookService.handle(WEBHOOK_ID, freshTimestamp(), "v1,sig", rawBody("Transaction.Failed"));

        verify(paymentService, times(1)).confirmByPgPaymentId(eq(PG_PAYMENT_ID));
    }

    @Test
    @DisplayName("Transaction.Cancelled는 PaymentRefundService.confirmRefundedByPgPaymentId로 라우팅된다")
    void handle_transactionCancelled_routesToPaymentRefundService() {
        portOneWebhookService.handle(WEBHOOK_ID, freshTimestamp(), "v1,sig", rawBody("Transaction.Cancelled"));

        verify(paymentRefundService, times(1)).confirmRefundedByPgPaymentId(eq(PG_PAYMENT_ID));
        verify(paymentService, never()).confirmByPgPaymentId(anyString());
    }

    @Test
    @DisplayName("모르는 타입은 에러 없이 무시한다(하위 호환)")
    void handle_unknownType_isIgnoredWithoutError() {
        portOneWebhookService.handle(WEBHOOK_ID, freshTimestamp(), "v1,sig", rawBody("Transaction.SomeFutureType"));

        verify(paymentService, never()).confirmByPgPaymentId(anyString());
        verify(paymentRefundService, never()).confirmRefundedByPgPaymentId(anyString());
    }

    @Test
    @DisplayName("Redis 호출이 실패해도(멱등성 체크 불가) fail-open으로 처리를 계속 진행한다")
    void handle_redisFailure_failsOpenAndContinuesProcessing() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("redis down"));

        portOneWebhookService.handle(WEBHOOK_ID, freshTimestamp(), "v1,sig", rawBody("Transaction.Paid"));

        verify(paymentService, times(1)).confirmByPgPaymentId(eq(PG_PAYMENT_ID));
    }
}
