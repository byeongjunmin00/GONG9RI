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
        LocalDateTime paidAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getMember().getId(),
                payment.getProduct().getId(),
                payment.getProduct().getName(),
                payment.getTeam() != null ? payment.getTeam().getId() : null,
                payment.getAmount(),
                payment.getStatus().name(),
                payment.getPaidAt()
        );
    }
}
