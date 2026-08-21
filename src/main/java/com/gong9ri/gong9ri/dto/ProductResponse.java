package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.PriceTier;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.ProductCategory;
import java.time.LocalDateTime;
import java.util.List;

public record ProductResponse(
        Long productId,
        Long sellerId,
        String sellerName,
        String name,
        String description,
        Integer basePrice,
        Integer maxParticipants,
        List<PriceTierResponse> priceTiers,
        LocalDateTime createdAt,
        String imageUrl,
        // 참여 취소로 생긴 환불 요청을 판매자 승인 없이 즉시 처리하는지(상품 단위 설정) —
        // 판매자 등록/수정 폼 프리필에도 쓰인다.
        Boolean autoRefundOnCancel,
        // 카카오톡 공유하기(docs/dev/share/kakao-share/design.md)용 — 브라우저에서 Kakao.init(...)에
        // 그대로 쓰인다. portoneStoreId/portoneChannelKey를 PaymentResponse에 실어 내려주는 것과 동일한
        // 패턴(도메인 화이트리스트로 보호되는 공개 가능한 값이라 별도 인증 없이 내려줘도 무방).
        String kakaoJsKey,
        // 메인 페이지 카테고리 필터용(product/category). 판매자 등록/수정 폼 프리필에도 쓰인다.
        ProductCategory category,
        // 오픈예정(product/product-launch) — null이면 이미 공개된 상품(하위 호환 기본값). 미래 시각이면
        // 그 전까지 혼자구매·신규 팀 신설이 서버에서 거절된다(PRODUCT_NOT_YET_OPEN). 판매자 등록/수정
        // 폼 프리필에도 쓰인다.
        LocalDateTime openAt,
        // 판매자 신뢰 배지(product/seller-trust) — ProductSummaryResponse와 동일 기준. boolean이 아니라
        // Boolean이어야 하는 이유도 동일(ProductSummaryResponse.sellerTrustedBadge 필드 주석 참고 —
        // 이 필드 추가 이전에 캐시된 상세 응답을 배포 직후 읽으면 primitive는 역직렬화 시 500을 낸다).
        Boolean sellerTrustedBadge,
        Double ratingAverage,
        Integer reviewCount,
        // 상품 이미지 여러 장(product/image, 2026-08-20). product_image 행이 없으면 대표 이미지
        // 한 장짜리 목록으로 채워지므로, 클라이언트는 imageUrl 유무와 무관하게 이 목록만 보면 된다.
        List<String> imageUrls,
        // 카카오톡 공유 카드에 실을 정식 주소(share/kakao-share, 2026-08-20 추가). 프론트가
        // window.location.href를 쓰면 **공유한 사람이 보고 있던 주소**가 그대로 나간다 — 로컬에서
        // 공유하면 받는 사람 기기의 localhost를 찾아 아무것도 안 열리고, 추적용 쿼리파라미터가
        // 붙어 있으면 그것까지 딸려간다. 공유 링크는 서버가 아는 공개 주소여야 한다.
        // 이 필드 추가 이전에 캐시된 응답을 읽으면 null이 되는데, 프론트가 그때만 기존 방식으로
        // 폴백하므로 배포 직후에도 깨지지 않는다(String이라 primitive 역직렬화 문제도 없다).
        String shareUrl,
        // 판매자 프로필 사진(member/profile-image 노출, 2026-08-21). sellerName과 같은 출처라 추가 조회가
        // 없다. 사진이 없으면 null이고 프론트가 이름 첫 글자 동그라미를 그린다 — 이 필드 추가 이전에
        // 캐시된 상세 응답에서도 null이 되므로 배포 직후에 깨지지 않는다.
        String sellerProfileImageUrl
) {
    public static ProductResponse of(Product product, List<PriceTier> priceTiers, String kakaoJsKey,
            boolean sellerTrustedBadge, String baseUrl) {
        return new ProductResponse(
                product.getId(),
                product.getSeller().getId(),
                product.getSeller().getName(),
                product.getName(),
                product.getDescription(),
                product.getBasePrice(),
                product.getMaxParticipants(),
                priceTiers.stream().map(PriceTierResponse::from).toList(),
                product.getCreatedAt(),
                product.getImageUrl(),
                product.isAutoRefundOnCancel(),
                kakaoJsKey,
                product.getCategory(),
                product.getOpenAt(),
                sellerTrustedBadge,
                null,
                null,
                List.of(),
                shareUrl(baseUrl, product.getId()),
                product.getSeller().getProfileImageUrl()
        );
    }

    /** 공유 카드에 실을 정식 주소. base-url이 비어있으면(로컬 미설정 등) null을 주고 프론트가 폴백한다. */
    private static String shareUrl(String baseUrl, Long productId) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        return baseUrl.replaceAll("/+$", "") + "/product.html?id=" + productId;
    }

    public ProductResponse withReviewStats(Double ratingAverage, Integer reviewCount) {
        return new ProductResponse(
                productId, sellerId, sellerName, name, description, basePrice, maxParticipants,
                priceTiers, createdAt, imageUrl, autoRefundOnCancel, kakaoJsKey, category, openAt,
                sellerTrustedBadge, ratingAverage, reviewCount, imageUrls, shareUrl, sellerProfileImageUrl
        );
    }

    /**
     * 이미지 목록을 채운다 (product/image).
     *
     * <p>{@code product_image} 행이 없는 상품(이 기능 이전에 등록된 대부분)은 <b>대표 이미지 한 장짜리
     * 목록</b>으로 채운다 — 그래서 기존 데이터를 옮기는 마이그레이션 없이도 클라이언트는 이 목록만 보면
     * 된다. 대표 이미지조차 없으면 빈 목록이다.
     */
    public ProductResponse withImages(List<String> images) {
        List<String> resolved = (images != null && !images.isEmpty())
                ? images
                : (imageUrl != null && !imageUrl.isBlank() ? List.of(imageUrl) : List.of());
        return new ProductResponse(
                productId, sellerId, sellerName, name, description, basePrice, maxParticipants,
                priceTiers, createdAt, imageUrl, autoRefundOnCancel, kakaoJsKey, category, openAt,
                sellerTrustedBadge, ratingAverage, reviewCount, resolved, shareUrl, sellerProfileImageUrl
        );
    }
}
