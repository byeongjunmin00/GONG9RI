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
        @NotEmpty @Valid List<PriceTierRequest> priceTiers
) {
}
