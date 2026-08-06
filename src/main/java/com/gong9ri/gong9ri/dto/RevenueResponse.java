package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.SellerRevenueSummary;

public record RevenueResponse(
        Integer totalRevenue,
        Long paidCount,
        Long refundedCount
) {
    public static RevenueResponse from(SellerRevenueSummary summary) {
        return new RevenueResponse(
                summary.getTotalRevenue(),
                summary.getPaidCount(),
                summary.getRefundedCount()
        );
    }

    // seller_revenue_summary 요약 행이 없는 판매자(결제가 한 번도 없었던 판매자) 전용 — 항상 정확히
    // 0/0/0이다(SellerMypageService.revenue 참고).
    public static RevenueResponse empty() {
        return new RevenueResponse(0, 0L, 0L);
    }
}
