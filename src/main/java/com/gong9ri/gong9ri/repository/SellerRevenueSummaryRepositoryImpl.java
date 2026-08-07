package com.gong9ri.gong9ri.repository;

import static com.gong9ri.gong9ri.entity.QSellerRevenueSummary.sellerRevenueSummary;

import com.querydsl.core.types.dsl.DateTimeExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;

public class SellerRevenueSummaryRepositoryImpl implements SellerRevenueSummaryRepositoryCustom {

    private final EntityManager entityManager;
    private final JPAQueryFactory queryFactory;

    public SellerRevenueSummaryRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public int applyRefund(Long sellerId, Integer refundedAmount, Long refundedCount) {
        // 원래 @Modifying(flushAutomatically = true)와 동일하게, 벌크 UPDATE 실행 전에 대기 중인
        // 변경(예: Payment::refund로 더티가 된 엔티티)을 먼저 반영한다.
        entityManager.flush();
        long updated = queryFactory
                .update(sellerRevenueSummary)
                .set(sellerRevenueSummary.totalRevenue, sellerRevenueSummary.totalRevenue.subtract(refundedAmount))
                .set(sellerRevenueSummary.paidCount, sellerRevenueSummary.paidCount.subtract(refundedCount))
                .set(sellerRevenueSummary.refundedCount, sellerRevenueSummary.refundedCount.add(refundedCount))
                .set(sellerRevenueSummary.updatedAt, DateTimeExpression.currentTimestamp(LocalDateTime.class))
                .where(sellerRevenueSummary.seller.id.eq(sellerId))
                .execute();
        // 원래 @Modifying(clearAutomatically = true)와 동일하게, 벌크 UPDATE 후 영속성 컨텍스트를 비워
        // 이후 조회가 stale 캐시를 보지 않도록 한다.
        entityManager.clear();
        return (int) updated;
    }
}
