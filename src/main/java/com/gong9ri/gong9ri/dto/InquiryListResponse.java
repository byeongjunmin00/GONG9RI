package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.Inquiry;
import java.util.List;

public record InquiryListResponse(
        int count,
        List<InquiryResponse> inquiries
) {
    public static InquiryListResponse of(List<Inquiry> inquiries) {
        return new InquiryListResponse(inquiries.size(), inquiries.stream().map(InquiryResponse::from).toList());
    }
}
