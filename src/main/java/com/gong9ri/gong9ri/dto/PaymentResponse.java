package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.Payment;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long paymentId,
        Long memberId,
        Long productId,
        String productName,
        Long teamId,
        Integer amount,
        String status,
        LocalDateTime paidAt,
        String pgPaymentId,
        // 결제 요청 접수(create) 응답에만 채워진다 — 프론트가 이 값으로 PortOne.requestPayment()를 연다.
        // 이미 존재하는 결제를 확정(confirm)·조회(detail)할 때는 프론트가 새로 결제창을 열 필요가 없어 null.
        String portoneStoreId,
        String portoneChannelKey
) {
    public static PaymentResponse from(Payment payment) {
        return from(payment, null, null);
    }

    public static PaymentResponse from(Payment payment, String portoneStoreId, String portoneChannelKey) {
        return new PaymentResponse(
                payment.getId(),
                payment.getMember().getId(),
                payment.getProduct().getId(),
                payment.getProduct().getName(),
                payment.getTeam() != null ? payment.getTeam().getId() : null,
                payment.getAmount(),
                payment.getStatus().name(),
                payment.getPaidAt(),
                payment.getPgPaymentId(),
                portoneStoreId,
                portoneChannelKey
        );
    }
}
