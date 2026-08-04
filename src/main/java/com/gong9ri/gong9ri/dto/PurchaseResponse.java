package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.Payment;
import java.time.LocalDateTime;

public record PurchaseResponse(
        Long paymentId,
        Long productId,
        String productName,
        Integer amount,
        String status,
        LocalDateTime paidAt
) {
    public static PurchaseResponse from(Payment payment) {
        return new PurchaseResponse(
                payment.getId(),
                payment.getProduct().getId(),
                payment.getProduct().getName(),
                payment.getAmount(),
                payment.getStatus().name(),
                payment.getPaidAt()
        );
    }
}
