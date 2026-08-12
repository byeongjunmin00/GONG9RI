package com.gong9ri.gong9ri.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * PortOne V2 REST API 실제 호출 구현. 별도 SDK 의존성 없이 {@code RestClient}(spring-web 내장, 신규
 * 라이브러리 추가 없음)로 직접 호출한다 — {@code io.portone:server-sdk}가 Maven Central에 실제로
 * 존재하는지 확인되지 않아 검증 없이 추가하지 않는다(docs/dev/ongoing/payment-portone.md 브리핑).
 *
 * <p>인증은 {@code Authorization: PortOne {API_SECRET}} 헤더 — PortOne 공식 문서 기준이며 OAuth Bearer
 * 형식이 아니다.
 */
@Component
public class PortOneApiClient implements PortOneClient {

    private final RestClient restClient;
    private final String apiSecret;

    public PortOneApiClient(
            @Value("${portone.api-base-url}") String apiBaseUrl,
            @Value("${portone.api-secret}") String apiSecret) {
        this.apiSecret = apiSecret;
        this.restClient = RestClient.builder().baseUrl(apiBaseUrl).build();
    }

    @Override
    public PortOnePaymentDetail getPayment(String pgPaymentId) {
        PaymentApiResponse response = restClient.get()
                .uri("/payments/{paymentId}", pgPaymentId)
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader())
                .retrieve()
                .body(PaymentApiResponse.class);
        Integer total = response != null && response.amount() != null ? response.amount().total() : null;
        return new PortOnePaymentDetail(response != null ? response.status() : null, total);
    }

    @Override
    public PortOneCancelResult cancelPayment(String pgPaymentId, String reason) {
        CancelApiResponse response = restClient.post()
                .uri("/payments/{paymentId}/cancel", pgPaymentId)
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CancelApiRequest(reason))
                .retrieve()
                .body(CancelApiResponse.class);
        String status = response != null && response.cancellation() != null ? response.cancellation().status() : null;
        return new PortOneCancelResult(status);
    }

    private String authorizationHeader() {
        return "PortOne " + apiSecret;
    }

    // PortOne 응답 스키마 중 서버 재검증에 필요한 부분만 매핑한다(그 외 필드는 무시).
    private record PaymentApiResponse(String status, Amount amount) {
        private record Amount(Integer total) {
        }
    }

    private record CancelApiRequest(String reason) {
    }

    private record CancelApiResponse(Cancellation cancellation) {
        private record Cancellation(String status) {
        }
    }
}
