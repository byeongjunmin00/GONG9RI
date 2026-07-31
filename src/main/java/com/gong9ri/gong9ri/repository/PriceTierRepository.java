package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.PriceTier;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PriceTierRepository extends JpaRepository<PriceTier, Long> {

    List<PriceTier> findByProductIdOrderByMinCountAsc(Long productId);

    @Query("SELECT pt.product.id AS productId, MIN(pt.price) AS bestPrice "
            + "FROM PriceTier pt WHERE pt.product.id IN :productIds GROUP BY pt.product.id")
    List<BestPriceProjection> findBestPricesByProductIds(@Param("productIds") List<Long> productIds);

    @Transactional
    void deleteByProductId(Long productId);
}
