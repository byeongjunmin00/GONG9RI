package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.repository.RevenueSummaryProjection;

public record RevenueResponse(
        Integer totalRevenue,
        Long paidCount,
        Long refundedCount
) {
    public static RevenueResponse from(RevenueSummaryProjection projection) {
        return new RevenueResponse(
                projection.getTotalRevenue(),
                projection.getPaidCount(),
                projection.getRefundedCount()
        );
    }
}
