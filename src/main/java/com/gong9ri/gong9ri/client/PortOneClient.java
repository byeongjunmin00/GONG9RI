package com.gong9ri.gong9ri.client;

/**
 * PortOne V2 REST API 클라이언트 — 서버측 결제 재검증(조회) + 결제취소만 다룬다(카드결제 스코프,
 * docs/dev/payment/portone/design.md). 테스트에서는 항상 {@code @MockitoBean}으로 대체해 실제
 * 네트워크 호출을 하지 않는다({@code AiProductSuggestionServiceTest}가 {@code ChatClient.Builder}를
 * 목으로 대체하는 것과 같은 패턴).
 */
public interface PortOneClient {

    /**
     * {@code GET /payments/{paymentId}} — 서버가 클라이언트의 "성공" 응답을 그대로 믿지 않고 직접
     * 재조회해서 실제 승인 상태·금액을 확인할 때 쓴다.
     */
    PortOnePaymentDetail getPayment(String pgPaymentId);

    /**
     * {@code POST /payments/{paymentId}/cancel} — 공구팀 미성사 자동환불 경로 전용(자발적 취소 기능
     * 없음, docs/dev/ongoing/payment-portone.md 스코프).
     */
    PortOneCancelResult cancelPayment(String pgPaymentId, String reason);
}
