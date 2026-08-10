package com.gong9ri.gong9ri.event;

import com.gong9ri.gong9ri.service.TeamDeadlineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * {@code TeamDeadlineDetectedEvent} 구독자 — 기존 {@code TeamDeadlineService.processDeadline(teamId)}
 * (락+재검증+FAILED 전환+환불, 팀별 독립 트랜잭션) 로직을 그대로 수행한다. 진입 경로만 스케줄러 직접 호출에서
 * 이벤트로 바뀐 것 — 락·재검증 전략은 변경하지 않는다(docs/dev/ongoing/refund-event-messaging.md).
 *
 * {@code @Async}: 스케줄러의 스캔 루프(TeamDeadlineScheduler.checkDeadlines)가 팀별 처리(락 대기+DB 쓰기)를
 * 기다리지 않도록 처리 자체를 별도 스레드로 넘긴다 — 스캔 루프를 막지 않는 게 요구사항이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TeamDeadlineEventListener {

    private final TeamDeadlineService teamDeadlineService;

    @Async
    @EventListener
    public void handleTeamDeadlineDetected(TeamDeadlineDetectedEvent event) {
        log.debug("마감 감지 이벤트 처리 시작: teamId={}", event.teamId());
        teamDeadlineService.processDeadline(event.teamId());
    }
}
