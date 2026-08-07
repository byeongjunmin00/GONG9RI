package com.gong9ri.gong9ri.repository;

import static com.gong9ri.gong9ri.entity.QPayment.payment;

import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.PaymentStatus;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;

public class PaymentRepositoryImpl implements PaymentRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public PaymentRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public Optional<Payment> findByIdWithDetails(Long id) {
        Payment result = queryFactory
                .selectFrom(payment)
                .join(payment.member).fetchJoin()
                .join(payment.product).fetchJoin()
                .leftJoin(payment.team).fetchJoin()
                .where(payment.id.eq(id))
                .fetchOne();
        return Optional.ofNullable(result);
    }

    @Override
    public List<Payment> findAllByMemberIdWithProduct(Long memberId) {
        return queryFactory
                .selectFrom(payment)
                .join(payment.product).fetchJoin()
                .where(payment.member.id.eq(memberId))
                .orderBy(payment.paidAt.desc())
                .fetch();
    }

    @Override
    public RevenueSummaryProjection findRevenueSummaryBySellerId(Long sellerId) {
        return queryFactory
                .select(Projections.constructor(RevenueSummaryProjectionImpl.class,
                        new CaseBuilder()
                                .when(payment.status.eq(PaymentStatus.PAID)).then(payment.amount)
                                .otherwise(0)
                                .sum().coalesce(0),
                        new CaseBuilder()
                                .when(payment.status.eq(PaymentStatus.PAID)).then(1L)
                                .otherwise(0L)
                                .sum().coalesce(0L),
                        new CaseBuilder()
                                .when(payment.status.eq(PaymentStatus.REFUNDED)).then(1L)
                                .otherwise(0L)
                                .sum().coalesce(0L)))
                .from(payment)
                .where(payment.product.seller.id.eq(sellerId))
                .fetchOne();
    }

    @Override
    public List<Long> findDistinctSellerIdsWithPayments() {
        return queryFactory
                .select(payment.product.seller.id)
                .from(payment)
                .distinct()
                .fetch();
    }
}
