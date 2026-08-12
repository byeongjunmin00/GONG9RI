package com.gong9ri.gong9ri.event;

import com.gong9ri.gong9ri.dto.TeamJoinResponse;

/**
 * 공구팀 참가로 정원이 바뀌었을 때 발행 — {@code TeamCapacityChangedEventListener}가 이 이벤트를
 * 받아 해당 상품의 실시간 브로드캐스트 토픽으로 전달한다.
 */
public record TeamCapacityChangedEvent(Long productId, TeamJoinResponse payload) {
}
