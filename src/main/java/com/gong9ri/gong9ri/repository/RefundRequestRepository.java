package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.RefundRequest;
import com.gong9ri.gong9ri.entity.RefundRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long>, RefundRequestRepositoryCustom {

    // 솔로 구매 직접 환불 요청 중복 방지 — 같은 결제에 이미 처리 대기 중인 요청이 있으면 새로 만들지 않는다.
    boolean existsByPayment_IdAndStatus(Long paymentId, RefundRequestStatus status);
}
