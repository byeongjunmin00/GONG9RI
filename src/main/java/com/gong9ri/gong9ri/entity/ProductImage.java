package com.gong9ri.gong9ri.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품 이미지 한 장 (product/image). 상품 1 : 이미지 N.
 *
 * <p><b>업로드한 파일과 외부 URL을 구분하지 않는다.</b> {@code url}에는 우리 볼륨에 저장된 파일 경로
 * ({@code /uploads/...})가 들어갈 수도 있고, 판매자가 붙여넣은 외부 주소({@code https://...})가 들어갈
 * 수도 있다. 화면과 API는 "이미지 목록"만 알면 되고 그게 어디에 있는 파일인지는 관심사가 아니다 —
 * 기존 상품 대부분이 외부 URL을 쓰고 있어서(작업 시점 36개 중 35개) 둘을 동등하게 다루는 게 필수였다.
 *
 * <p>{@code Product.imageUrl}(대표 이미지)은 그대로 남는다. 목록 조회는 상품 20개를 한 번에 내리는데
 * 이미지를 매번 조인하면 N+1이 되므로, 대표 한 장만 상품 행에 비정규화해 둔다
 * ({@code group_buy_team.current_count}와 같은 결의 결정). 이 테이블이 비어 있는 상품은 대표 이미지를
 * 유일한 이미지로 취급하므로 <b>기존 데이터를 옮기는 마이그레이션이 필요 없다.</b>
 */
@Entity
@Table(name = "product_image", indexes = {
        @Index(name = "idx_product_display_order", columnList = "product_id, display_order")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** 볼륨에 저장된 파일 경로({@code /uploads/...}) 또는 외부 이미지 주소. */
    @Column(nullable = false, length = 500)
    private String url;

    /** 화면에 보여줄 순서. 0이 대표 이미지. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public ProductImage(Product product, String url, int displayOrder) {
        this.product = product;
        this.url = url;
        this.displayOrder = displayOrder;
    }
}
