package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.BuyerTeamResponse;
import com.gong9ri.gong9ri.dto.NotificationResponse;
import com.gong9ri.gong9ri.dto.PurchaseResponse;
import com.gong9ri.gong9ri.dto.RefundRequestResponse;
import com.gong9ri.gong9ri.dto.WishlistItemResponse;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.repository.NotificationRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.RefundRequestRepository;
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
    private final NotificationRepository notificationRepository;
    private final RefundRequestRepository refundRequestRepository;
    private final WishlistService wishlistService;

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

    public List<NotificationResponse> notifications(MemberUserDetails principal) {
        requireBuyer(principal);
        return notificationRepository.findAllByMemberIdOrderByCreatedAtDesc(principal.getMember().getId()).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    // 본인이 요청한 환불 요청 전체(대기/승인/거절 포함) — 참여 취소로 자동 생성된 요청도 포함된다.
    public List<RefundRequestResponse> refundRequests(MemberUserDetails principal) {
        requireBuyer(principal);
        return refundRequestRepository.findAllByRequesterIdWithPaymentAndProduct(principal.getMember().getId())
                .stream()
                .map(RefundRequestResponse::from)
                .toList();
    }

    // 찜한 상품 목록 — 실제 로직(멱등 추가/제거 포함)은 WishlistService가 소유하고, 여기선 조회만 위임한다.
    public List<WishlistItemResponse> wishlist(MemberUserDetails principal) {
        return wishlistService.myWishlist(principal);
    }

    private void requireBuyer(MemberUserDetails principal) {
        if (principal.getMember().getRole() != Role.BUYER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
