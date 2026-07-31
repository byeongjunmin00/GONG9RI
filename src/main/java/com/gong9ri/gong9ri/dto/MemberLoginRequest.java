package com.gong9ri.gong9ri.dto;

import jakarta.validation.constraints.NotBlank;

public record MemberLoginRequest(
        @NotBlank String username,
        @NotBlank String password
) {
}
