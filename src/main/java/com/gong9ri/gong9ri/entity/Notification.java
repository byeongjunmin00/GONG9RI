package com.gong9ri.gong9ri.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 알림 (docs/db/notification.md). 하드 삭제 없음(알림 이력 보존) — 삭제 메서드를 두지 않는다.
 */
@Entity
@Table(name = "notification", indexes = {
        @Index(name = "idx_member", columnList = "member_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 255)
    private String message;

    // 팀 관련 알림이 아니면 NULL. related_team_id → group_buy_team.id.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_team_id")
    private GroupBuyTeam relatedTeam;

    /**
     * 알림을 눌렀을 때 이동할 앱 내부 경로(예: {@code /product.html?id=33}). 알림 종류가 늘면서
     * 연결 대상이 공구팀만이 아니게 돼(문의·리뷰·결제 등) 추가했다 — 대상 타입+ID로 정규화하는 대신
     * 경로를 그대로 저장한다(프론트에 타입별 URL 조립 분기를 만들지 않기로 함, 2026-08-20 결정).
     *
     * NULL 허용이다 — 이 컬럼이 생기기 전에 만들어진 기존 알림들은 값이 없다. 프론트는 링크 없는
     * 알림을 "클릭해도 이동하지 않는 항목"으로 정상 처리해야 한다.
     */
    @Column(length = 255)
    private String linkUrl;

    @Column(nullable = false)
    private Boolean isRead;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Notification(Member member, NotificationType type, String message, GroupBuyTeam relatedTeam) {
        this(member, type, message, relatedTeam, null);
    }

    public Notification(Member member, NotificationType type, String message, GroupBuyTeam relatedTeam,
            String linkUrl) {
        this.member = member;
        this.type = type;
        this.message = message;
        this.relatedTeam = relatedTeam;
        this.linkUrl = linkUrl;
        this.isRead = false;
    }

    public void markAsRead() {
        this.isRead = true;
    }
}
