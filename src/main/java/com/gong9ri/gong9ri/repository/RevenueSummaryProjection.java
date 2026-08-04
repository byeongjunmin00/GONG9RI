package com.gong9ri.gong9ri.repository;

public interface RevenueSummaryProjection {

    Integer getTotalRevenue();

    Long getPaidCount();

    Long getRefundedCount();
}
