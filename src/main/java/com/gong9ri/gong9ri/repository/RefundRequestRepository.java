package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.RefundRequest;
import com.gong9ri.gong9ri.entity.RefundRequestStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long>, RefundRequestRepositoryCustom {

    // 솔로 구매 직접 환불 요청 중복 방지 — 같은 결제에 이미 처리 대기 중인 요청이 있으면 새로 만들지 않는다.
    boolean existsByPayment_IdAndStatus(Long paymentId, RefundRequestStatus status);

    // 마감 스윕(team/deadline-check) 대상에서, 참여 취소로 이미 대기 중인 환불 요청이 걸린 결제를
    // 제외하기 위한 조회 — payment id 목록 중 status(대개 PENDING)에 해당하는 요청만 뽑는다.
    List<RefundRequest> findByPayment_IdInAndStatus(List<Long> paymentIds, RefundRequestStatus status);
}
