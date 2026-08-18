package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.PaymentStatus;
import com.gong9ri.gong9ri.entity.RefundRequestStatus;
import com.gong9ri.gong9ri.entity.TeamStatus;
import com.gong9ri.gong9ri.event.TeamPaymentsRefundRequestedEvent;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.RefundRequestRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공구팀 마감 체크 & 환불 트리거 (docs/policy/refund-trigger.md).
 * 스캔(findExpiredRecruitingTeamIds)과 실제 전환(processDeadline)을 분리해,
 * "팀 단위 트랜잭션"으로 처리한다 — 전체 대상 팀을 하나의 트랜잭션으로 묶지 않는다.
 *
 * <p><b>PortOne 연동 이후(docs/dev/payment/portone/design.md)</b>: 이 서비스는 더 이상 결제 상태를
 * 직접 REFUNDED로 바꾸지 않는다 — 그 전에는 {@code Payment.refund()}(DB 상태만 전환)를 이 트랜잭션
 * 안에서 바로 호출했지만, 이제 실제 환불은 PortOne 결제취소 API 호출을 거쳐야 하고 그 호출(외부 HTTP,
 * 지연 가능)을 이 메서드가 잡고 있는 비관적 락({@code findByIdForUpdate}) 트랜잭션 안에서 하면 락을
 * 오래 잡게 된다. 그래서 이 메서드는 "환불 대상 결제 id 목록"만 커밋 후 이벤트로 넘기고
 * ({@code TeamPaymentsRefundRequestedEvent}), 실제 취소 호출과 상태 전환은
 * {@code TeamPaymentsRefundRequestedEventListener} → {@code PaymentRefundService}가 트랜잭션 밖에서
 * 담당한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamDeadlineService {

    private final GroupBuyTeamRepository groupBuyTeamRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRequestRepository refundRequestRepository;
    private final ApplicationEventPublisher eventPublisher;

    public List<Long> findExpiredRecruitingTeamIds() {
        return groupBuyTeamRepository.findIdsByStatusAndDeadlineBefore(TeamStatus.RECRUITING, LocalDateTime.now());
    }

    // 팀 하나를 FAILED로 전환하고, 그 팀의 PAID 결제에 대해 환불(포트원 취소) 요청 이벤트를 발행한다
    // — 팀별로 독립된 트랜잭션. join(TeamService)과 같은 findByIdForUpdate 락을 재사용해, 마감 직전
    // 참가 시도와 이 처리가 같은 행에서 직렬화되게 한다.
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
        List<Long> paymentIds = paidPayments.stream().map(Payment::getId).toList();

        // 참여 취소(team/leave)로 이미 대기 중(PENDING)인 환불 요청이 걸린 결제는 이 마감 스윕에서
        // 제외한다 — 판매자의 승인/거절 결정을 기다리는 중인 요청을, 마감 스윕이 먼저 가로채 취소해
        // 버리면 그 RefundRequest가 영구히 고아 상태로 남는다(docs/dev/refund/request/design.md
        // "매우 중요한 제약" 옆 FAILED 케이스 분석 참고). REJECTED/APPROVED로 이미 결정 난 요청이 있는
        // 결제는 제외 대상이 아니다 — 그 결정과 무관하게 결제가 여전히 PAID라면 정상적인 마감 환불
        // 대상이다.
        Set<Long> pendingPaymentIds = refundRequestRepository
                .findByPayment_IdInAndStatus(paymentIds, RefundRequestStatus.PENDING).stream()
                .map(refundRequest -> refundRequest.getPayment().getId())
                .collect(Collectors.toSet());
        List<Long> refundTargetPaymentIds = paymentIds.stream()
                .filter(paymentId -> !pendingPaymentIds.contains(paymentId))
                .toList();

        if (!refundTargetPaymentIds.isEmpty()) {
            // 여기서는 결제 상태·판매자 수익 요약을 건드리지 않는다 — 포트원 취소 API 응답을 확인한
            // 뒤에만(PaymentRefundService) 실제 반영한다. 이 이벤트는 이 트랜잭션이 커밋된 이후에만
            // 소비된다(TeamPaymentsRefundRequestedEventListener, AFTER_COMMIT).
            eventPublisher.publishEvent(new TeamPaymentsRefundRequestedEvent(teamId, refundTargetPaymentIds));
        }

        log.info("공구팀 마감 실패 처리 완료: teamId={}, refundRequestedPaymentCount={}, "
                        + "excludedPendingRefundRequestCount={}",
                teamId, refundTargetPaymentIds.size(), pendingPaymentIds.size());
    }
}
