package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.ProductImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    // 상품 상세에서 이미지들을 보여줄 순서대로 조회한다 — idx_product_display_order 인덱스 활용.
    List<ProductImage> findByProductIdOrderByDisplayOrderAsc(Long productId);

    // 상품 수정 시 이미지 목록을 통째로 교체한다(개별 diff를 계산하지 않는다 — 가격 구간
    // (priceTierRepository.deleteByProductId)과 동일한 방식으로, 순서 변경까지 한 번에 반영된다).
    @Transactional
    void deleteByProductId(Long productId);
}
