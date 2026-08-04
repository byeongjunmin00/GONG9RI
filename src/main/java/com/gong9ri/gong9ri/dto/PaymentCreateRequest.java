package com.gong9ri.gong9ri.dto;

import jakarta.validation.constraints.NotNull;

public record PaymentCreateRequest(
        @NotNull Long productId,
        Long teamId
) {
}
