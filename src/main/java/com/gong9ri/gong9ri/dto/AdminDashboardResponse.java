package com.gong9ri.gong9ri.dto;

// 관리자 대시보드(product/admin) 요약 카드 — 무거운 집계 없이 각 리포지토리 count() 한 번씩만 쓴다.
public record AdminDashboardResponse(
        long totalMembers,
        long totalBuyers,
        long totalSellers,
        long totalProducts,
        long totalPayments,
        long pendingRefundRequests
) {
}
