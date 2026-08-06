package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.SellerRevenueSummary;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SellerRevenueSummaryRepository extends JpaRepository<SellerRevenueSummary, Long> {

    Optional<SellerRevenueSummary> findBySellerId(Long sellerId);

    // payment/create 트랜잭션 안에서 호출한다 — 요약 행이 없으면 이 결제 값으로 새로 만들고(그 판매자의
    // "첫 결제"), 있으면 원자적으로 증가시킨다(upsert, docs/dev/mypage/view/changes/004-upsert-fix.md).
    // MySQL의 INSERT ... ON DUPLICATE KEY UPDATE는 UNIQUE(seller_id) 충돌이 나면 그 행에 락을 걸고
    // UPDATE로 전환하는 단일 SQL 문이라, 같은 판매자에게 동시에 여러 "첫 결제"가 들어와도(요약 행이
    // 아직 없는 상태) 유실·중복 없이 정확히 반영된다.
    // (이전 방식은 "행이 있으면만 증가"하는 조건부 UPDATE였는데, 아직 조회된 적 없는(부트스트랩 전)
    // 판매자의 결제가 조용히 무시되는 경쟁 상태가 있었다 — 이번 전환으로 그 경쟁 상태 자체가 사라진다.)
    // "정원 초과 금지" 같은 지켜야 할 불변식이 없는 단순 누적이라 비관적 락은 필요 없다.
    // 네이티브 INSERT라 영속성 컨텍스트를 거치지 않으므로, 같은 트랜잭션 안에서 이후 findBySellerId로
    // 재조회할 때 stale 캐시를 안 보도록 clearAutomatically로 컨텍스트를 비운다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT INTO seller_revenue_summary "
            + "(seller_id, total_revenue, paid_count, refunded_count, created_at, updated_at) "
            + "VALUES (:sellerId, :amount, 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
            + "ON DUPLICATE KEY UPDATE "
            + "total_revenue = total_revenue + :amount, "
            + "paid_count = paid_count + 1, "
            + "updated_at = CURRENT_TIMESTAMP",
            nativeQuery = true)
    void incrementPaid(@Param("sellerId") Long sellerId, @Param("amount") Integer amount);

    // team/deadline-check 환불 처리 시 감소시킨다 — 환불된 결제들의 금액 합·건수를 한 번에 반영한다.
    // **전제**: incrementPaid가 위와 같이 upsert로 바뀐 뒤부터는, 결제 시점에 이미 요약 행이 만들어져
    // 있어야 한다(환불은 항상 이미 PAID였던 결제를 대상으로 하므로). 이 전제가 깨지는 유일한 경우는
    // "이번 upsert 전환 이전부터 있던 결제 이력"이 아직 백필(SellerRevenueSummaryBackfillService/
    // Runner, docs/db/seller_revenue_summary.md)되지 않은 채로 환불이 먼저 들어오는 것 — 이 메서드는
    // 여전히 조건부 UPDATE라 그 경우 대상 행이 없어 0 rows affected로 조용히 무시된다. 호출부
    // (TeamDeadlineService.processDeadline)가 이 리턴값이 0이면 WARN 로그를 남겨 드러나게 한다.
    // flushAutomatically=true가 특히 중요하다: 이 호출 직전에 TeamDeadlineService가
    // paidPayments.forEach(Payment::refund)로 여러 Payment 엔티티를 더티 상태로 만들어두는데,
    // flush 없이 clearAutomatically만 실행하면 그 변경이 flush되지 않은 채 컨텍스트에서
    // detach되어 DB에 반영되지 않고 유실된다(실제로 재현·확인된 버그).
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SellerRevenueSummary s SET "
            + "s.totalRevenue = s.totalRevenue - :refundedAmount, "
            + "s.paidCount = s.paidCount - :refundedCount, "
            + "s.refundedCount = s.refundedCount + :refundedCount, "
            + "s.updatedAt = CURRENT_TIMESTAMP "
            + "WHERE s.seller.id = :sellerId")
    int applyRefund(@Param("sellerId") Long sellerId, @Param("refundedAmount") Integer refundedAmount,
            @Param("refundedCount") Long refundedCount);
}
