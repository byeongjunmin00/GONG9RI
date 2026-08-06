package com.gong9ri.gong9ri.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 판매자별 결제 집계(총매출·PAID건수·REFUNDED건수)를 미리 계산해둔 요약 행.
 * {@code group_buy_team.current_count}와 같은 패턴 — 매번 SUM/COUNT하지 않고, 결제/환불이 발생하는
 * 트랜잭션 안에서 이 컬럼을 즉시 증감시킨다(docs/db/seller_revenue_summary.md, docs/ERD.md).
 * 값 자체를 필드에서 직접 바꾸는 도메인 메서드는 두지 않는다 — 갱신은 항상
 * {@code SellerRevenueSummaryRepository}의 원자적 UPDATE(incrementPaid/applyRefund)로만 한다.
 */
@Entity
@Table(name = "seller_revenue_summary")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class SellerRevenueSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false, unique = true)
    private Member seller;

    @Column(nullable = false)
    private Integer totalRevenue;

    @Column(nullable = false)
    private Long paidCount;

    @Column(nullable = false)
    private Long refundedCount;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public SellerRevenueSummary(Member seller, Integer totalRevenue, Long paidCount, Long refundedCount) {
        this.seller = seller;
        this.totalRevenue = totalRevenue;
        this.paidCount = paidCount;
        this.refundedCount = refundedCount;
    }
}
