package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.PaymentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long>, PaymentRepositoryCustom {

    // 마감 체크 스케줄러가 팀의 PAID 결제를 일괄 환불 대상으로 조회한다 — idx_team_status(team_id, status) 인덱스 활용.
    List<Payment> findByTeamIdAndStatus(Long teamId, PaymentStatus status);

    // PortOne 웹훅이 가리키는 결제 건을 pgPaymentId로 역조회한다(idx_pg_payment_id 인덱스 활용).
    Optional<Payment> findByPgPaymentId(String pgPaymentId);
}
