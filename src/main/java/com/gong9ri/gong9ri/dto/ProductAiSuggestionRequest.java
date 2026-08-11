package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.PromptCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProductAiSuggestionRequest(
        @NotNull PromptCategory category,
        @NotBlank String inputText
) {
}
