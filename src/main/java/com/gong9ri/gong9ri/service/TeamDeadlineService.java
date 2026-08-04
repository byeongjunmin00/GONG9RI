package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.PaymentStatus;
import com.gong9ri.gong9ri.entity.TeamStatus;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
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

        log.info("공구팀 마감 실패 처리 완료: teamId={}, refundedPaymentCount={}", teamId, paidPayments.size());
    }
}
