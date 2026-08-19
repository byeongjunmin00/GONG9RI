package com.gong9ri.gong9ri.event;

import com.gong9ri.gong9ri.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@code NotificationRequestedEvent} 구독자 — {@code TeamRefundedEventListener}와 같은 이유로
 * AFTER_COMMIT이다. 알림을 만든 원인이 된 작업(결제 확정·문의 등록·환불 승인 등)이 실제로 커밋된
 * 뒤에만 알림이 남아야 한다. 롤백된 작업의 알림이 유령처럼 남으면 안 된다.
 *
 * <p><b>{@code @Async}가 필수다 — 없으면 커넥션 풀이 고갈된다.</b> AFTER_COMMIT 콜백은 원본 트랜잭션의
 * JDBC 커넥션이 <i>아직 반납되기 전에</i> 실행된다. 여기서 동기로 {@code NotificationService}
 * ({@code REQUIRES_NEW})를 부르면 한 요청 스레드가 커넥션을 <b>동시에 2개</b> 필요로 하게 되고, 동시
 * 요청이 풀 크기만큼 몰리면 전원이 첫 커넥션을 쥔 채 두 번째를 기다리며 아무도 진행하지 못한다.
 * 실제로 동시 결제 20건 테스트({@code SellerRevenueSummaryConcurrencyTest})에서
 * {@code total=10, active=10, idle=0, waiting=16}으로 재현됐다. {@code @Async}로 분리하면 알림 INSERT가
 * 별도 스레드에서 커넥션 1개만 잡으므로 이 교착이 생기지 않는다
 * ({@code RefundRequestApprovedEventListener}가 이미 같은 이유로 {@code @Async}다).
 *
 * <p>부작용: 알림 생성이 원인 작업보다 아주 조금 늦게 보인다. 알림은 즉시성이 필수인 데이터가 아니라
 * 허용 가능한 트레이드오프로 판단했다(대신 테스트는 폴링으로 기다린다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRequestedEventListener {

    private final NotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNotificationRequested(NotificationRequestedEvent event) {
        // 알림 생성이 실패해도 원인이 된 작업(이미 커밋됨)은 되돌리지 않는다 — 알림은 부가 기능이라
        // 이게 깨졌다고 결제/문의/환불 처리가 실패한 것처럼 보이면 안 된다. 대신 로그로 남겨 추적한다.
        try {
            notificationService.createFromRequest(event);
        } catch (Exception e) {
            log.error("알림 생성 실패(원인 작업은 이미 커밋됨, 무시하고 진행): type={}, memberIds={}",
                    event.type(), event.memberIds(), e);
        }
    }
}
