package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.config.CacheConfig;
import com.gong9ri.gong9ri.dto.RevenueResponse;
import com.gong9ri.gong9ri.dto.SellerProductResponse;
import com.gong9ri.gong9ri.dto.SellerTeamResponse;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
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

    // 집계 쿼리(SUM, COUNT) 비용이 크고 실시간성이 덜 중요해 sellerId 기준으로 캐싱한다 (docs/policy/caching.md).
    // 키는 principal 객체가 아닌 sellerId(Long)만 사용한다 — principal을 키로 쓰면 직렬화·동등성 문제가 생긴다.
    @Cacheable(cacheNames = CacheConfig.SELLER_REVENUE_CACHE, key = "#principal.member.id")
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
