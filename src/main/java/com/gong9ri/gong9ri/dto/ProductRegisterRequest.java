package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.ProductCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

public record ProductRegisterRequest(
        @NotBlank String name,
        String description,
        @NotNull @Min(1) Integer basePrice,
        @Min(2) Integer maxParticipants,
        @NotEmpty @Valid List<PriceTierRequest> priceTiers,
        String imageUrl,
        // 상품 이미지 여러 장(product/image, 2026-08-20). 업로드된 파일 경로(/uploads/...)와 외부 URL을
        // 섞어 담을 수 있다. 생략(null)하면 기존처럼 imageUrl 한 장만 쓰는 동작 그대로다 — 기존 상품
        // 대부분이 imageUrl만 갖고 있어서 하위호환이 필수였다.
        List<String> imageUrls,
        // 참여 취소(team/leave) 시 생기는 환불 요청을 판매자 승인 없이 즉시 처리할지 여부. 생략(null)하면
        // false로 취급한다(docs/dev/ongoing/team-leave-and-refund-request.md).
        Boolean autoRefundOnCancel,
        // 메인 페이지 카테고리 필터용(product/category). 필수 — 등록/수정 폼에서 항상 선택하게 한다.
        @NotNull ProductCategory category,
        // 오픈예정(product/product-launch) — 생략(null)하면 기존과 동일하게 등록 즉시 공개. 값이 있으면
        // 미래 시각이어야 한다(과거 시각을 "오픈예정"이라고 등록하는 건 의미가 없다).
        @Future LocalDateTime openAt
) {
}
