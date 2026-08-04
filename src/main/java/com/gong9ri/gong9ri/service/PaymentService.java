package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
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
        return PaymentResponse.from(saved);
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
