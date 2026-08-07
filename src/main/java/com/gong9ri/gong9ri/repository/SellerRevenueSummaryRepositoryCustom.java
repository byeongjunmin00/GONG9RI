package com.gong9ri.gong9ri.repository;

/**
 * SellerRevenueSummaryRepository의 QueryDSL 기반 커스텀 쿼리.
 * 구현은 {@link SellerRevenueSummaryRepositoryImpl} 참고 — docs/dev/ongoing/querydsl-migration.md.
 */
public interface SellerRevenueSummaryRepositoryCustom {

    // team/deadline-check 환불 처리 시 감소시킨다 — 환불된 결제들의 금액 합·건수를 한 번에 반영한다.
    // **전제**: incrementPaid가 upsert인 뒤부터는, 결제 시점에 이미 요약 행이 만들어져 있어야 한다
    // (환불은 항상 이미 PAID였던 결제를 대상으로 하므로). 이 전제가 깨지는 유일한 경우는 "upsert 전환
    // 이전부터 있던 결제 이력"이 아직 백필(SellerRevenueSummaryBackfillService/Runner,
    // docs/db/seller_revenue_summary.md)되지 않은 채로 환불이 먼저 들어오는 것 — 이 메서드는 여전히
    // 조건부 UPDATE라 그 경우 대상 행이 없어 0 rows affected로 조용히 무시된다. 호출부
    // (TeamDeadlineService.processDeadline)가 이 리턴값이 0이면 WARN 로그를 남겨 드러나게 한다.
    // 구현에서 flush를 먼저 하는 이유(원래 @Modifying(flushAutomatically = true)와 동일)가 특히 중요하다:
    // 이 호출 직전에 TeamDeadlineService가 paidPayments.forEach(Payment::refund)로 여러 Payment
    // 엔티티를 더티 상태로 만들어두는데, flush 없이 clear만 실행하면 그 변경이 flush되지 않은 채
    // 컨텍스트에서 detach되어 DB에 반영되지 않고 유실된다(실제로 재현·확인된 버그).
    int applyRefund(Long sellerId, Integer refundedAmount, Long refundedCount);
}
