package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.PaymentStatus;
import com.gong9ri.gong9ri.entity.TeamParticipation;
import com.gong9ri.gong9ri.entity.TeamStatus;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.TeamParticipationRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 미결제 참여 자동 만료(team/reservation-expiry, docs/dev/ongoing/team-payment-enforcement.md).
 * 참가/신설({@code TeamService.join}/{@code create})은 결제 완료 여부와 무관하게 자리를 즉시 반영하는
 * "예약 후 유예" 모델이라, 결제 페이지로 이동시켰는데도 사용자가 결제를 끝내지 않고 이탈하면 자리가
 * 영구히 묶일 수 있다. 이 서비스는 참가 시점({@code TeamParticipation.joinedAt}) 기준 10분이 지나도록
 * 그 팀+멤버 조합에 연결된 {@code PAID} 결제가 없으면, {@code TeamService.leave()}와 동일한 효과로
 * 그 참여를 자동 취소한다(정원 감소, 필요 시 리더 승계, 마지막 참여자면 팀 FAILED 전환). 결제가 애초에
 * PAID가 아니므로 환불 요청은 생성되지 않는다({@code TeamService.cancelParticipation}이 PAID 결제가
 * 있을 때만 {@code RefundRequestService.createFromTeamLeave}를 호출하기 때문에 별도 분기가 필요 없다).
 *
 * <p>스캔(읽기전용)과 실제 처리(팀별 독립 트랜잭션)를 분리하고, team/join과 동일한
 * {@code findByIdForUpdate} 비관적 락을 재사용해 만료 처리와 참가/취소 요청이 같은 팀 row에서
 * 직렬화되게 한다 — team/deadline-check와 동일한 패턴이다. 다만 이 기능은 외부 HTTP 호출(PortOne
 * 결제취소 등)이 없어서 락을 오래 잡을 위험이 없으므로, deadline-check처럼 이벤트 발행-구독으로 처리를
 * 비동기 분리할 필요는 없다 — 스케줄러가 팀별 처리 메서드를 그대로 동기 호출한다
 * ({@code TeamReservationExpiryScheduler}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamReservationExpiryService {

    private static final long EXPIRY_MINUTES = 10;

    private final GroupBuyTeamRepository groupBuyTeamRepository;
    private final TeamParticipationRepository teamParticipationRepository;
    private final PaymentRepository paymentRepository;
    private final TeamService teamService;

    public List<Long> findTeamIdsWithExpiredUnpaidParticipations() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(EXPIRY_MINUTES);
        return teamParticipationRepository.findTeamIdsWithParticipationBefore(TeamStatus.RECRUITING, cutoff);
    }

    // 팀 하나에 딸린 만료된 미결제 참여를 전부 취소한다 — 팀별로 독립된 트랜잭션. join()/leave()와
    // 같은 findByIdForUpdate 락을 재사용해, 처리 중 다른 참가/취소 요청과 같은 행에서 직렬화되게 한다.
    @Transactional
    public void processExpiredParticipations(Long teamId) {
        GroupBuyTeam team = groupBuyTeamRepository.findByIdForUpdate(teamId).orElse(null);
        if (team == null) {
            return;
        }
        // 스캔 시점과 락 획득 시점 사이에 상태가 바뀌었을 수 있어(예: 그사이 정원이 차서 SUCCESS 전환,
        // 또는 이미 FAILED) 방어적으로 재검증한다. RECRUITING이 아니면 이 팀은 처리 대상이 아니다 —
        // TeamService.leave()도 RECRUITING에서만 취소를 허용하므로 같은 전제를 유지한다.
        if (team.getStatus() != TeamStatus.RECRUITING) {
            return;
        }

        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(EXPIRY_MINUTES);
        List<TeamParticipation> candidates = teamParticipationRepository
                .findAllByTeamIdWithMemberOrderByJoinedAtAsc(teamId).stream()
                .filter(participation -> participation.getJoinedAt().isBefore(cutoff))
                .toList();

        int canceledCount = 0;
        for (TeamParticipation participation : candidates) {
            // 앞선 취소로 마지막 참여자가 빠져 팀이 FAILED로 전환됐다면 더 처리할 대상이 없다.
            if (team.getStatus() != TeamStatus.RECRUITING) {
                break;
            }

            Member member = participation.getMember();
            // 방어적 재검증: 이 트랜잭션 안에서 이미 처리됐거나(이론상 후보 목록에 중복 없음, 안전망),
            // 스캔 이후 결제가 PAID로 확정됐으면 건드리지 않는다 — 이 스캔의 후보 판정은 결제 상태를
            // 보지 않았으므로 최종 판정은 반드시 여기서 다시 해야 한다.
            if (!teamParticipationRepository.existsByTeamIdAndMemberId(teamId, member.getId())) {
                continue;
            }
            boolean hasPaidPayment = !paymentRepository
                    .findByTeamIdAndMemberIdAndStatus(teamId, member.getId(), PaymentStatus.PAID)
                    .isEmpty();
            if (hasPaidPayment) {
                continue;
            }

            teamService.cancelParticipation(team, member);
            canceledCount++;
        }

        if (canceledCount > 0) {
            log.info("미결제 참여 자동 만료 처리 완료: teamId={}, canceledCount={}", teamId, canceledCount);
        }
    }
}
