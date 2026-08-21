package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.Member;
import java.util.List;
import org.springframework.data.domain.Page;

public record AdminMemberPageResponse(
        List<AdminMemberResponse> content,
        int page,
        int size,
        long totalElements
) {
    public static AdminMemberPageResponse of(Page<Member> page) {
        return new AdminMemberPageResponse(
                page.getContent().stream().map(AdminMemberResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }

    public static AdminMemberPageResponse of(Page<Member> page, List<AdminMemberResponse> content) {
        return new AdminMemberPageResponse(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }
}
