package com.gong9ri.gong9ri.entity;

/**
 * 결제 상태 전이 (docs/dev/payment/portone/design.md):
 * <pre>
 * PENDING --(서버가 PortOne 재조회로 확정)--&gt; PAID --(PortOne 취소 SUCCEEDED 또는 웹훅 Transaction.Cancelled)--&gt; REFUNDED
 *    |                                        |
 *    +--(PortOne 재조회 결과 FAILED)--&gt; FAILED  +--(PortOne 취소 REQUESTED, 비동기)--&gt; REFUND_PENDING --(웹훅 확정)--&gt; REFUNDED
 * </pre>
 */
public enum PaymentStatus {
    // 결제 요청 접수 — 클라이언트가 PortOne 결제창을 아직 열었거나 진행 중, 서버가 아직 확정하지 않음.
    PENDING,
    // 서버가 PortOne API 재조회로 승인·금액 일치를 확인해 확정한 상태.
    PAID,
    // 서버 재조회 결과 PortOne이 최종 실패로 응답한 상태(승인 대기 중 결제 자체가 실패).
    FAILED,
    // 결제취소를 요청했고 PortOne이 비동기 처리 중(REQUESTED)이라 웹훅 최종 확정을 기다리는 중간 상태.
    REFUND_PENDING,
    // 결제취소가 실제로 완료됐음을 확인한 상태.
    REFUNDED
}
