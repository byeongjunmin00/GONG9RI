package com.gong9ri.gong9ri.event;

import com.gong9ri.gong9ri.entity.NotificationType;
import java.util.List;

/**
 * "이 회원들에게 이런 알림을 남겨달라"는 범용 요청 이벤트.
 *
 * 알림 종류가 8종으로 늘면서(2026-08-20) 종류마다 이벤트+리스너를 한 쌍씩 만들면 보일러플레이트만
 * 늘고 정작 하는 일(알림 row INSERT)은 전부 같아서, 이벤트를 하나로 통일했다. 어떤 문구를 누구에게
 * 보낼지는 발행하는 쪽({@code NotificationPublisher})이 정하고, 이 이벤트는 그 결과만 실어 나른다.
 *
 * 기존 {@code TeamRefundedEvent}는 그대로 둔다 — 그건 "환불이 완료됐다"는 도메인 사실을 알리는
 * 이벤트라 알림 말고 다른 구독자가 붙을 수 있는 반면, 이 이벤트는 처음부터 알림 전용이다.
 *
 * 엔티티가 아니라 <b>식별자/문자열만</b> 담는다 — 리스너가 원본 트랜잭션 커밋 이후에 실행되므로
 * 그 시점에 살아있지 않을 수 있는 영속성 컨텍스트에 의존하면 안 된다.
 *
 * @param memberIds 수신자들(중복은 소비하는 쪽에서 제거한다)
 * @param teamId    관련 공구팀 — 팀과 무관한 알림이면 null
 * @param linkUrl   알림 클릭 시 이동할 앱 내부 경로 — 없으면 null
 */
public record NotificationRequestedEvent(
        List<Long> memberIds,
        NotificationType type,
        String message,
        Long teamId,
        String linkUrl
) {
}
