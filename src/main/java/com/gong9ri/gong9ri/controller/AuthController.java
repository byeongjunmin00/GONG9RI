package com.gong9ri.gong9ri.controller;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.common.mail.EmailService;
import com.gong9ri.gong9ri.common.response.ApiResponse;
import com.gong9ri.gong9ri.common.security.LoginAttemptGuard;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.common.security.TokenService;
import com.gong9ri.gong9ri.dto.EmailVerificationResendRequest;
import com.gong9ri.gong9ri.dto.MemberLoginRequest;
import com.gong9ri.gong9ri.dto.MemberResponse;
import com.gong9ri.gong9ri.dto.MemberSignupRequest;
import com.gong9ri.gong9ri.dto.PasswordResetConfirmRequest;
import com.gong9ri.gong9ri.dto.PasswordResetRequestDto;
import com.gong9ri.gong9ri.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final Duration VERIFICATION_TOKEN_TTL = Duration.ofHours(24);
    private static final Duration PASSWORD_RESET_TOKEN_TTL = Duration.ofMinutes(30);

    private final MemberService memberService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final LoginAttemptGuard loginAttemptGuard;
    private final TokenService tokenService;
    private final EmailService emailService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<MemberResponse>> signup(@Valid @RequestBody MemberSignupRequest request) {
        MemberResponse response = memberService.signup(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<MemberResponse>> login(@Valid @RequestBody MemberLoginRequest request,
                                                               HttpServletRequest httpRequest,
                                                               HttpServletResponse httpResponse) {
        if (loginAttemptGuard.isLocked(request.username())) {
            log.warn("로그인 시도 제한(계정 잠금)으로 거절: username={}", request.username());
            throw new BusinessException(ErrorCode.LOGIN_ATTEMPTS_EXCEEDED);
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (AuthenticationException e) {
            loginAttemptGuard.recordFailure(request.username());
            log.warn("로그인 실패: username={}", request.username());
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        loginAttemptGuard.recordSuccess(request.username());

        MemberUserDetails candidate = (MemberUserDetails) authentication.getPrincipal();
        if (!candidate.getMember().isEmailVerified()) {
            log.warn("미인증 이메일 계정 로그인 거절: memberId={}, username={}",
                    candidate.getMember().getId(), request.username());
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        MemberUserDetails principal = (MemberUserDetails) authentication.getPrincipal();
        log.info("로그인 성공: memberId={}, username={}", principal.getMember().getId(), principal.getMember().getUsername());
        MemberResponse response = MemberResponse.from(principal.getMember());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberResponse>> me(@AuthenticationPrincipal MemberUserDetails principal) {
        MemberResponse response = MemberResponse.from(principal.getMember());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    // 이메일 안의 링크를 브라우저로 직접 클릭해서 들어오는 요청이라, JSON이 아니라 간단한 안내 HTML을
    // 직접 응답한다(한 번 보고 마는 랜딩이라 별도 정적 페이지까지는 안 만듦).
    @GetMapping(value = "/verify-email", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> verifyEmail(@RequestParam String token) {
        return tokenService.resolveAndConsume("email-verify", token)
                .map(memberId -> {
                    memberService.verifyEmail(memberId);
                    log.info("이메일 인증 링크 처리 완료: memberId={}", memberId);
                    return ResponseEntity.ok(htmlMessage("이메일 인증 완료", "이메일 인증이 완료됐습니다. 이제 로그인할 수 있어요."));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(htmlMessage("인증 실패", "유효하지 않거나 만료된 링크입니다. 인증 메일을 다시 요청해주세요.")));
    }

    @PostMapping("/verify-email/resend")
    public ResponseEntity<ApiResponse<Void>> resendVerificationEmail(@Valid @RequestBody EmailVerificationResendRequest request) {
        memberService.findByUsername(request.username())
                .filter(member -> !member.isEmailVerified())
                .ifPresent(member -> {
                    String token = tokenService.issue("email-verify", member.getId(), VERIFICATION_TOKEN_TTL);
                    emailService.sendVerificationEmail(member.getEmail(), token);
                });
        // 계정이 없거나 이미 인증된 경우도 포함해서 항상 같은 응답 — 계정 존재 여부를 노출하지 않는다.
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/password/reset-request")
    public ResponseEntity<ApiResponse<Void>> requestPasswordReset(@Valid @RequestBody PasswordResetRequestDto request) {
        memberService.findByEmail(request.email())
                .ifPresent(member -> {
                    String token = tokenService.issue("password-reset", member.getId(), PASSWORD_RESET_TOKEN_TTL);
                    emailService.sendPasswordResetEmail(member.getEmail(), token);
                });
        // 존재 여부와 무관하게 항상 같은 응답 — 어떤 이메일이 가입돼 있는지 노출하지 않는다.
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/password/reset")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody PasswordResetConfirmRequest request) {
        Long memberId = tokenService.resolveAndConsume("password-reset", request.token())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_OR_EXPIRED_TOKEN));
        memberService.changePassword(memberId, request.newPassword());
        log.info("비밀번호 재설정 링크 처리 완료: memberId={}", memberId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private String htmlMessage(String title, String message) {
        return "<!DOCTYPE html><html lang=\"ko\"><head><meta charset=\"UTF-8\"><title>" + title
                + " — GONG9RI</title></head><body style=\"font-family:sans-serif;text-align:center;padding:80px 20px;\">"
                + "<h1>" + title + "</h1><p>" + message + "</p><p><a href=\"/login.html\">로그인 페이지로 이동</a></p>"
                + "</body></html>";
    }
}
