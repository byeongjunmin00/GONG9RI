package com.gong9ri.gong9ri.repository;

import static com.gong9ri.gong9ri.entity.QRefundRequest.refundRequest;

import com.gong9ri.gong9ri.entity.QProduct;
import com.gong9ri.gong9ri.entity.RefundRequest;
import com.gong9ri.gong9ri.entity.RefundRequestStatus;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

public class RefundRequestRepositoryImpl implements RefundRequestRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public RefundRequestRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    // 조인 없이 refund_request 행 하나만 잠근다 — GroupBuyTeamRepositoryImpl.findByIdForUpdate와 동일
    // 패턴. RefundRequestService.approve()/reject()가 이 트랜잭션에서 이 엔티티를 읽는 유일한 진입점
    // 이어야 한다(RefundRequestRepositoryCustom의 인터페이스 주석 참고 — 언락 조회 후 락만 추가하는
    // 방식은 실제로 동시성 버그였다).
    @Override
    public Optional<RefundRequest> findByIdForUpdate(Long id) {
        RefundRequest result = queryFactory
                .selectFrom(refundRequest)
                .where(refundRequest.id.eq(id))
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne();
        return Optional.ofNullable(result);
    }

    @Override
    public List<RefundRequest> findAllByRequesterIdWithPaymentAndProduct(Long requesterId) {
        return queryFactory
                .selectFrom(refundRequest)
                .join(refundRequest.payment).fetchJoin()
                .join(refundRequest.payment.product).fetchJoin()
                // 요청자도 함께 가져온다 — RefundRequestResponse.from이 요청자 이름·프로필 사진을 읽는데,
                // 이 목록만 fetch join이 빠져 있어 환불 요청 N건마다 member SELECT가 더 나가고 있었다
                // (판매자·관리자 목록 쿼리는 원래 requester를 fetch join 하고 있다).
                .join(refundRequest.requester).fetchJoin()
                .where(refundRequest.requester.id.eq(requesterId))
                .orderBy(refundRequest.requestedAt.desc())
                .fetch();
    }

    // seller.id로 필터링해야 해서(payment.product.seller, 3단계) 별도 alias로 QProduct를 새로 만들어
    // 조인 대상으로 명시한다 — 새로 만든 alias는 자체가 root라 기본 PathInits 예산이 그 지점부터 다시
    // 2단계 적용되므로 product.seller 접근이 문제없다(QRefundRequest의 기본 PathInits(DIRECT2)가
    // root로부터 2단계까지만 자동 초기화하는 QueryDSL 제약을 우회하는 패턴).
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

    @Override
    public Page<RefundRequest> findAllForAdmin(RefundRequestStatus status, Pageable pageable) {
        QProduct product = new QProduct("adminRefundProduct");
        BooleanExpression statusFilter = status != null ? refundRequest.status.eq(status) : null;

        List<RefundRequest> content = queryFactory
                .selectFrom(refundRequest)
                .join(refundRequest.payment).fetchJoin()
                .join(refundRequest.payment.product, product).fetchJoin()
                .join(refundRequest.requester).fetchJoin()
                .where(statusFilter)
                .orderBy(refundRequest.requestedAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // 개수는 fetch join 없이 따로 센다 — 조인해봐야 개수는 같고 비용만 는다.
        JPAQuery<Long> countQuery = queryFactory
                .select(refundRequest.count())
                .from(refundRequest)
                .where(statusFilter);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }
}
