package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.BuyerTeamResponse;
import com.gong9ri.gong9ri.dto.PurchaseResponse;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.TeamParticipationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BuyerMypageService {

    private final PaymentRepository paymentRepository;
    private final TeamParticipationRepository teamParticipationRepository;

    public List<PurchaseResponse> purchases(MemberUserDetails principal) {
        requireBuyer(principal);
        return paymentRepository.findAllByMemberIdWithProduct(principal.getMember().getId()).stream()
                .map(PurchaseResponse::from)
                .toList();
    }

    public List<BuyerTeamResponse> teams(MemberUserDetails principal) {
        requireBuyer(principal);
        return teamParticipationRepository.findAllByMemberIdWithTeamAndProduct(principal.getMember().getId()).stream()
                .map(BuyerTeamResponse::from)
                .toList();
    }

    private void requireBuyer(MemberUserDetails principal) {
        if (principal.getMember().getRole() != Role.BUYER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
