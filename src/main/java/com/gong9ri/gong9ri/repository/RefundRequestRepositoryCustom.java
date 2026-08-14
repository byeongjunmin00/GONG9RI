package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.RefundRequest;
import java.util.List;
import java.util.Optional;

/**
 * RefundRequestRepository의 QueryDSL 기반 커스텀 쿼리(페치조인) — docs/dev/ongoing/querydsl-migration.md
 * 관례와 동일하게, payment·product·requester까지 fetch join해 N+1을 피한다. payment.product.seller
 * (root로부터 3단계)는 기본 PathInits(DIRECT2, 2단계까지만 자동 초기화) 제약으로 체이닝 접근이 안 돼
 * ({@link RefundRequestRepositoryImpl} 참고), 필요한 곳(판매자 스코핑 필터)에서만 별도 alias로 조인한다.
 */
public interface RefundRequestRepositoryCustom {

    // 승인/거절 처리(RefundRequestService.approve/reject) 전용 — 비관적 락을 걸며 조회한다
    // (GroupBuyTeamRepositoryImpl.findByIdForUpdate와 동일 패턴). 이 트랜잭션에서 이 엔티티를 읽는
    // 첫 조회가 곧 이 락 조회여야 한다 — Hibernate는 이미 영속성 컨텍스트에 있는 엔티티를 재조회해도
    // 필드 값을 자동으로 새로고침하지 않아서, 언락 조회 뒤에 락만 추가로 거는 방식은 그 사이 다른
    // 트랜잭션이 이미 커밋한 변경사항을 못 본다(직접 겪은 버그, RefundRequestConcurrencyTest 참고).
    // payment/product/seller는 여기서 fetch join하지 않고 지연로딩으로 확인한다 — 조인된 여러 테이블
    // 행을 한꺼번에 잠그는 걸 피하고 refund_request 행 하나에만 락을 걸기 위함.
    Optional<RefundRequest> findByIdForUpdate(Long id);

    // 구매자 마이페이지 — 본인이 요청한 환불 요청 전체(대기/승인/거절 포함).
    List<RefundRequest> findAllByRequesterIdWithPaymentAndProduct(Long requesterId);

    // 판매자 마이페이지 — 내가 등록한 상품에 대한 환불 요청 전체.
    List<RefundRequest> findAllBySellerIdWithPaymentAndProduct(Long sellerId);
}
