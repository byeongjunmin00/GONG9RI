package com.gong9ri.gong9ri.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InquiryAnswerRequest(
        @NotBlank @Size(max = 1000) String content
) {
}
