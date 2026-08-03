package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.TeamJoinResponse;
import com.gong9ri.gong9ri.dto.TeamResponse;
import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.entity.TeamParticipation;
import com.gong9ri.gong9ri.entity.TeamStatus;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.TeamParticipationRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamService {

    private static final long TEAM_DURATION_DAYS = 7;

    private final GroupBuyTeamRepository groupBuyTeamRepository;
    private final TeamParticipationRepository teamParticipationRepository;
    private final ProductRepository productRepository;

    public List<TeamResponse> list(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return groupBuyTeamRepository.findByProductIdAndStatus(productId, TeamStatus.RECRUITING).stream()
                .map(TeamResponse::from)
                .toList();
    }

    @Transactional
    public TeamResponse create(MemberUserDetails principal, Long productId) {
        requireBuyer(principal);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        Member leader = principal.getMember();

        GroupBuyTeam team = new GroupBuyTeam(product, leader, product.getMaxParticipants(),
                LocalDateTime.now().plusDays(TEAM_DURATION_DAYS));
        GroupBuyTeam saved = groupBuyTeamRepository.save(team);
        teamParticipationRepository.save(new TeamParticipation(saved, leader));

        log.info("공구팀 신설 완료: teamId={}, productId={}, leaderId={}", saved.getId(), productId, leader.getId());
        return TeamResponse.from(saved);
    }

    @Transactional
    public TeamJoinResponse join(MemberUserDetails principal, Long teamId) {
        requireBuyer(principal);
        Member member = principal.getMember();

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

        log.info("공구팀 참가 완료: teamId={}, memberId={}, currentCount={}",
                teamId, member.getId(), team.getCurrentCount());
        return TeamJoinResponse.from(team);
    }

    private void requireBuyer(MemberUserDetails principal) {
        if (principal.getMember().getRole() != Role.BUYER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
