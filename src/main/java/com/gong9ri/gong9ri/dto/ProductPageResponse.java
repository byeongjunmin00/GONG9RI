package com.gong9ri.gong9ri.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record ProductPageResponse(
        List<ProductSummaryResponse> content,
        int page,
        int size,
        long totalElements
) {
    public static ProductPageResponse of(Page<ProductSummaryResponse> page) {
        return new ProductPageResponse(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
