package com.gong9ri.gong9ri.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record MemberInfoUpdateRequest(
        @NotBlank String name,
        @NotBlank @Email String email
) {
}
