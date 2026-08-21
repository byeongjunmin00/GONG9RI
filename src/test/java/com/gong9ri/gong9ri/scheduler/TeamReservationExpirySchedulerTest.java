package com.gong9ri.gong9ri.scheduler;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gong9ri.gong9ri.service.TeamReservationExpiryService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 스케줄러가 스캔된 팀 id마다 {@code processExpiredParticipations}를 호출하는지 검증한다.
 * DB/Spring 컨텍스트 없는 순수 단위 테스트(Mockito) — 실제 취소 효과(정원 감소·리더 승계·FAILED 전환)는
 * {@code service/TeamReservationExpiryServiceTest}에서 검증한다. team/deadline-check와 달리 이 기능은
 * 외부 HTTP 호출이 없어 이벤트 발행-구독으로 처리를 분리하지 않고 스캔 루프에서 그대로 동기 호출한다
 * (TeamReservationExpiryScheduler 클래스 Javadoc 참고).
 */
@ExtendWith(MockitoExtension.class)
class TeamReservationExpirySchedulerTest {

    @Mock
    private TeamReservationExpiryService teamReservationExpiryService;

    @Test
    @DisplayName("checkExpiredParticipations는 스캔된 각 팀 id마다 processExpiredParticipations를 호출한다")
    void checkExpiredParticipations_processesEachScannedTeam() {
        when(teamReservationExpiryService.findTeamIdsWithExpiredUnpaidParticipations())
                .thenReturn(List.of(1L, 2L));
        TeamReservationExpiryScheduler scheduler = new TeamReservationExpiryScheduler(teamReservationExpiryService);

        scheduler.checkExpiredParticipations();

        verify(teamReservationExpiryService).processExpiredParticipations(1L);
        verify(teamReservationExpiryService).processExpiredParticipations(2L);
    }

    @Test
    @DisplayName("스캔 결과가 없으면 처리 메서드를 호출하지 않는다")
    void checkExpiredParticipations_noExpiredTeams_processesNothing() {
        when(teamReservationExpiryService.findTeamIdsWithExpiredUnpaidParticipations()).thenReturn(List.of());
        TeamReservationExpiryScheduler scheduler = new TeamReservationExpiryScheduler(teamReservationExpiryService);

        scheduler.checkExpiredParticipations();

        verify(teamReservationExpiryService, never()).processExpiredParticipations(org.mockito.ArgumentMatchers.any());
    }
}
