package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.Inquiry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    List<Inquiry> findByProductIdOrderByCreatedAtDesc(Long productId);
}
