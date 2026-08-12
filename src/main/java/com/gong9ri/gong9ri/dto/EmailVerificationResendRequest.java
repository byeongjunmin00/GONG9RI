package com.gong9ri.gong9ri.dto;

import jakarta.validation.constraints.NotBlank;

public record EmailVerificationResendRequest(
        @NotBlank String username
) {
}
