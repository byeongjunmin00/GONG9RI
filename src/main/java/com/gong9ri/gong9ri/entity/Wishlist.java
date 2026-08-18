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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

// 찜(product/wishlist) — 구매자 전용(WishlistService에서 requireBuyer로 검증, 결제/참가 흐름과 동일한
// 역할 제약). 상태(추가/제거)만 있으면 되는 단순 토글이라 수정일(updatedAt)은 두지 않는다 — 제거는
// 행 자체를 삭제하고, 재추가는 새 행을 만든다(그래서 createdAt만으로 "언제 찜했는지"가 항상 정확함).
// 한 회원이 같은 상품을 중복으로 찜하지 못하도록 (member_id, product_id) 유니크 제약을 건다.
@Entity
@Table(name = "wishlist", indexes = {
        @Index(name = "idx_member", columnList = "member_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_wishlist_member_product", columnNames = {"member_id", "product_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Wishlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Wishlist(Member member, Product product) {
        this.member = member;
        this.product = product;
    }
}
