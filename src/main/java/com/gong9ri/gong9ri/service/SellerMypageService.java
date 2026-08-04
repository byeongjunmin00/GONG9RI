package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.RevenueResponse;
import com.gong9ri.gong9ri.dto.SellerProductResponse;
import com.gong9ri.gong9ri.dto.SellerTeamResponse;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerMypageService {

    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final GroupBuyTeamRepository groupBuyTeamRepository;

    public List<SellerProductResponse> products(MemberUserDetails principal) {
        requireSeller(principal);
        return productRepository.findAllBySellerIdOrderByCreatedAtDesc(principal.getMember().getId()).stream()
                .map(SellerProductResponse::from)
                .toList();
    }

    public RevenueResponse revenue(MemberUserDetails principal) {
        requireSeller(principal);
        return RevenueResponse.from(
                paymentRepository.findRevenueSummaryBySellerId(principal.getMember().getId()));
    }

    public List<SellerTeamResponse> teams(MemberUserDetails principal) {
        requireSeller(principal);
        return groupBuyTeamRepository.findAllBySellerIdWithProduct(principal.getMember().getId()).stream()
                .map(SellerTeamResponse::from)
                .toList();
    }

    private void requireSeller(MemberUserDetails principal) {
        if (principal.getMember().getRole() != Role.SELLER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
