package com.gong9ri.gong9ri.dto;

/**
 * 모델별 누적 토큰·P95 응답지연·에러율 대시보드(발제 AI 필수3 요구사항).
 * errorRate는 0.0~1.0 비율(예: 0.05 = 5%).
 */
public record ChatStatsResponse(
        String model,
        long callCount,
        long totalTokens,
        long p95LatencyMs,
        double errorRate
) {
}
