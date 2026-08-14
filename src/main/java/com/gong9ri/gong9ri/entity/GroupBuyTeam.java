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

    // 참여 취소(team/leave) — join()의 increaseParticipant()와 대칭이다. 정원 감소는 곧바로 자리 반환을
    // 의미한다(취소 즉시 다른 사람이 그 자리에 참가할 수 있다). 호출 전 status가 RECRUITING임을 서비스
    // 계층(TeamService.leave)이 보장한다(참여 취소는 RECRUITING에서만 허용). 마지막 한 명이 빠져
    // currentCount가 0이 되면 그 팀은 더 이상 유지할 수 없으므로 FAILED로 전환한다 — 남은 결제가 없다는
    // 전제(docs/dev/ongoing/team-leave-and-refund-request.md "리스크/전제").
    public void decreaseParticipant() {
        this.currentCount -= 1;
        if (this.currentCount <= 0) {
            this.status = TeamStatus.FAILED;
        }
    }

    // 리더가 참여를 취소하면 그다음 최초 참가자에게 리더 지위를 승계한다 — 리더는 "방 구별용" 역할일
    // 뿐 특별한 권한을 갖지 않는다는 전제(사용자 확인).
    public void changeLeader(Member newLeader) {
        this.leader = newLeader;
    }

    // 마감 체크 스케줄러가 호출한다 — RECRUITING 상태일 때만 FAILED로 전환한다(이중 전환 방지 가드).
    public void fail() {
        if (this.status == TeamStatus.RECRUITING) {
            this.status = TeamStatus.FAILED;
        }
    }
}
