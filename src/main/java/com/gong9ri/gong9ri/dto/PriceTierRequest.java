package com.gong9ri.gong9ri.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PriceTierRequest(
        @NotNull @Min(2) Integer minCount,
        @NotNull @Min(1) Integer price
) {
}
