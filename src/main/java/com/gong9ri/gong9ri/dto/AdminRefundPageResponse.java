package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.RefundRequest;
import java.util.List;
import org.springframework.data.domain.Page;

public record AdminRefundPageResponse(
        List<RefundRequestResponse> content,
        int page,
        int size,
        long totalElements
) {
    public static AdminRefundPageResponse of(Page<RefundRequest> page) {
        return new AdminRefundPageResponse(
                page.getContent().stream().map(RefundRequestResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }
}
