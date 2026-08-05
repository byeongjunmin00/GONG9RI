package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.config.CacheConfig;
import com.gong9ri.gong9ri.dto.PaymentCreateRequest;
import com.gong9ri.gong9ri.dto.PaymentResponse;
import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.PriceTier;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.PriceTierRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.TeamParticipationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final GroupBuyTeamRepository groupBuyTeamRepository;
    private final TeamParticipationRepository teamParticipationRepository;
    private final PriceTierRepository priceTierRepository;
    private final CacheManager cacheManager;

    @Transactional
    public PaymentResponse create(MemberUserDetails principal, PaymentCreateRequest request) {
        requireBuyer(principal);
        Member member = principal.getMember();

        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        GroupBuyTeam team = null;
        Integer amount = product.getBasePrice();
        if (request.teamId() != null) {
            team = groupBuyTeamRepository.findById(request.teamId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND));
            requireRoomOrAlreadyJoined(team, member);
            amount = resolveTeamPrice(product, team);
        }

        Payment saved = paymentRepository.save(new Payment(member, product, team, amount));
        log.info("결제 생성 완료: paymentId={}, memberId={}, productId={}, teamId={}, amount={}",
                saved.getId(), member.getId(), product.getId(), request.teamId(), amount);

        // 결제가 발생하면 판매자 수익 캐시가 옛 값을 반환하지 않도록 무효화한다 (docs/policy/caching.md).
        // product는 항상 존재하므로 teamId 유무와 무관하게 항상 무효화한다.
        // product는 이미 로딩돼 있어 product.getSeller()는 지연 로딩 프록시 초기화만 발생시킨다(추가 쿼리는 미미).
        evictSellerRevenueCache(product.getSeller().getId());
        return PaymentResponse.from(saved);
    }

    // @CacheEvict는 메서드 파라미터만 SpEL로 참조할 수 있어, 메서드 본문에서 조회한 product의 sellerId는
    // 애노테이션으로 표현할 수 없다 — CacheManager를 직접 호출해 무효화한다.
    private void evictSellerRevenueCache(Long sellerId) {
        Cache cache = cacheManager.getCache(CacheConfig.SELLER_REVENUE_CACHE);
        if (cache != null) {
            cache.evict(sellerId);
        }
    }

    public PaymentResponse detail(MemberUserDetails principal, Long paymentId) {
        Payment payment = paymentRepository.findByIdWithDetails(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        requireOwner(principal, payment);
        return PaymentResponse.from(payment);
    }

    // 팀 참가는 team/join·team/create에서 이미 완결된다 — 결제는 그 결과를 다시 검증만 한다.
    // 아직 참가하지 않은 멤버가 이미 정원이 찬 팀으로 결제를 시도하는 경합만 방어적으로 막는다.
    private void requireRoomOrAlreadyJoined(GroupBuyTeam team, Member member) {
        boolean alreadyJoined = teamParticipationRepository.existsByTeamIdAndMemberId(team.getId(), member.getId());
        if (!alreadyJoined && team.getCurrentCount() >= team.getMaxParticipants()) {
            throw new BusinessException(ErrorCode.TEAM_FULL);
        }
    }

    private Integer resolveTeamPrice(Product product, GroupBuyTeam team) {
        List<PriceTier> tiers = priceTierRepository.findByProductIdOrderByMinCountAsc(product.getId());
        Integer price = product.getBasePrice();
        for (PriceTier tier : tiers) {
            if (team.getCurrentCount() >= tier.getMinCount()) {
                price = tier.getPrice();
            } else {
                break;
            }
        }
        return price;
    }

    private void requireBuyer(MemberUserDetails principal) {
        if (principal.getMember().getRole() != Role.BUYER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void requireOwner(MemberUserDetails principal, Payment payment) {
        if (!payment.getMember().getId().equals(principal.getMember().getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
