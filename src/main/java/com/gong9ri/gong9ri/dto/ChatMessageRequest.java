package com.gong9ri.gong9ri.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatMessageRequest(
        Long sessionId,
        @NotBlank String content
) {
}
