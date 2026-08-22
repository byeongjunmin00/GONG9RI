package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.PaymentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PaymentRepository extends JpaRepository<Payment, Long>, PaymentRepositoryCustom {

    // 마감 체크 스케줄러가 팀의 PAID 결제를 일괄 환불 대상으로 조회한다 — idx_team_status(team_id, status) 인덱스 활용.
    List<Payment> findByTeamIdAndStatus(Long teamId, PaymentStatus status);

    // PortOne 웹훅이 가리키는 결제 건을 pgPaymentId로 역조회한다(idx_pg_payment_id 인덱스 활용).
    Optional<Payment> findByPgPaymentId(String pgPaymentId);

    // 리뷰 작성 자격 검증 — 이 상품을 실제로 결제 완료(PAID)한 적이 있는 회원인지 확인한다.
    // (member_id, product_id, status) 복합 인덱스는 따로 없음 — 이 프로젝트 규모에서 성능 문제로
    // 드러나면 그때 추가한다, 실측 근거 없이 미리 만들지 않음.
    boolean existsByMemberIdAndProductIdAndStatus(Long memberId, Long productId, PaymentStatus status);

    // 상품 삭제 가드(product/admin) — 결제가 하나라도 있으면 삭제를 막는다.
    boolean existsByProduct_Id(Long productId);

    // 관리자 강제 삭제(product/admin) — 장난성 게시물처럼 결제·리뷰가 붙어도 지워야 할 때만 쓴다.
    @Transactional
    void deleteByProduct_Id(Long productId);

    // 참여 취소(team/leave) — 취소한 사람이 그 팀에 대해 PAID 결제를 갖고 있으면 환불 요청 자동 생성
    // 대상이다(docs/dev/ongoing/team-leave-and-refund-request.md).
    List<Payment> findByTeamIdAndMemberIdAndStatus(Long teamId, Long memberId, PaymentStatus status);

    // 관리자 회원 삭제 — 결제 이력이 하나라도 있으면 하드 삭제를 막는다(product/admin).
    boolean existsByMemberId(Long memberId);

    // 관리자 회원 활동 수치 배치 조회 (N+1 방지)
    @Query("SELECT p.member.id, COUNT(p) FROM Payment p WHERE p.member.id IN :memberIds GROUP BY p.member.id")
    List<Object[]> countPaymentsByMemberIds(@Param("memberIds") List<Long> memberIds);

    // 판매자 마이페이지 — 내가 판매한 상품들에 대한 결제 건과 구매자, 상품, 팀 정보 배치 패치 조회 (N+1 방지)
    @Query("SELECT p FROM Payment p JOIN FETCH p.product pr JOIN FETCH p.member m LEFT JOIN FETCH p.team t WHERE pr.seller.id = :sellerId AND p.status <> 'PENDING' AND p.status <> 'FAILED' ORDER BY p.paidAt DESC")
    List<Payment> findAllBySellerIdWithProductAndMemberAndTeam(@Param("sellerId") Long sellerId);

    // 주문번호 백필(admin-identifier-codes, IdentifierCodeBackfillService) — 이 컬럼이 nullable인
    // 동안 아직 채번되지 않은 기존 행만 골라낸다.
    @Query("SELECT p.id FROM Payment p WHERE p.orderNo IS NULL")
    List<Long> findIdsByOrderNoIsNull();
}
