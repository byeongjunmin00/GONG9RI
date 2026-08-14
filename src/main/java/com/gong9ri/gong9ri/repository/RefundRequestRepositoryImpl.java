package com.gong9ri.gong9ri.repository;

import static com.gong9ri.gong9ri.entity.QRefundRequest.refundRequest;

import com.gong9ri.gong9ri.entity.QProduct;
import com.gong9ri.gong9ri.entity.RefundRequest;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;

public class RefundRequestRepositoryImpl implements RefundRequestRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public RefundRequestRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    // payment·product까지는 fetch join(응답 DTO 매핑에 필요). seller는 QRefundRequest의 기본
    // PathInits(DIRECT2, 자동 초기화가 root로부터 2단계까지만 — refundRequest -> payment -> product)를
    // 넘어서는 3단계 경로(payment.product.seller)라 그대로 체이닝하면 NPE가 난다(QueryDSL의 알려진
    // 제약, 실측 기록: docs/logs/refund/request/). 이 메서드를 쓰는 소유권 확인(RefundRequestService.
    // findWithOwnerCheck)은 단건 조회라 seller를 지연로딩으로 한 번 더 SELECT해도 N+1 문제가 아니다.
    @Override
    public Optional<RefundRequest> findByIdWithPaymentAndProduct(Long id) {
        RefundRequest result = queryFactory
                .selectFrom(refundRequest)
                .join(refundRequest.payment).fetchJoin()
                .join(refundRequest.payment.product).fetchJoin()
                .join(refundRequest.requester).fetchJoin()
                .where(refundRequest.id.eq(id))
                .fetchOne();
        return Optional.ofNullable(result);
    }

    @Override
    public List<RefundRequest> findAllByRequesterIdWithPaymentAndProduct(Long requesterId) {
        return queryFactory
                .selectFrom(refundRequest)
                .join(refundRequest.payment).fetchJoin()
                .join(refundRequest.payment.product).fetchJoin()
                .where(refundRequest.requester.id.eq(requesterId))
                .orderBy(refundRequest.requestedAt.desc())
                .fetch();
    }

    // seller.id로 필터링해야 해서(payment.product.seller, 3단계) 별도 alias로 QProduct를 새로 만들어
    // 조인 대상으로 명시한다 — 새로 만든 alias는 자체가 root라 기본 PathInits 예산이 그 지점부터 다시
    // 2단계 적용되므로 product.seller 접근이 문제없다(위 findByIdWithPaymentAndProduct 주석과 동일한
    // QueryDSL 제약 회피 패턴).
    @Override
    public List<RefundRequest> findAllBySellerIdWithPaymentAndProduct(Long sellerId) {
        QProduct product = new QProduct("refundRequestProduct");

        return queryFactory
                .selectFrom(refundRequest)
                .join(refundRequest.payment).fetchJoin()
                .join(refundRequest.payment.product, product).fetchJoin()
                .join(refundRequest.requester).fetchJoin()
                .where(product.seller.id.eq(sellerId))
                .orderBy(refundRequest.requestedAt.desc())
                .fetch();
    }
}
