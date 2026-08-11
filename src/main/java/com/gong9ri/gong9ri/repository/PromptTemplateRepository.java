package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.PromptCategory;
import com.gong9ri.gong9ri.entity.PromptTemplate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, Long> {

    Optional<PromptTemplate> findByCategory(PromptCategory category);

    boolean existsByCategory(PromptCategory category);
}
