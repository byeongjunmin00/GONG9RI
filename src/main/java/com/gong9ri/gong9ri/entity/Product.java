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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "product", indexes = {
        @Index(name = "idx_seller", columnList = "seller_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private Member seller;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Integer basePrice;

    @Column(nullable = false)
    private Integer maxParticipants;

    @Column(length = 500)
    private String imageUrl;

    // 참여 취소(team/leave)로 자동 생성되는 환불 요청을 판매자 승인 없이 즉시 처리할지 여부(상품 단위 설정,
    // docs/dev/ongoing/team-leave-and-refund-request.md). 기존 row가 있는 테이블에 NOT NULL 컬럼을
    // 추가하는 마이그레이션이라 @ColumnDefault로 SQL DEFAULT false 절을 만들어 기존 row도 안전하게
    // 처리되게 한다(Member.emailVerified와 동일한 패턴). 솔로 구매 직접 환불 요청은 이 설정과 무관하게
    // 항상 판매자 승인이 필요하다(이미 배송됐을 수 있어서) — 이 플래그는 참여 취소로 생긴 요청에만 적용.
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean autoRefundOnCancel;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Product(Member seller, String name, String description, Integer basePrice, Integer maxParticipants,
            String imageUrl) {
        this(seller, name, description, basePrice, maxParticipants, imageUrl, false);
    }

    public Product(Member seller, String name, String description, Integer basePrice, Integer maxParticipants,
            String imageUrl, boolean autoRefundOnCancel) {
        this.seller = seller;
        this.name = name;
        this.description = description;
        this.basePrice = basePrice;
        this.maxParticipants = maxParticipants;
        this.imageUrl = imageUrl;
        this.autoRefundOnCancel = autoRefundOnCancel;
    }

    public void update(String name, String description, Integer basePrice, Integer maxParticipants,
            String imageUrl, boolean autoRefundOnCancel) {
        this.name = name;
        this.description = description;
        this.basePrice = basePrice;
        this.maxParticipants = maxParticipants;
        this.imageUrl = imageUrl;
        this.autoRefundOnCancel = autoRefundOnCancel;
    }
}
