package com.gong9ri.gong9ri.repository;

/**
 * {@link RevenueSummaryProjection}의 QueryDSL {@code Projections.constructor} 대상 구현체.
 * QueryDSL은 인터페이스 프로젝션(bean binding)을 직접 지원하지 않아, 생성자 프로젝션이 바인딩할
 * 구체 클래스가 필요하다 — {@link PaymentRepositoryImpl} 참고.
 */
public class RevenueSummaryProjectionImpl implements RevenueSummaryProjection {

    private final Integer totalRevenue;
    private final Long paidCount;
    private final Long refundedCount;

    public RevenueSummaryProjectionImpl(Integer totalRevenue, Long paidCount, Long refundedCount) {
        this.totalRevenue = totalRevenue;
        this.paidCount = paidCount;
        this.refundedCount = refundedCount;
    }

    @Override
    public Integer getTotalRevenue() {
        return totalRevenue;
    }

    @Override
    public Long getPaidCount() {
        return paidCount;
    }

    @Override
    public Long getRefundedCount() {
        return refundedCount;
    }
}
