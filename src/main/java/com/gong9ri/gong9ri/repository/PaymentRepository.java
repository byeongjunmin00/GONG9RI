package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.PaymentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("SELECT p FROM Payment p JOIN FETCH p.member JOIN FETCH p.product LEFT JOIN FETCH p.team WHERE p.id = :id")
    Optional<Payment> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT p FROM Payment p JOIN FETCH p.product WHERE p.member.id = :memberId ORDER BY p.paidAt DESC")
    List<Payment> findAllByMemberIdWithProduct(@Param("memberId") Long memberId);

    // 마감 체크 스케줄러가 팀의 PAID 결제를 일괄 환불 전환하기 전에 조회한다 — idx_team_status(team_id, status) 인덱스 활용.
    List<Payment> findByTeamIdAndStatus(Long teamId, PaymentStatus status);

    @Query("SELECT "
            + "COALESCE(SUM(CASE WHEN p.status = 'PAID' THEN p.amount ELSE 0 END), 0) AS totalRevenue, "
            + "COALESCE(SUM(CASE WHEN p.status = 'PAID' THEN 1L ELSE 0L END), 0L) AS paidCount, "
            + "COALESCE(SUM(CASE WHEN p.status = 'REFUNDED' THEN 1L ELSE 0L END), 0L) AS refundedCount "
            + "FROM Payment p WHERE p.product.seller.id = :sellerId")
    RevenueSummaryProjection findRevenueSummaryBySellerId(@Param("sellerId") Long sellerId);
}
