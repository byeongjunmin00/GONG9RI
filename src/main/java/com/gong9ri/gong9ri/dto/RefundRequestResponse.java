package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.RefundRequest;
import com.gong9ri.gong9ri.entity.RefundRequestStatus;
import java.time.LocalDateTime;

public record RefundRequestResponse(
        Long refundRequestId,
        Long paymentId,
        Long productId,
        String productName,
        Long teamId,
        Integer amount,
        String paymentStatus,
        RefundRequestStatus status,
        String reason,
        // 거절 사유 템플릿의 설명 문구. REJECTED가 아니면 null.
        String rejectionReason,
        LocalDateTime requestedAt,
        LocalDateTime decidedAt
) {
    public static RefundRequestResponse from(RefundRequest refundRequest) {
        Payment payment = refundRequest.getPayment();
        return new RefundRequestResponse(
                refundRequest.getId(),
                payment.getId(),
                payment.getProduct().getId(),
                payment.getProduct().getName(),
                payment.getTeam() != null ? payment.getTeam().getId() : null,
                payment.getAmount(),
                payment.getStatus().name(),
                refundRequest.getStatus(),
                refundRequest.getReason(),
                refundRequest.getRejectionReason() != null ? refundRequest.getRejectionReason().getDescription() : null,
                refundRequest.getRequestedAt(),
                refundRequest.getDecidedAt()
        );
    }
}
