package com.gong9ri.gong9ri.controller;

import com.gong9ri.gong9ri.common.response.ApiResponse;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.MemberResponse;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/member/profile-image")
@RequiredArgsConstructor
public class MemberProfileImageController {

    private final MemberService memberService;
    private final SecurityContextRepository securityContextRepository;

    @PostMapping
    public ResponseEntity<ApiResponse<MemberResponse>> uploadProfileImage(
            @AuthenticationPrincipal MemberUserDetails principal,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        MemberResponse response = memberService.updateProfileImage(principal.getMember().getId(), file);
        refreshSessionPrincipal(principal.getMember(), response, httpRequest, httpResponse);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<MemberResponse>> deleteProfileImage(
            @AuthenticationPrincipal MemberUserDetails principal,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        MemberResponse response = memberService.deleteProfileImage(principal.getMember().getId());
        refreshSessionPrincipal(principal.getMember(), response, httpRequest, httpResponse);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 세션의 SecurityContext가 들고 있는 principal은 로그인 시점 스냅샷이라, DB만 바꾸고 세션을
    // 안 갱신하면 이후 GET /api/auth/me나 헤더 표시가 사진 변경 전 값을 계속 보여준다
    // (AuthController.updateMe()와 동일한 문제·동일한 해법, docs/dev/mypage/view/design.md 참고).
    private void refreshSessionPrincipal(Member member, MemberResponse response,
                                          HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        member.updateProfileImage(response.profileImageUrl());
        MemberUserDetails newPrincipal = new MemberUserDetails(member);
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(newPrincipal, null, newPrincipal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
    }
}
