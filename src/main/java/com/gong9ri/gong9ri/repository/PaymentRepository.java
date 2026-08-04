package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.Payment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("SELECT p FROM Payment p JOIN FETCH p.member JOIN FETCH p.product LEFT JOIN FETCH p.team WHERE p.id = :id")
    Optional<Payment> findByIdWithDetails(@Param("id") Long id);
}
