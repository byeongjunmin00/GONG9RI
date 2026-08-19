package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.TeamCreateRequest;
import com.gong9ri.gong9ri.dto.TeamJoinResponse;
import com.gong9ri.gong9ri.dto.TeamParticipantResponse;
import com.gong9ri.gong9ri.dto.TeamResponse;
import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.PaymentStatus;
import com.gong9ri.gong9ri.entity.PriceTier;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.entity.TeamParticipation;
import com.gong9ri.gong9ri.entity.TeamStatus;
import com.gong9ri.gong9ri.event.TeamCapacityChangedEvent;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.PriceTierRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.TeamParticipationRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamService {

    private static final long TEAM_DURATION_DAYS = 7;
    private static final String STRATEGY_ATOMIC = "atomic";

    private final GroupBuyTeamRepository groupBuyTeamRepository;
    private final TeamParticipationRepository teamParticipationRepository;
    private final ProductRepository productRepository;
    private final PriceTierRepository priceTierRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRequestService refundRequestService;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationPublisher notificationPublisher;

    // team/join 동시성 전략 토글(lock|atomic) — docs/logs/team/crud/003-atomic-comparison.md
    // 필드 주입: @RequiredArgsConstructor(final 필드)로는 Lombok이 @Value를 생성자 파라미터로
    // 복사해준다는 보장이 없어서(lombok.config 설정 필요), 단순 설정값 하나는 필드 주입으로 안전하게 받는다.
    @Value("${team.join-strategy:lock}")
    private String joinStrategy;

    public List<TeamResponse> list(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return groupBuyTeamRepository.findByProductIdAndStatus(productId, TeamStatus.RECRUITING).stream()
                .map(TeamResponse::from)
                .toList();
    }

    @Transactional
    public TeamResponse create(MemberUserDetails principal, Long productId, TeamCreateRequest request) {
        requireBuyer(principal);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        if (product.isNotYetOpen()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_YET_OPEN);
        }
        Member leader = principal.getMember();

        Integer targetParticipants = request.targetParticipants();
        boolean matchesPriceTier = priceTierRepository.findByProductIdOrderByMinCountAsc(productId).stream()
                .map(PriceTier::getMinCount)
                .anyMatch(targetParticipants::equals);
        if (!matchesPriceTier) {
            throw new BusinessException(ErrorCode.INVALID_TARGET_PARTICIPANTS);
        }

        GroupBuyTeam team = new GroupBuyTeam(product, leader, targetParticipants,
                LocalDateTime.now().plusDays(TEAM_DURATION_DAYS));
        GroupBuyTeam saved = groupBuyTeamRepository.save(team);
        teamParticipationRepository.save(new TeamParticipation(saved, leader));

        log.info("공구팀 신설 완료: teamId={}, productId={}, leaderId={}, targetParticipants={}",
                saved.getId(), productId, leader.getId(), targetParticipants);
        return TeamResponse.from(saved);
    }

    @Transactional
    public TeamJoinResponse join(MemberUserDetails principal, Long teamId) {
        requireBuyer(principal);
        Member member = principal.getMember();

        if (STRATEGY_ATOMIC.equals(joinStrategy)) {
            return joinAtomic(teamId, member);
        }
        return joinWithLock(teamId, member);
    }

    private TeamJoinResponse joinWithLock(Long teamId, Member member) {
        // 비관적 락으로 이 팀 row를 잠근다 — 이후 검증·증가는 이 잠금이 풀릴 때까지 다른 요청과 직렬화된다.
        GroupBuyTeam team = groupBuyTeamRepository.findByIdForUpdate(teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND));

        if (teamParticipationRepository.existsByTeamIdAndMemberId(teamId, member.getId())) {
            throw new BusinessException(ErrorCode.ALREADY_JOINED);
        }

        if (team.getCurrentCount() >= team.getMaxParticipants()) {
            throw new BusinessException(ErrorCode.TEAM_FULL);
        }

        team.increaseParticipant();
        teamParticipationRepository.save(new TeamParticipation(team, member));

        TeamJoinResponse response = TeamJoinResponse.from(team);
        eventPublisher.publishEvent(new TeamCapacityChangedEvent(team.getProduct().getId(), response));
        log.info("공구팀 참가 완료(lock): teamId={}, memberId={}, currentCount={}",
                teamId, member.getId(), team.getCurrentCount());
        publishTeamSucceededIfNeeded(team);
        return response;
    }

    // 비관적 락 없이, 조건부 UPDATE(incrementIfCapacity)의 원자성에만 의존하는 대안 경로 — 성능 비교용.
    // 정원 증가(UPDATE)를 먼저 시도한다 — INSERT를 먼저 하면 FK 체크 때문에 이 트랜잭션이 team row에
    // 공유 락을 잡은 채로 UPDATE의 배타 락 승급을 기다리게 되고, 여러 스레드가 동시에 이 순서를 밟으면
    // 서로의 배타 락 승급을 기다리다 데드락이 난다(실제로 재현됨). UPDATE를 먼저 해서 이 트랜잭션이
    // team row의 배타 락을 먼저 확보해두면, 뒤이은 INSERT의 FK 공유 락 요청은 자기 자신의 락이라
    // 즉시 허용된다.
    private TeamJoinResponse joinAtomic(Long teamId, Member member) {
        if (!groupBuyTeamRepository.existsById(teamId)) {
            throw new BusinessException(ErrorCode.TEAM_NOT_FOUND);
        }

        int updated = groupBuyTeamRepository.incrementIfCapacity(teamId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.TEAM_FULL);
        }

        try {
            teamParticipationRepository.save(
                    new TeamParticipation(groupBuyTeamRepository.getReferenceById(teamId), member));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ALREADY_JOINED);
        }

        GroupBuyTeam team = groupBuyTeamRepository.findById(teamId).orElseThrow();
        TeamJoinResponse response = TeamJoinResponse.from(team);
        eventPublisher.publishEvent(new TeamCapacityChangedEvent(team.getProduct().getId(), response));
        log.info("공구팀 참가 완료(atomic): teamId={}, memberId={}, currentCount={}",
                teamId, member.getId(), team.getCurrentCount());
        publishTeamSucceededIfNeeded(team);
        return response;
    }

    /**
     * 이번 참가로 팀이 정원을 채워 성사됐다면 참여자 전원 + 판매자에게 알림을 발행한다(notification).
     *
     * <p>참가 경로가 두 개(비관적 락 / 원자적 UPDATE)라 양쪽에서 공통으로 부른다. 상태가 이번에 막
     * SUCCESS로 바뀐 그 호출에서만 참이므로(이후 참가는 TEAM_FULL로 막힌다) 같은 팀에 알림이 두 번
     * 생기지 않는다.
     */
    private void publishTeamSucceededIfNeeded(GroupBuyTeam team) {
        if (team.getStatus() != TeamStatus.SUCCESS) {
            return;
        }
        notificationPublisher.teamSucceeded(
                teamParticipationRepository.findMemberIdsByTeamId(team.getId()),
                team.getProduct().getSeller().getId(),
                team.getId(),
                team.getProduct().getId(),
                team.getProduct().getName());
    }

    /**
     * 공구팀 참여 취소(team/leave) — join()과 대칭적인 API다. 취소 가능 조건: 로그인한 구매자가 그
     * 팀의 현재 참여자여야 하고, 팀 상태가 RECRUITING이어야 하며(그 외 상태는 거절 —
     * {@code TEAM_NOT_RECRUITING}), 그 팀의 마지막 남은 참여자가 아니어야 한다(사용자 확인 —
     * {@code LAST_PARTICIPANT_CANNOT_LEAVE}: 마지막 1명은 참여 취소·환불 모두 불가, 팀 실패 처리는
     * {@code team/deadline-check} 스케줄러 경로로만 일어난다). join()의 락 경로
     * ({@code findByIdForUpdate})를 그대로 재사용해 동시성을 제어한다(취소와 동시에 다른 사람이
     * 참가를 시도하는 경합 방지).
     *
     * <p>한 트랜잭션 안에서: 참여 기록 제거 → 정원(currentCount) 감소(자리 즉시 반환) → 취소한 사람이
     * 리더였다면 그다음 최초 참가자에게 리더 승계 → 취소한 사람의 PAID 결제가 있으면 환불 요청 자동
     * 생성({@code RefundRequestService.createFromTeamLeave}). 마지막 참여자는 위 가드로 이 메서드에
     * 도달하기 전에 이미 거절되므로, {@code decreaseParticipant()}가 currentCount를 0으로 만들 일은
     * 이 경로에서 없다(리더 승계 로직상 마지막 1명은 항상 그 시점의 리더라서, 이 가드 하나로 "리더가
     * 마지막으로 나가면서 leader 필드가 삭제된 참여자를 계속 가리키는" 문제까지 함께 막힌다).
     */
    @Transactional
    public TeamJoinResponse leave(MemberUserDetails principal, Long teamId) {
        requireBuyer(principal);
        Member member = principal.getMember();

        GroupBuyTeam team = groupBuyTeamRepository.findByIdForUpdate(teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND));

        if (!teamParticipationRepository.existsByTeamIdAndMemberId(teamId, member.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (team.getStatus() != TeamStatus.RECRUITING) {
            throw new BusinessException(ErrorCode.TEAM_NOT_RECRUITING);
        }
        if (team.getCurrentCount() <= 1) {
            throw new BusinessException(ErrorCode.LAST_PARTICIPANT_CANNOT_LEAVE);
        }

        boolean wasLeader = team.getLeader().getId().equals(member.getId());

        teamParticipationRepository.deleteByTeamIdAndMemberId(teamId, member.getId());
        team.decreaseParticipant();

        if (wasLeader && team.getStatus() != TeamStatus.FAILED) {
            teamParticipationRepository.findFirstByTeamIdOrderByJoinedAtAsc(teamId)
                    .ifPresent(next -> team.changeLeader(next.getMember()));
        }

        List<Payment> paidPayments =
                paymentRepository.findByTeamIdAndMemberIdAndStatus(teamId, member.getId(), PaymentStatus.PAID);
        if (!paidPayments.isEmpty()) {
            boolean autoRefund = team.getProduct().isAutoRefundOnCancel();
            for (Payment payment : paidPayments) {
                refundRequestService.createFromTeamLeave(payment, member, autoRefund);
            }
        }

        TeamJoinResponse response = TeamJoinResponse.from(team);
        eventPublisher.publishEvent(new TeamCapacityChangedEvent(team.getProduct().getId(), response));
        log.info("공구팀 참여 취소 완료: teamId={}, memberId={}, currentCount={}, wasLeader={}, refundRequestCount={}",
                teamId, member.getId(), team.getCurrentCount(), wasLeader, paidPayments.size());
        return response;
    }

    /**
     * 팀 참여자 목록(마스킹된 이름/팀장 여부/참여 시각)을 리더 우선 + joinedAt 오름차순으로 반환한다.
     * 비로그인 사용자도 호출할 수 있는 공개 조회다(SecurityConfig permitAll, docs/dev/team/crud/changes/ 참고).
     */
    public List<TeamParticipantResponse> participants(Long teamId) {
        if (!groupBuyTeamRepository.existsById(teamId)) {
            throw new BusinessException(ErrorCode.TEAM_NOT_FOUND);
        }

        List<TeamParticipation> participations =
                teamParticipationRepository.findAllByTeamIdWithMemberOrderByJoinedAtAsc(teamId);

        // joinedAt 오름차순으로 이미 조회했으므로, 리더 여부만 안정 정렬(stable sort)로 앞으로 끌어오면
        // "리더 먼저, 이후 참여 순서" 규칙이 완성된다.
        return participations.stream()
                .sorted(Comparator.comparing(TeamService::isLeaderOf).reversed())
                .map(p -> TeamParticipantResponse.from(p, isLeaderOf(p)))
                .toList();
    }

    private static boolean isLeaderOf(TeamParticipation participation) {
        return participation.getMember().getId().equals(participation.getTeam().getLeader().getId());
    }

    private void requireBuyer(MemberUserDetails principal) {
        if (principal.getMember().getRole() != Role.BUYER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
