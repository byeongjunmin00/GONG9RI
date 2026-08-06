package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.PaymentStatus;
import com.gong9ri.gong9ri.entity.TeamStatus;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.SellerRevenueSummaryRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공구팀 마감 체크 & 환불 트리거 (docs/policy/refund-trigger.md).
 * 스캔(findExpiredRecruitingTeamIds)과 실제 전환(processDeadline)을 분리해,
 * "팀 단위 트랜잭션"으로 처리한다 — 전체 대상 팀을 하나의 트랜잭션으로 묶지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamDeadlineService {

    private final GroupBuyTeamRepository groupBuyTeamRepository;
    private final PaymentRepository paymentRepository;
    private final SellerRevenueSummaryRepository sellerRevenueSummaryRepository;

    public List<Long> findExpiredRecruitingTeamIds() {
        return groupBuyTeamRepository.findIdsByStatusAndDeadlineBefore(TeamStatus.RECRUITING, LocalDateTime.now());
    }

    // 팀 하나를 FAILED로 전환하고 그 팀의 PAID 결제를 전부 REFUNDED로 전환한다 — 팀별로 독립된 트랜잭션.
    // join(TeamService)과 같은 findByIdForUpdate 락을 재사용해, 마감 직전 참가 시도와 이 처리가 같은 행에서 직렬화되게 한다.
    @Transactional
    public void processDeadline(Long teamId) {
        GroupBuyTeam team = groupBuyTeamRepository.findByIdForUpdate(teamId).orElse(null);
        if (team == null) {
            return;
        }

        // 스캔 시점과 락 획득 시점 사이에 상태가 바뀌었을 수 있어(예: 방금 참가로 SUCCESS 전환) 방어적으로 재검증한다.
        if (team.getStatus() != TeamStatus.RECRUITING || !team.getDeadline().isBefore(LocalDateTime.now())) {
            return;
        }

        team.fail();

        List<Payment> paidPayments = paymentRepository.findByTeamIdAndStatus(teamId, PaymentStatus.PAID);
        paidPayments.forEach(Payment::refund);

        // 실제로 환불이 발생한 경우에만 판매자 수익 요약(seller_revenue_summary)을 감소시킨다 — 이 메서드는
        // 팀별 독립 트랜잭션이므로 전체 배치가 끝난 뒤가 아니라 이 팀 처리 시점에 즉시 반영해야 한다
        // (docs/db/seller_revenue_summary.md). 환불된 결제들의 금액 합·건수를 한 번에 집계해서 반영한다.
        if (!paidPayments.isEmpty()) {
            int refundedAmount = paidPayments.stream().mapToInt(Payment::getAmount).sum();
            long refundedCount = paidPayments.size();
            Long sellerId = team.getProduct().getSeller().getId();
            int rowsAffected = sellerRevenueSummaryRepository.applyRefund(sellerId, refundedAmount, refundedCount);
            // applyRefund의 전제(결제 시점에 이미 요약 행이 있어야 함)가 깨진 경우 — upsert 전환
            // 이전부터 있던 결제 이력이 아직 백필되지 않은 판매자로 추정된다(SellerRevenueSummaryRepository
            // 참고). 조용히 무시되면 그 판매자의 요약값이 영구히 틀어지므로 WARN으로 드러낸다.
            if (rowsAffected == 0) {
                log.warn("판매자 수익 요약 환불 반영 실패(요약 행 없음, 백필 필요 추정): sellerId={}, teamId={}, "
                        + "refundedAmount={}, refundedCount={}", sellerId, teamId, refundedAmount, refundedCount);
            }
        }

        log.info("공구팀 마감 실패 처리 완료: teamId={}, refundedPaymentCount={}", teamId, paidPayments.size());
    }
}
