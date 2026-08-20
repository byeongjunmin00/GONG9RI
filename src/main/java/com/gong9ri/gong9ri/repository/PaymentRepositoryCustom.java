package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.Payment;
import java.util.List;
import java.util.Optional;

/**
 * PaymentRepository의 QueryDSL 기반 커스텀 쿼리(페치조인·집계).
 * 구현은 {@link PaymentRepositoryImpl} 참고 — docs/dev/ongoing/querydsl-migration.md.
 */
public interface PaymentRepositoryCustom {

    Optional<Payment> findByIdWithDetails(Long id);

    List<Payment> findAllByMemberIdWithProduct(Long memberId);

    RevenueSummaryProjection findRevenueSummaryBySellerId(Long sellerId);

    // seller_revenue_summary 1회성 백필(SellerRevenueSummaryBackfillService) 대상 판매자를 찾는다 —
    // "결제 이력은 있는데 아직 요약 행이 없는" 판매자를 골라내기 위한 후보 목록(전체 seller_id).
    List<Long> findDistinctSellerIdsWithPayments();

    // 결제 확정(PaymentService.confirm/confirmByPgPaymentId) 전용 — 클라이언트 confirm()과 웹훅이
    // 거의 동시에 같은 결제를 확정하려 들 수 있어(둘 다 PENDING 게이트만 보고 통과하면 중복 확정),
    // 비관적 락으로 한쪽이 커밋될 때까지 다른 쪽을 대기시킨다(GroupBuyTeamRepositoryImpl.findByIdForUpdate와 동일 패턴).
    Optional<Payment> findByIdForUpdate(Long id);

    Optional<Payment> findByPgPaymentIdForUpdate(String pgPaymentId);
}
