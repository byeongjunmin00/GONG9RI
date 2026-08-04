package com.gong9ri.gong9ri.scheduler;

import com.gong9ri.gong9ri.service.TeamDeadlineService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 공구팀 마감 체크 & 환불 트리거 스케줄러 (docs/policy/refund-trigger.md).
 * 1분마다 RECRUITING + deadline 지난 팀을 스캔하고, 팀별로 독립된 트랜잭션에서 FAILED 전환 + 환불을 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TeamDeadlineScheduler {

    private static final long FIXED_RATE_MS = 60_000L;

    private final TeamDeadlineService teamDeadlineService;

    @Scheduled(fixedRate = FIXED_RATE_MS)
    public void checkDeadlines() {
        List<Long> expiredTeamIds = teamDeadlineService.findExpiredRecruitingTeamIds();
        if (expiredTeamIds.isEmpty()) {
            return;
        }

        log.info("공구팀 마감 체크 스캔: 대상 teamCount={}", expiredTeamIds.size());
        for (Long teamId : expiredTeamIds) {
            teamDeadlineService.processDeadline(teamId);
        }
    }
}
