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

    /**
     * 공구팀 번호(admin-identifier-codes, 2026-08-22). {@code "T" + PK 7자리 zero-pad}
     * ({@link com.gong9ri.gong9ri.common.identifier.IdentifierCodeFormatter#teamNo}) — 팀 1건당
     * 1개이고 {@code TeamParticipation}(참여자 개개인)에는 붙지 않는다. 채번(신설) 시점에 한 번
     * 정해지고 이후 {@code status} 전이와 무관하게 불변이다(PK 파생이라 자연히 불변). 지금은
     * nullable이다 — 백필 완료 전까지는 NOT NULL/UNIQUE 제약을 걸지 않는다(`Member.memberCode` 필드
     * 주석 참고). **admin에는 노출하지 않는다** — admin 전용 공구팀 목록 화면이 아직 없어서다(확정 1,
     * 다음 작업으로 이연). 대신 상품 상세 팀 카드·마이페이지(고객 대면 화면)에 노출한다.
     */
    @Column(name = "team_no", length = 20)
    private String teamNo;

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

    /** 공구팀 번호 채번(신설 직후 1회 호출, {@code TeamService.create}). 백필 서비스도 재사용한다. */
    public void assignTeamNo(String teamNo) {
        this.teamNo = teamNo;
    }
}
