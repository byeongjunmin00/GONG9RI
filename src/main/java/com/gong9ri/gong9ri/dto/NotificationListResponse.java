package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.Notification;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 알림 목록 응답 — 페이지네이션 도입(2026-08-20)으로 배열이 아닌 객체가 됐다.
 *
 * <p>{@code unreadCount}가 이 DTO의 존재 이유다. 알림 목록을 잘라서 내리기 시작하면 프론트가 받아온
 * 목록에서 안 읽은 개수를 셀 수 없다 — 20개만 받았는데 안읽음이 30개면 20으로 세고, 그 20개를 읽는
 * 순간 뱃지가 0이 되지만 실제로는 10개가 남는다. 그래서 개수는 잘린 목록과 무관하게 서버가 센다.
 *
 * @param unreadCount   이 회원의 안 읽은 알림 총 개수(현재 페이지와 무관)
 * @param totalCount    이 회원의 전체 알림 개수
 * @param hasNext       더 불러올 페이지가 남았는지 — 프론트의 "더 보기" 노출 여부
 * @param notifications 요청한 페이지의 알림들(최신순)
 */
public record NotificationListResponse(
        long unreadCount,
        long totalCount,
        boolean hasNext,
        List<NotificationResponse> notifications
) {
    public static NotificationListResponse of(Page<Notification> page, long unreadCount) {
        return new NotificationListResponse(
                unreadCount,
                page.getTotalElements(),
                page.hasNext(),
                page.getContent().stream().map(NotificationResponse::from).toList()
        );
    }
}
