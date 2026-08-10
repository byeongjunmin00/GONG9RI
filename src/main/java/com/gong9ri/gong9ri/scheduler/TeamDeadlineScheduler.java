package com.gong9ri.gong9ri.scheduler;

import com.gong9ri.gong9ri.event.TeamDeadlineDetectedEvent;
import com.gong9ri.gong9ri.service.TeamDeadlineService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 공구팀 마감 체크 & 환불 트리거 스케줄러 (docs/policy/refund-trigger.md).
 * 1분마다 RECRUITING + deadline 지난 팀을 스캔하고, 팀 id별로 {@code TeamDeadlineDetectedEvent}만
 * 발행한다 — 상태전환+환불 처리는 직접 호출하지 않고 {@code TeamDeadlineEventListener}(비동기)에게 넘긴다
 * (docs/dev/ongoing/refund-event-messaging.md).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TeamDeadlineScheduler {

    private static final long FIXED_RATE_MS = 60_000L;

    private final TeamDeadlineService teamDeadlineService;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(fixedRate = FIXED_RATE_MS)
    public void checkDeadlines() {
        List<Long> expiredTeamIds = teamDeadlineService.findExpiredRecruitingTeamIds();
        if (expiredTeamIds.isEmpty()) {
            return;
        }

        log.info("공구팀 마감 체크 스캔: 대상 teamCount={}", expiredTeamIds.size());
        for (Long teamId : expiredTeamIds) {
            eventPublisher.publishEvent(new TeamDeadlineDetectedEvent(teamId));
        }
    }
}
