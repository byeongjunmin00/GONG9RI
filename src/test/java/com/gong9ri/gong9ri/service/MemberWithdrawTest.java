package com.gong9ri.gong9ri.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴 (member/withdraw, 2026-08-21).
 *
 * <p>고정하려는 것은 <b>"행을 지우지 않는다"</b>는 결정이다. 이 회원을 참조하는 테이블이 12개고
 * 결제·공구팀 참여는 남의 화면에도 영향을 주므로, 지우는 대신 로그인만 막고 이름을 가린다.
 */
@SpringBootTest
@Transactional
class MemberWithdrawTest {

    @Autowired
    private MemberService memberService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ProductRepository productRepository;

    @Test
    @DisplayName("탈퇴해도 회원 행은 남고, 로그인만 막히며 이름이 가려진다")
    void withdraw_keepsRow_masksIdentity() {
        Member member = saveMember("wdUser1", Role.BUYER, "홍길동");
        Long id = member.getId();

        memberService.withdraw(id, "rawPassword");

        Member after = memberRepository.findById(id).orElseThrow();
        assertTrue(after.isWithdrawn(), "탈퇴 표시가 남아야 로그인 게이트가 막을 수 있다");
        assertEquals("탈퇴한 회원", after.getName(),
                "이름을 비우면 리뷰·참여자 목록에서 그 자리가 빈칸이 되어 화면이 깨진 것처럼 보인다");
        assertNull(after.getProfileImageUrl());
        assertEquals("wdUser1", after.getUsername(),
                "username은 남겨야 같은 아이디로 재가입해 남의 기록을 이어받는 걸 막는다");
    }

    @Test
    @DisplayName("비밀번호가 틀리면 탈퇴되지 않는다 — 되돌릴 수 없는 동작이라 본인 확인을 요구한다")
    void withdraw_wrongPassword_rejected() {
        Member member = saveMember("wdUser2", Role.BUYER, "홍길동");

        assertThrows(BusinessException.class,
                () -> memberService.withdraw(member.getId(), "틀린비밀번호"));

        assertFalse(memberRepository.findById(member.getId()).orElseThrow().isWithdrawn());
    }

    @Test
    @DisplayName("탈퇴 후 비밀번호는 무효한 값으로 바뀐다 — 게이트 검사 하나에만 기대지 않는다")
    void withdraw_invalidatesPassword() {
        Member member = saveMember("wdUser3", Role.BUYER, "홍길동");
        String before = member.getPassword();

        memberService.withdraw(member.getId(), "rawPassword");

        String after = memberRepository.findById(member.getId()).orElseThrow().getPassword();
        assertNotEquals(before, after);
        assertFalse(passwordEncoder.matches("rawPassword", after),
                "원래 비밀번호로 다시 인증되면 안 된다");
    }

    @Test
    @DisplayName("관리자는 스스로 탈퇴할 수 없다 — 마지막 관리자가 나가면 관리자 화면에 아무도 못 들어간다")
    void withdraw_admin_rejected() {
        Member admin = saveMember("wdAdmin", Role.ADMIN, "관리자");

        BusinessException e = assertThrows(BusinessException.class,
                () -> memberService.withdraw(admin.getId(), "rawPassword"));
        assertEquals(ErrorCode.FORBIDDEN, e.getErrorCode());
    }

    @Test
    @DisplayName("이미 탈퇴한 계정은 다시 탈퇴할 수 없다")
    void withdraw_twice_rejected() {
        Member member = saveMember("wdUser4", Role.BUYER, "홍길동");
        memberService.withdraw(member.getId(), "rawPassword");

        BusinessException e = assertThrows(BusinessException.class,
                () -> memberService.withdraw(member.getId(), "rawPassword"));
        assertEquals(ErrorCode.ACCOUNT_WITHDRAWN, e.getErrorCode());
    }

    @Test
    @DisplayName("판매자가 탈퇴하면 그 사람 상품이 전부 숨겨진다 — 배송할 사람 없는 상품이 계속 팔리면 안 된다")
    void withdraw_seller_hidesProducts() {
        Member seller = saveMember("wdSeller", Role.SELLER, "판매자");
        Product visible = productRepository.save(
                new Product(seller, "탈퇴 전 상품", "설명", 10000, 5, null));
        assertFalse(visible.isHidden());

        memberService.withdraw(seller.getId(), "rawPassword");

        assertTrue(productRepository.findById(visible.getId()).orElseThrow().isHidden(),
                "탈퇴한 판매자의 상품이 계속 노출되면 아무도 배송하지 않을 주문이 들어온다");
    }

    private Member saveMember(String username, Role role, String name) {
        Member member = new Member(username, passwordEncoder.encode("rawPassword"),
                name, username + "@test.com", role);
        member.verifyEmail();
        return memberRepository.save(member);
    }
}
