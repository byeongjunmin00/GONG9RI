package com.gong9ri.gong9ri.controller;

import com.gong9ri.gong9ri.common.response.ApiResponse;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.MemberWithdrawRequest;
import com.gong9ri.gong9ri.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 탈퇴 (member/withdraw, 2026-08-21).
 *
 * <p>탈퇴 직후 <b>세션을 반드시 끊는다.</b> 안 그러면 이미 로그인된 그 브라우저는 탈퇴한 계정으로
 * 계속 돌아다닐 수 있다 — 로그인 게이트는 "다시 로그인할 때"만 막기 때문이다.
 */
@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
public class MemberWithdrawController {

    private final MemberService memberService;

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @AuthenticationPrincipal MemberUserDetails principal,
            @RequestBody(required = false) MemberWithdrawRequest request,
            HttpServletRequest httpRequest) {
        memberService.withdraw(principal.getMember().getId(),
                request == null ? null : request.password());

        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
