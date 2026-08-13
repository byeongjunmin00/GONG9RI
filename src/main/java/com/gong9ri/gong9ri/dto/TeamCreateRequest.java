package com.gong9ri.gong9ri.dto;

import jakarta.validation.constraints.NotNull;

public record TeamCreateRequest(
        @NotNull Integer targetParticipants
) {
}
