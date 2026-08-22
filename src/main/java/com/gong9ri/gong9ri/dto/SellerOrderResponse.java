package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.PaymentStatus;
import com.gong9ri.gong9ri.entity.ShipmentStatus;
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
        // 공구팀 번호(admin-identifier-codes, 2026-08-22) — teamId와 같은 자리에 노출한다.
        String teamNo,
        TeamStatus teamStatus,
        Integer teamCurrentCount,
        Integer teamMaxParticipants,
        String preparationStatus,
        String preparationStatusLabel,
        ShipmentStatus shipmentStatus,
        String shipmentStatusLabel,
        String trackingCarrier,
        String trackingNumber,
        // 프로필 사진(member/profile-image 노출, 2026-08-21). 구매자 이름과 같은 회원 엔티티에서 읽으므로
        // 추가 조회가 없다. 없으면 null → 프론트가 구매자 이름 첫 글자 동그라미를 그린다.
        String buyerProfileImageUrl
) {
    /**
     * 이 주문의 표시용 진행 상태(REFUNDED/RECRUITING/FAILED/PREPARING)를 결제·공구팀 상태로부터
     * 계산한다(저장값 아님). 판매자 주문 목록 쿼리가 이미 PENDING/FAILED 결제를 제외하고 넘겨준다는
     * 전제 위에서 동작한다(005 리뷰에서 발견한 버그, {@code PaymentRepository}의 WHERE 조건 참고) —
     * 그 전제가 깨지면(PENDING 결제가 들어오면) 팀이 없거나 SUCCESS인 경우 잘못 PREPARING으로 표시될
     * 수 있으므로, 배송 단계 변경 가능 여부는 이 메서드가 아니라 {@link #isShipmentManageable}로
     * 별도 판정한다(결제 상태를 독립적으로 다시 확인함).
     */
    public static String derivePreparationStatus(Payment payment) {
        if (payment.getStatus() == PaymentStatus.REFUNDED || payment.getStatus() == PaymentStatus.REFUND_PENDING) {
            return "REFUNDED";
        }
        GroupBuyTeam team = payment.getTeam();
        TeamStatus teamStatus = team != null ? team.getStatus() : null;
        if (teamStatus == TeamStatus.RECRUITING) {
            return "RECRUITING";
        }
        if (teamStatus == TeamStatus.FAILED) {
            return "FAILED";
        }
        // SUCCESS 또는 솔로 구매(팀 없음)
        return "PREPARING";
    }

    /**
     * 판매자가 이 주문의 배송 단계를 바꿀 수 있는지 — {@code PAID} 결제이면서(PENDING/FAILED/REFUND_PENDING/
     * REFUNDED 전부 제외) 공구팀도 RECRUITING/FAILED가 아닌(=실제 배송 대상이 된) 경우에만 허용한다.
     * {@code SellerMypageService.updateShipment()}가 변경 요청마다 이 메서드로 먼저 검증한다.
     */
    public static boolean isShipmentManageable(Payment payment) {
        return payment.getStatus() == PaymentStatus.PAID && "PREPARING".equals(derivePreparationStatus(payment));
    }

    private static String preparationStatusLabel(String preparationStatus, Integer currentCount,
            Integer maxParticipants) {
        return switch (preparationStatus) {
            case "REFUNDED" -> "🔄 환불/취소됨";
            case "RECRUITING" -> "⏳ 공구 모집 중 (" + currentCount + "/" + maxParticipants + "명)";
            case "FAILED" -> "❌ 공구 실패 (환불 처리)";
            default -> "🚚 배송 준비 중";
        };
    }

    public static SellerOrderResponse from(Payment payment) {
        GroupBuyTeam team = payment.getTeam();
        Long teamId = team != null ? team.getId() : null;
        String teamNo = team != null ? team.getTeamNo() : null;
        TeamStatus teamStatus = team != null ? team.getStatus() : null;
        Integer currentCount = team != null ? team.getCurrentCount() : null;
        Integer maxParticipants = team != null ? team.getMaxParticipants() : null;

        String prepStatus = derivePreparationStatus(payment);
        String prepLabel = preparationStatusLabel(prepStatus, currentCount, maxParticipants);

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
                teamNo,
                teamStatus,
                currentCount,
                maxParticipants,
                prepStatus,
                prepLabel,
                payment.getShipmentStatus(),
                payment.getShipmentStatus().label(),
                payment.getTrackingCarrier(),
                payment.getTrackingNumber(),
                payment.getMember() != null ? payment.getMember().getProfileImageUrl() : null
        );
    }
}
