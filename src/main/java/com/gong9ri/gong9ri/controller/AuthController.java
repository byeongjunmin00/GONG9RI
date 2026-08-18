package com.gong9ri.gong9ri.controller;

import com.gong9ri.gong9ri.client.KakaoClient;
import com.gong9ri.gong9ri.client.KakaoUserInfo;
import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.common.mail.EmailService;
import com.gong9ri.gong9ri.common.response.ApiResponse;
import com.gong9ri.gong9ri.common.security.LoginAttemptGuard;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.common.security.TokenService;
import com.gong9ri.gong9ri.dto.EmailVerificationResendRequest;
import com.gong9ri.gong9ri.dto.KakaoLoginResult;
import com.gong9ri.gong9ri.dto.MemberInfoUpdateRequest;
import com.gong9ri.gong9ri.dto.MemberLoginRequest;
import com.gong9ri.gong9ri.dto.MemberResponse;
import com.gong9ri.gong9ri.dto.MemberSignupRequest;
import com.gong9ri.gong9ri.dto.PasswordResetConfirmRequest;
import com.gong9ri.gong9ri.dto.PasswordResetRequestDto;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.service.MemberService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.bind.annotation.PatchMapping;
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

    private static final String KAKAO_OAUTH_STATE_SESSION_KEY = "kakao_oauth_state";
    private static final String KAKAO_OAUTH_ROLE_SESSION_KEY = "kakao_oauth_role";

    private final MemberService memberService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final LoginAttemptGuard loginAttemptGuard;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final KakaoClient kakaoClient;

    @Value("${app.base-url}")
    private String appBaseUrl;

    @Value("${kakao.client-id}")
    private String kakaoClientId;

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
        if (candidate.getMember().isSuspended()) {
            log.warn("정지된 계정 로그인 거절: memberId={}, username={}",
                    candidate.getMember().getId(), request.username());
            throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
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

    // 세션의 SecurityContext가 들고 있는 principal은 로그인 시점에 로드된 Member 스냅샷이라, DB만
    // 바꾸고 세션을 안 갱신하면 이후 GET /me나 헤더 표시가 수정 전 값을 계속 보여주게 된다 —
    // login()/kakaoCallback()과 동일하게 갱신된 principal로 SecurityContext를 다시 세팅한다.
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<MemberResponse>> updateMe(
            @AuthenticationPrincipal MemberUserDetails principal,
            @Valid @RequestBody MemberInfoUpdateRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        Member updated = memberService.updateInfo(principal.getMember().getId(), request);

        MemberUserDetails newPrincipal = new MemberUserDetails(updated);
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(newPrincipal, null, newPrincipal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        return ResponseEntity.ok(ApiResponse.success(MemberResponse.from(updated)));
    }

    // 세션 무효화만으로는 (1) 현재 요청 스레드에 남아있는 SecurityContextHolder의 인증 정보,
    // (2) 브라우저가 여전히 들고 있는 세션 쿠키(JSESSIONID)가 정리되지 않는다 — 둘 다 확실히
    // 정리해야 "로그아웃했는데 인증된 것처럼 보이는" 문제가 재현되지 않는다.
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();

        Cookie sessionCookie = new Cookie("JSESSIONID", null);
        sessionCookie.setPath("/");
        sessionCookie.setHttpOnly(true);
        sessionCookie.setMaxAge(0);
        httpResponse.addCookie(sessionCookie);

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

    // 로그인 고도화 3단계 — 카카오 로그인. 이 프로젝트는 세션 인증을 전부 수동으로 구현해왔어서
    // (spring-boot-starter-oauth2-client의 oauth2Login() 자동 필터를 안 씀) 카카오도 같은 방식(직접
    // Authorization Code 흐름 구현)으로 일관되게 간다 — docs/dev/auth/social-login/design.md.
    @GetMapping("/kakao/login")
    public void kakaoLogin(@RequestParam(required = false) String role,
                            HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws IOException {
        String state = UUID.randomUUID().toString();
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(KAKAO_OAUTH_STATE_SESSION_KEY, state);
        // 신규 가입일 때만 쓰는 의도된 role — 회원가입 페이지의 "구매자로 가입"/"판매자로 가입" 카카오
        // 버튼이 넘겨준다. role 파라미터가 없거나 잘못된 값이면(로그인 페이지의 일반 "카카오로 로그인"
        // 버튼 경로) 세션에 아무 것도 남기지 않는다 — 콜백에서 "명시적으로 role을 선택하고 들어온 진입"과
        // "일반 로그인 진입"을 구분해서, 후자는 role 불일치 안내를 띄우지 않게 하기 위함이다.
        Role parsedRole = parseRoleOrNull(role);
        if (parsedRole != null) {
            session.setAttribute(KAKAO_OAUTH_ROLE_SESSION_KEY, parsedRole);
        }

        // prompt=login — 카카오 자체 로그인 세션(기본 24시간, "로그인 상태 유지" 선택 시 최대 1개월)이
        // 남아있어도 매번 카카오 로그인 화면을 다시 띄우게 강제한다. 우리 앱에서 로그아웃한 뒤 다시
        // "카카오로 로그인"을 눌렀을 때, 카카오 쪽 세션 때문에 재인증 없이 바로 로그인되는 걸 막기 위함
        // (카카오톡 인앱 브라우저에서는 이 파라미터가 지원되지 않는다 — 카카오 공식 문서에 명시된 제한).
        String authorizeUrl = "https://kauth.kakao.com/oauth/authorize"
                + "?client_id=" + URLEncoder.encode(kakaoClientId, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(kakaoRedirectUri(), StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&prompt=login"
                + "&state=" + state;
        httpResponse.sendRedirect(authorizeUrl);
    }

    @GetMapping("/kakao/callback")
    public void kakaoCallback(@RequestParam(required = false) String code,
                               @RequestParam(required = false) String state,
                               @RequestParam(required = false) String error,
                               HttpServletRequest httpRequest,
                               HttpServletResponse httpResponse) throws IOException {
        HttpSession session = httpRequest.getSession(false);
        Object savedState = session != null ? session.getAttribute(KAKAO_OAUTH_STATE_SESSION_KEY) : null;
        if (error != null || code == null || savedState == null || !savedState.equals(state)) {
            log.warn("카카오 로그인 거절: error={}, stateOk={}", error, savedState != null && savedState.equals(state));
            httpResponse.sendRedirect("/login.html?error=kakao");
            return;
        }
        session.removeAttribute(KAKAO_OAUTH_STATE_SESSION_KEY); // state는 1회성
        Role intendedRole = (Role) session.getAttribute(KAKAO_OAUTH_ROLE_SESSION_KEY);
        session.removeAttribute(KAKAO_OAUTH_ROLE_SESSION_KEY);
        // 세션에 저장된 값이 있으면 role을 명시적으로 골라 들어온 진입(회원가입 페이지의 역할별 버튼)이다.
        // 없으면 role 파라미터 없는 일반 "카카오로 로그인" 버튼 경로 — 이 경우는 role 불일치가 있어도
        // 안내하지 않는다(현행 유지).
        boolean explicitRoleRequested = intendedRole != null;
        if (intendedRole == null) {
            intendedRole = Role.BUYER;
        }

        try {
            String accessToken = kakaoClient.exchangeCodeForAccessToken(code, kakaoRedirectUri());
            if (accessToken == null || accessToken.isBlank()) {
                // 카카오가 200을 응답했는데 access_token이 비어있는 비정상 케이스 — 이 상태로 getUserInfo를
                // 호출하면 "Bearer null" 헤더로 불필요한 API 호출을 한 번 더 하고 나서야 실패하게 되므로,
                // 여기서 바로 실패시켜 원인이 로그에 명확히 남게 한다.
                throw new IllegalStateException("카카오 액세스 토큰 발급 실패(응답에 access_token 없음)");
            }
            KakaoUserInfo userInfo = kakaoClient.getUserInfo(accessToken);
            KakaoLoginResult result = memberService.findOrCreateByKakao(userInfo, intendedRole);
            Member member = result.member();

            MemberUserDetails principal = new MemberUserDetails(member);
            Authentication authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, httpRequest, httpResponse);

            log.info("카카오 로그인 성공: memberId={}", member.getId());
            if (explicitRoleRequested && result.roleMismatch()) {
                // 이미 다른 role로 가입된 계정 — 로그인은 기존 role 그대로 진행하되(design.md), 사용자가
                // 고른 진입 버튼과 실제 로그인된 role이 다르다는 걸 메인 페이지에서 안내하도록 신호를 싣는다
                // (login.html의 ?signup=success/?error=kakao와 같은 "쿼리파라미터+배너" 패턴).
                httpResponse.sendRedirect("/?kakaoRoleMismatch=" + member.getRole().name());
            } else {
                httpResponse.sendRedirect("/");
            }
        } catch (Exception e) {
            log.error("카카오 로그인 처리 실패: error={}", e.getMessage(), e);
            httpResponse.sendRedirect("/login.html?error=kakao");
        }
    }

    // redirect_uri는 /kakao/login(인가 요청)과 /kakao/callback(토큰 교환) 양쪽에서 정확히 같은 값이어야
    // 한다는 카카오 API 요구사항 때문에 한 곳에서만 조립한다.
    private String kakaoRedirectUri() {
        return appBaseUrl + "/api/auth/kakao/callback";
    }

    // role 파라미터가 없거나 잘못된 값이면 null — 호출부(kakaoLogin)가 "명시적 role 선택 없음"으로
    // 취급해 세션에 아무 것도 저장하지 않는다(신규 가입 시엔 콜백에서 BUYER로 안전하게 폴백).
    private Role parseRoleOrNull(String role) {
        try {
            return role != null ? Role.valueOf(role) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String htmlMessage(String title, String message) {
        return "<!DOCTYPE html><html lang=\"ko\"><head><meta charset=\"UTF-8\"><title>" + title
                + " — GONG9RI</title></head><body style=\"font-family:sans-serif;text-align:center;padding:80px 20px;\">"
                + "<h1>" + title + "</h1><p>" + message + "</p><p><a href=\"/login.html\">로그인 페이지로 이동</a></p>"
                + "</body></html>";
    }
}
