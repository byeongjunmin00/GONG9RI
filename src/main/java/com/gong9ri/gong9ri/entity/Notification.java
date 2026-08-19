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

    @Column(nullable = false)
    private Boolean isRead;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Notification(Member member, NotificationType type, String message, GroupBuyTeam relatedTeam) {
        this.member = member;
        this.type = type;
        this.message = message;
        this.relatedTeam = relatedTeam;
        this.isRead = false;
    }

    public void markAsRead() {
        this.isRead = true;
    }
}
