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

    // 메인 페이지 카테고리 필터용(product/category). 기존 row가 있는 테이블에 NOT NULL 컬럼을
    // 추가하는 마이그레이션이라 autoRefundOnCancel과 동일하게 @ColumnDefault로 SQL DEFAULT 절을
    // 만들어 기존 row도 안전하게 처리되게 한다 — 기존 상품은 전부 ETC로 시작하고, 실제 재분류는
    // 판매자가 상품 수정 폼에서 직접 한다(일괄 마이그레이션 스코프 밖, 사용자 결정).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @ColumnDefault("'ETC'")
    private ProductCategory category;

    // 오픈예정(product/product-launch) — null이면 기존과 동일하게 등록 즉시 공개(하위 호환, 기존 row는
    // 전부 null). 미래 시각이면 그 시각 전까지는 목록/상세에 노출은 되지만(둘러보기는 가능) 혼자구매·
    // 신규 팀 신설은 서버가 거절한다(PRODUCT_NOT_YET_OPEN) — "예정"이라는 걸 미리 알리고 기대하게 하는
    // 게 목적이라 아예 숨기지 않는다(와디즈 "오픈예정" 탭도 노출은 하되 구매만 막는 방식).
    @Column
    private LocalDateTime openAt;

    // 관리자 숨김(product/admin, 2026-08-21). true면 목록·상세에서 일반 사용자에게 보이지 않는다.
    // 삭제와 달리 **되돌릴 수 있고 데이터가 남는다** — 결제·리뷰가 달려 삭제할 수 없는 상품(FK 제약)을
    // 치우거나, 잠깐만 내렸다가 되살릴 때 쓴다. 기존 row가 있는 테이블에 NOT NULL 컬럼을 추가하는
    // 마이그레이션이라 autoRefundOnCancel과 동일하게 @ColumnDefault로 SQL DEFAULT 절을 만든다.
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean hidden;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Product(Member seller, String name, String description, Integer basePrice, Integer maxParticipants,
            String imageUrl) {
        this(seller, name, description, basePrice, maxParticipants, imageUrl, false, ProductCategory.ETC);
    }

    public Product(Member seller, String name, String description, Integer basePrice, Integer maxParticipants,
            String imageUrl, boolean autoRefundOnCancel) {
        this(seller, name, description, basePrice, maxParticipants, imageUrl, autoRefundOnCancel, ProductCategory.ETC);
    }

    public Product(Member seller, String name, String description, Integer basePrice, Integer maxParticipants,
            String imageUrl, boolean autoRefundOnCancel, ProductCategory category) {
        this(seller, name, description, basePrice, maxParticipants, imageUrl, autoRefundOnCancel, category, null);
    }

    public Product(Member seller, String name, String description, Integer basePrice, Integer maxParticipants,
            String imageUrl, boolean autoRefundOnCancel, ProductCategory category, LocalDateTime openAt) {
        this.seller = seller;
        this.name = name;
        this.description = description;
        this.basePrice = basePrice;
        this.maxParticipants = maxParticipants;
        this.imageUrl = imageUrl;
        this.autoRefundOnCancel = autoRefundOnCancel;
        this.category = category;
        this.openAt = openAt;
    }

    public void update(String name, String description, Integer basePrice, Integer maxParticipants,
            String imageUrl, boolean autoRefundOnCancel, ProductCategory category, LocalDateTime openAt) {
        this.name = name;
        this.description = description;
        this.basePrice = basePrice;
        this.maxParticipants = maxParticipants;
        this.imageUrl = imageUrl;
        this.autoRefundOnCancel = autoRefundOnCancel;
        this.category = category;
        this.openAt = openAt;
    }

    // 아직 공개 전(오픈예정 시각이 미래)인지 — PaymentService.create()/TeamService.create()가 이걸로
    // 혼자구매·신규 팀 신설을 거절한다(PRODUCT_NOT_YET_OPEN).
    /**
     * 대표 이미지를 이미지 목록의 첫 장으로 맞춘다 (product/image).
     *
     * <p>{@code imageUrl}은 {@code product_image} 테이블이 생긴 뒤에도 남아 있는 <b>의도적인 비정규화</b>다 —
     * 목록 조회가 상품 20개를 한 번에 내리는데 이미지를 매번 조인하면 N+1이 되므로, 카드에 쓸 한 장만
     * 상품 행에 들고 있는다.
     */
    public void changeRepresentativeImage(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public boolean isNotYetOpen() {
        return openAt != null && openAt.isAfter(LocalDateTime.now());
    }

    /** 관리자 숨김 (product/admin). 되돌릴 수 있다 — 데이터는 그대로 남는다. */
    public void hide() {
        this.hidden = true;
    }

    public void unhide() {
        this.hidden = false;
    }
}
