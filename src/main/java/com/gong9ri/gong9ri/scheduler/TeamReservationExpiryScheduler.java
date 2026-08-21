package com.gong9ri.gong9ri.scheduler;

import com.gong9ri.gong9ri.service.TeamReservationExpiryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 미결제 참여 자동 만료 스케줄러(team/reservation-expiry,
 * docs/dev/ongoing/team-payment-enforcement.md). 1분마다 RECRUITING 팀 중 10분 이상 지난 미결제
 * 참여가 있는 팀을 스캔해, 팀별로 {@code TeamReservationExpiryService.processExpiredParticipations}를
 * 호출한다. team/deadline-check와 달리 외부 HTTP 호출(PortOne 등)이 없어 락을 오래 잡을 위험이 없으므로,
 * 이벤트 발행-구독으로 처리를 비동기 분리하지 않고 스캔 루프에서 그대로 동기 호출한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TeamReservationExpiryScheduler {

    private static final long FIXED_RATE_MS = 60_000L;

    private final TeamReservationExpiryService teamReservationExpiryService;

    @Scheduled(fixedRate = FIXED_RATE_MS)
    public void checkExpiredParticipations() {
        List<Long> teamIds = teamReservationExpiryService.findTeamIdsWithExpiredUnpaidParticipations();
        if (teamIds.isEmpty()) {
            return;
        }

        log.info("미결제 참여 자동 만료 스캔: 대상 teamCount={}", teamIds.size());
        for (Long teamId : teamIds) {
            teamReservationExpiryService.processExpiredParticipations(teamId);
        }
    }
}
