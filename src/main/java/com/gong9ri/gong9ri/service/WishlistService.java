package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.WishlistItemResponse;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.entity.Wishlist;
import com.gong9ri.gong9ri.repository.BestPriceProjection;
import com.gong9ri.gong9ri.repository.PriceTierRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.WishlistRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 찜(product/wishlist) — 구매자 전용, 결제/참가 흐름과 동일한 역할 제약(requireBuyer).
 *
 * <p>추가/제거 둘 다 멱등(idempotent)하게 만든다 — 이미 찜한 상품을 다시 찜하거나, 찜 안 한 상품을
 * 취소해도 에러 없이 조용히 성공 처리한다. 하트 아이콘 토글 UI가 "지금 찜 상태인지"를 매번 서버에
 * 먼저 물어보지 않고 그냥 반대 동작을 호출해도 되게 하기 위함(중복 클릭·네트워크 재시도에도 안전).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final ProductRepository productRepository;
    private final PriceTierRepository priceTierRepository;

    @Transactional
    public void add(MemberUserDetails principal, Long productId) {
        requireBuyer(principal);
        Member member = principal.getMember();

        if (wishlistRepository.existsByMember_IdAndProduct_Id(member.getId(), productId)) {
            return;
        }
        var product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        wishlistRepository.save(new Wishlist(member, product));
        log.info("찜 추가: memberId={}, productId={}", member.getId(), productId);
    }

    @Transactional
    public void remove(MemberUserDetails principal, Long productId) {
        requireBuyer(principal);
        Member member = principal.getMember();

        wishlistRepository.deleteByMember_IdAndProduct_Id(member.getId(), productId);
        log.info("찜 제거: memberId={}, productId={}", member.getId(), productId);
    }

    public List<WishlistItemResponse> myWishlist(MemberUserDetails principal) {
        requireBuyer(principal);

        List<Wishlist> wishlists = wishlistRepository.findAllByMemberIdWithProduct(principal.getMember().getId());
        List<Long> productIds = wishlists.stream().map(w -> w.getProduct().getId()).toList();
        Map<Long, Integer> bestPrices = productIds.isEmpty()
                ? Map.of()
                : priceTierRepository.findBestPricesByProductIds(productIds).stream()
                        .collect(Collectors.toMap(BestPriceProjection::getProductId, BestPriceProjection::getBestPrice));

        return wishlists.stream()
                .map(w -> WishlistItemResponse.of(w, bestPrices.get(w.getProduct().getId())))
                .toList();
    }

    private void requireBuyer(MemberUserDetails principal) {
        if (principal.getMember().getRole() != Role.BUYER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
