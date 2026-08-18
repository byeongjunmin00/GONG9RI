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
        LocalDateTime wishlistedAt
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
                wishlist.getCreatedAt()
        );
    }
}
