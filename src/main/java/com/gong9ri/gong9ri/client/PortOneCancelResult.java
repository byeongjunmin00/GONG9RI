package com.gong9ri.gong9ri.client;

/**
 * {@code POST https://api.portone.io/payments/{paymentId}/cancel} 응답의 {@code cancellation.status}.
 * PortOne 공식 문서 기준 {@code SUCCEEDED}(즉시 완료) / {@code REQUESTED}(비동기 처리 중, 웹훅
 * {@code Transaction.Cancelled}로 최종 확정) / {@code FAILED} 중 하나 — 취소가 항상 동기로 즉시
 * 끝난다고 가정하지 않는다(docs/dev/payment/portone/design.md).
 */
public record PortOneCancelResult(String status) {

    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String REQUESTED = "REQUESTED";
    public static final String FAILED = "FAILED";
}
