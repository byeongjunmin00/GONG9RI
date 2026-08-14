package com.gong9ri.gong9ri.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ProductRegisterRequest(
        @NotBlank String name,
        String description,
        @NotNull Integer basePrice,
        @NotNull Integer maxParticipants,
        @NotEmpty @Valid List<PriceTierRequest> priceTiers,
        String imageUrl,
        // 참여 취소(team/leave) 시 생기는 환불 요청을 판매자 승인 없이 즉시 처리할지 여부. 생략(null)하면
        // false로 취급한다(docs/dev/ongoing/team-leave-and-refund-request.md).
        Boolean autoRefundOnCancel
) {
}
