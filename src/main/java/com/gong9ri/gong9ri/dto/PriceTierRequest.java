package com.gong9ri.gong9ri.dto;

import jakarta.validation.constraints.NotNull;

public record PriceTierRequest(
        @NotNull Integer minCount,
        @NotNull Integer price
) {
}
