package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.PriceTier;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface PriceTierRepository extends JpaRepository<PriceTier, Long>, PriceTierRepositoryCustom {

    List<PriceTier> findByProductIdOrderByMinCountAsc(Long productId);

    @Transactional
    void deleteByProductId(Long productId);
}
