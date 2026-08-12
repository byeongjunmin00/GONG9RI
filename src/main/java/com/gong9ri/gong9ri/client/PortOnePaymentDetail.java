package com.gong9ri.gong9ri.client;

/**
 * {@code GET https://api.portone.io/payments/{paymentId}} 응답에서 서버 재검증에 필요한 부분만 뽑은 값.
 * {@code status}는 PortOne이 실제로 응답한 문자열(예: "PAID", "FAILED", "VIRTUAL_ACCOUNT_ISSUED" 등)을
 * 그대로 담는다 — 이 스코프(카드결제)에서는 "PAID"·"FAILED"만 실제로 분기 처리한다.
 */
public record PortOnePaymentDetail(String status, Integer totalAmount) {
}
