package com.gong9ri.gong9ri.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

// 상품 리뷰 — 그 상품을 실제로 결제(PAID) 완료한 구매자만 작성할 수 있다(ReviewService에서 검증,
// 이 엔티티 자체는 그 전제를 강제하지 않는다 — Payment 참조를 별도로 안 들고 있는 이유는 리뷰 작성
// 시점에 검증만 하면 되고 이후 결제가 취소·환불돼도 이미 작성된 리뷰까지 소급해서 지울 필요는 없다고
// 판단했기 때문). 한 회원이 같은 상품에 리뷰를 두 개 이상 남기지 못하도록 (product_id, member_id)
// 유니크 제약을 건다.
@Entity
@Table(name = "review", indexes = {
        @Index(name = "idx_product", columnList = "product_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_review_product_member", columnNames = {"product_id", "member_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false)
    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String content;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Review(Product product, Member member, Integer rating, String content) {
        this.product = product;
        this.member = member;
        this.rating = rating;
        this.content = content;
    }

    public void update(Integer rating, String content) {
        this.rating = rating;
        this.content = content;
    }
}
