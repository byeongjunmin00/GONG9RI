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
 * 결제 환불 요청 (docs/db/refund_request.md, docs/dev/ongoing/team-leave-and-refund-request.md).
 *
 * <p>생성 경로는 두 가지뿐이며 서로 겹치지 않는다:
 * <ul>
 *   <li>참여 취소({@code TeamService.leave})가 자동으로 생성 — 팀 결제 전용, {@code reason}은 항상 null
 *       ("참여 취소"가 곧 사유)</li>
 *   <li>구매자가 솔로 구매({@code payment.team == null}) 건에 대해 직접 요청 — {@code reason} 필수</li>
 * </ul>
 * 팀이 딸린 결제는 (2)의 대상이 아니다 — 팀 결제의 환불은 오직 (1)로만 일어난다(악용 방지, 계획 문서
 * "매우 중요한 제약" 참고).
 */
@Entity
@Table(name = "refund_request", indexes = {
        @Index(name = "idx_payment", columnList = "payment_id"),
        @Index(name = "idx_requester", columnList = "requester_id"),
        @Index(name = "idx_status", columnList = "status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class RefundRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private Member requester;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundRequestStatus status;

    // 구매자가 직접 요청(솔로 구매)할 때만 값이 있다. 참여 취소로 자동 생성된 요청은 "참여 취소"가 곧
    // 사유라 null.
    @Column(length = 500)
    private String reason;

    // 거절 시에만 값이 있다 — 자유 텍스트가 아니라 템플릿(enum) 중 하나(사용자 확인 사항).
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private RefundRejectionReason rejectionReason;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    // 승인/거절 시점. PENDING인 동안은 null.
    private LocalDateTime decidedAt;

    public RefundRequest(Payment payment, Member requester, String reason) {
        this.payment = payment;
        this.requester = requester;
        this.reason = reason;
        this.status = RefundRequestStatus.PENDING;
    }

    // 판매자 승인(수동) 또는 상품별 "참여 취소 시 자동 환불" 설정이 켜져 있을 때(TeamService.leave가
    // 즉시 호출) — 실제 PortOne 결제취소 호출은 별도 이벤트(RefundRequestApprovedEvent)가 담당한다.
    public void approve() {
        if (this.status == RefundRequestStatus.PENDING) {
            this.status = RefundRequestStatus.APPROVED;
            this.decidedAt = LocalDateTime.now();
        }
    }

    public void reject(RefundRejectionReason rejectionReason) {
        if (this.status == RefundRequestStatus.PENDING) {
            this.status = RefundRequestStatus.REJECTED;
            this.rejectionReason = rejectionReason;
            this.decidedAt = LocalDateTime.now();
        }
    }
}
