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

    // 리뷰 작성 자격 검증 — 이 상품을 실제로 결제 완료(PAID)한 적이 있는 회원인지 확인한다.
    // (member_id, product_id, status) 복합 인덱스는 따로 없음 — 이 프로젝트 규모에서 성능 문제로
    // 드러나면 그때 추가한다, 실측 근거 없이 미리 만들지 않음.
    boolean existsByMemberIdAndProductIdAndStatus(Long memberId, Long productId, PaymentStatus status);

    // 참여 취소(team/leave) — 취소한 사람이 그 팀에 대해 PAID 결제를 갖고 있으면 환불 요청 자동 생성
    // 대상이다(docs/dev/ongoing/team-leave-and-refund-request.md).
    List<Payment> findByTeamIdAndMemberIdAndStatus(Long teamId, Long memberId, PaymentStatus status);

    // 관리자 회원 삭제 — 결제 이력이 하나라도 있으면 하드 삭제를 막는다(product/admin).
    boolean existsByMemberId(Long memberId);
}
