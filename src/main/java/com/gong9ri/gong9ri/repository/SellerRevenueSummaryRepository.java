package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.SellerRevenueSummary;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface SellerRevenueSummaryRepository
        extends JpaRepository<SellerRevenueSummary, Long>, SellerRevenueSummaryRepositoryCustom {

    Optional<SellerRevenueSummary> findBySellerId(Long sellerId);

    // 관리자 회원 삭제 — 다른 테이블이 참조하지 않는 leaf 데이터라 회원 삭제 시 함께 지운다(product/admin).
    // 삭제가 허용되는 회원은 이미 Product.existsBySeller_Id가 false라 이 요약 행이 있을 수 없지만,
    // 방어적으로 같이 정리한다.
    @Transactional
    void deleteBySellerId(Long sellerId);

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
    // native ON DUPLICATE KEY UPDATE upsert는 QueryDSL이 표현할 수 없는 영역이라 이번 전환 대상에서
    // 제외한다 — docs/dev/ongoing/querydsl-migration.md 참고.
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

    // 관리자 강제 삭제(product/admin) 후 보정 — 이 테이블은 결제마다 누적(incrementPaid)만 하는 집계라
    // 결제를 지워도 저절로 줄지 않는다. 남은 결제 기준으로 다시 계산한 값을 통째로 덮어쓴다.
    // 엔티티 세터가 아니라 UPDATE 쿼리로 바꾸는 건 이 테이블의 기존 규칙(원자적 UPDATE로만 변경)을
    // 그대로 따르기 위해서다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SellerRevenueSummary s "
            + "SET s.totalRevenue = :totalRevenue, s.paidCount = :paidCount, s.refundedCount = :refundedCount "
            + "WHERE s.seller.id = :sellerId")
    int overwrite(@Param("sellerId") Long sellerId,
            @Param("totalRevenue") Integer totalRevenue,
            @Param("paidCount") Long paidCount,
            @Param("refundedCount") Long refundedCount);
}
