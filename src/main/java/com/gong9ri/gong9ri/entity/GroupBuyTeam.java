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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "group_buy_team", indexes = {
        @Index(name = "idx_product_status", columnList = "product_id, status"),
        @Index(name = "idx_status_deadline", columnList = "status, deadline")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class GroupBuyTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leader_id", nullable = false)
    private Member leader;

    @Column(nullable = false)
    private Integer currentCount;

    @Column(nullable = false)
    private Integer maxParticipants;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TeamStatus status;

    @Column(nullable = false)
    private LocalDateTime deadline;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public GroupBuyTeam(Product product, Member leader, Integer maxParticipants, LocalDateTime deadline) {
        this.product = product;
        this.leader = leader;
        this.currentCount = 1;
        this.maxParticipants = maxParticipants;
        this.status = TeamStatus.RECRUITING;
        this.deadline = deadline;
    }

    public void increaseParticipant() {
        this.currentCount += 1;
        if (this.currentCount.equals(this.maxParticipants)) {
            this.status = TeamStatus.SUCCESS;
        }
    }
}
