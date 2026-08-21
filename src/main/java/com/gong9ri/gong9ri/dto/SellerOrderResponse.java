package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.PaymentStatus;
import com.gong9ri.gong9ri.entity.TeamStatus;
import java.time.LocalDateTime;

public record SellerOrderResponse(
        Long paymentId,
        String buyerName,
        String buyerEmail,
        Long productId,
        String productName,
        Integer amount,
        PaymentStatus status,
        LocalDateTime paidAt,
        Long teamId,
        TeamStatus teamStatus,
        Integer teamCurrentCount,
        Integer teamMaxParticipants,
        String preparationStatus,
        String preparationStatusLabel
) {
    public static SellerOrderResponse from(Payment payment) {
        GroupBuyTeam team = payment.getTeam();
        Long teamId = team != null ? team.getId() : null;
        TeamStatus teamStatus = team != null ? team.getStatus() : null;
        Integer currentCount = team != null ? team.getCurrentCount() : null;
        Integer maxParticipants = team != null ? team.getMaxParticipants() : null;

        String prepStatus;
        String prepLabel;

        if (payment.getStatus() == PaymentStatus.REFUNDED || payment.getStatus() == PaymentStatus.REFUND_PENDING) {
            prepStatus = "REFUNDED";
            prepLabel = "🔄 환불/취소됨";
        } else if (teamStatus == TeamStatus.RECRUITING) {
            prepStatus = "RECRUITING";
            prepLabel = "⏳ 공구 모집 중 (" + currentCount + "/" + maxParticipants + "명)";
        } else if (teamStatus == TeamStatus.FAILED) {
            prepStatus = "FAILED";
            prepLabel = "❌ 공구 실패 (환불 처리)";
        } else {
            // SUCCESS 또는 솔로 구매(팀 없음)
            prepStatus = "PREPARING";
            prepLabel = "🚚 배송 준비 중";
        }

        return new SellerOrderResponse(
                payment.getId(),
                payment.getMember() != null ? payment.getMember().getName() : "익명",
                payment.getMember() != null ? payment.getMember().getEmail() : "",
                payment.getProduct().getId(),
                payment.getProduct().getName(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getPaidAt(),
                teamId,
                teamStatus,
                currentCount,
                maxParticipants,
                prepStatus,
                prepLabel
        );
    }
}
