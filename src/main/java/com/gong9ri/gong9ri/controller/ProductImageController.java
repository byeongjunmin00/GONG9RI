package com.gong9ri.gong9ri.controller;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.common.response.ApiResponse;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.ProductImageUploadResponse;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.service.ProductImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 상품 이미지 업로드 (product/image).
 *
 * <p>상품 등록/수정과 <b>분리된 엔드포인트</b>다. 판매자가 파일을 먼저 올려 경로를 받고, 그 경로를
 * 상품 등록/수정 요청의 이미지 목록에 담아 보낸다. 이렇게 나눈 이유 —
 * <ul>
 *   <li>이미지를 고르는 즉시 미리보기를 보여줄 수 있다(폼 제출까지 기다리지 않는다).</li>
 *   <li>상품 등록 요청은 지금처럼 JSON 하나로 유지된다 — 기존 등록/수정 흐름을 multipart로 갈아엎지
 *       않아도 되고, <b>업로드한 파일과 외부 URL을 같은 목록에서 동등하게</b> 다룰 수 있다.</li>
 * </ul>
 *
 * <p>업로드 자체는 판매자면 누구나 할 수 있다(아직 어느 상품에 붙일지 정해지지 않은 시점이므로).
 * "남의 상품에 이미지를 붙이는" 것은 상품 수정 단계에서 기존 소유권 검증이 막는다.
 */
@RestController
@RequiredArgsConstructor
public class ProductImageController {

    private final ProductImageStorage productImageStorage;

    @PostMapping("/api/seller/products/images")
    public ResponseEntity<ApiResponse<ProductImageUploadResponse>> upload(
            @AuthenticationPrincipal MemberUserDetails principal,
            @RequestParam("file") MultipartFile file) {
        if (principal.getMember().getRole() != Role.SELLER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        String url = productImageStorage.store(file);
        return ResponseEntity.ok(ApiResponse.success(new ProductImageUploadResponse(url)));
    }
}
