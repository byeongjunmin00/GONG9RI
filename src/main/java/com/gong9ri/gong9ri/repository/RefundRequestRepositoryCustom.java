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

    // 승인/거절 처리 전 판매자 소유권 확인, 응답 DTO 매핑에 필요한 연관관계까지 한 번에 가져온다.
    Optional<RefundRequest> findByIdWithPaymentAndProduct(Long id);

    // 구매자 마이페이지 — 본인이 요청한 환불 요청 전체(대기/승인/거절 포함).
    List<RefundRequest> findAllByRequesterIdWithPaymentAndProduct(Long requesterId);

    // 판매자 마이페이지 — 내가 등록한 상품에 대한 환불 요청 전체.
    List<RefundRequest> findAllBySellerIdWithPaymentAndProduct(Long sellerId);
}
