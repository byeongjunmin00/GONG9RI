package com.gong9ri.gong9ri.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gong9ri.gong9ri.event.TeamDeadlineDetectedEvent;
import com.gong9ri.gong9ri.service.TeamDeadlineService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 스케줄러가 마감 지난 팀을 직접 처리(processDeadline 직접 호출)하지 않고 팀 id별로
 * {@code TeamDeadlineDetectedEvent}만 발행하는지 검증한다(docs/dev/ongoing/refund-event-messaging.md 태스크 1).
 * DB/Spring 컨텍스트 없는 순수 단위 테스트(Mockito) — 실제 이벤트 처리(비동기 리스너 경유 상태전환+환불)는
 * event 패키지의 통합 테스트에서 별도로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class TeamDeadlineSchedulerTest {

    @Mock
    private TeamDeadlineService teamDeadlineService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("checkDeadlines는 스캔된 각 팀 id로 이벤트만 발행하고, processDeadline을 직접 호출하지 않는다")
    void checkDeadlines_publishesEventPerTeam_andNeverCallsProcessDeadlineDirectly() {
        when(teamDeadlineService.findExpiredRecruitingTeamIds()).thenReturn(List.of(1L, 2L));
        TeamDeadlineScheduler scheduler = new TeamDeadlineScheduler(teamDeadlineService, eventPublisher);

        scheduler.checkDeadlines();

        verify(eventPublisher).publishEvent(new TeamDeadlineDetectedEvent(1L));
        verify(eventPublisher).publishEvent(new TeamDeadlineDetectedEvent(2L));
        verify(teamDeadlineService, never()).processDeadline(any());
    }

    @Test
    @DisplayName("스캔 결과가 없으면 이벤트를 발행하지 않는다")
    void checkDeadlines_noExpiredTeams_publishesNothing() {
        when(teamDeadlineService.findExpiredRecruitingTeamIds()).thenReturn(List.of());
        TeamDeadlineScheduler scheduler = new TeamDeadlineScheduler(teamDeadlineService, eventPublisher);

        scheduler.checkDeadlines();

        verify(eventPublisher, never()).publishEvent(any());
    }
}
