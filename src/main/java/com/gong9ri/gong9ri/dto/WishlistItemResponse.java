package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.Wishlist;
import java.time.LocalDateTime;

public record WishlistItemResponse(
        Long productId,
        String productName,
        Integer basePrice,
        Integer bestPrice,
        String imageUrl,
        String sellerName,
        LocalDateTime wishlistedAt,
        // 프로필 사진(member/profile-image 노출, 2026-08-21). 판매자 이름과 같은 회원 엔티티에서 읽으므로
        // 추가 조회가 없다. 없으면 null → 프론트가 판매자 이름 첫 글자 동그라미를 그린다.
        String sellerProfileImageUrl
) {
    public static WishlistItemResponse of(Wishlist wishlist, Integer bestPrice) {
        var product = wishlist.getProduct();
        return new WishlistItemResponse(
                product.getId(),
                product.getName(),
                product.getBasePrice(),
                bestPrice,
                product.getImageUrl(),
                product.getSeller().getName(),
                wishlist.getCreatedAt(),
                product.getSeller().getProfileImageUrl()
        );
    }
}
