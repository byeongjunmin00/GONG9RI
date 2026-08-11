package com.gong9ri.gong9ri.dto;

public record ChatSessionUsageResponse(
        Long sessionId,
        long totalTokens
) {
}
