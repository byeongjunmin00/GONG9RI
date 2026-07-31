package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MemberSignupRequest(
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotNull Role role
) {
}
