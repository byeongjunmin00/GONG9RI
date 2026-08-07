package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.Product;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long>, ProductRepositoryCustom {

    List<Product> findAllBySellerIdOrderByCreatedAtDesc(Long sellerId);
}
