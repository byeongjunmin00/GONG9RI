package com.gong9ri.gong9ri.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                            SecurityContextRepository securityContextRepository,
                                            AuthenticationEntryPoint authenticationEntryPoint) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(authenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        // "/*.html"은 단일 세그먼트만 매칭하므로(예: /login.html), 서브디렉토리 정적 페이지
                        // (예: /seller/products/new.html)까지 허용하도록 "/**/*.html"을 나란히 추가한다.
                        .requestMatchers("/", "/*.html", "/**/*.html", "/css/**", "/js/**", "/partials/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
                        // 로그인 고도화 2단계 — 전부 "아직 로그인 못 하는 상태"의 사용자가 쓰는 기능이라
                        // 인증 없이 열어야 한다(이메일 인증, 비밀번호 재설정).
                        .requestMatchers(HttpMethod.GET, "/api/auth/verify-email").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/verify-email/resend").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/password/reset-request", "/api/auth/password/reset").permitAll()
                        // 로그인 고도화 3단계 — 카카오 로그인. 인가 요청/콜백 둘 다 로그인 전 사용자가
                        // 쓰는 흐름이라 인증 없이 열어야 한다.
                        .requestMatchers(HttpMethod.GET, "/api/auth/kakao/login", "/api/auth/kakao/callback").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                        // 리뷰 목록 조회는 비로그인도 볼 수 있어야 한다(상품 상세 페이지 공개 정보의 일부).
                        // 작성/수정/삭제는 별도 명시 없이 anyRequest().authenticated()로 막힌다.
                        .requestMatchers(HttpMethod.GET, "/api/products/*/reviews").permitAll()
                        // 공구팀 정원 브로드캐스트용 STOMP 핸드셰이크 — 이미 GET /api/products/**로
                        // 공개된 정보를 실시간으로 밀어주는 것뿐이라 인증 불필요.
                        .requestMatchers("/ws-team/**").permitAll()
                        // 배포 고도화(도전과제) — Railway가 배포 게이팅에 쓰는 헬스체크 경로.
                        // 인증을 요구하면 Railway의 헬스체크 프로버가 401을 받아 배포가 영원히
                        // 대기하게 되므로 반드시 permitAll이어야 한다.
                        .requestMatchers("/actuator/health").permitAll()
                        // PortOne 웹훅 콜백 — PG가 직접 호출하므로 세션 인증 불가. 서명 검증
                        // (PortOneWebhookVerifier)이 곧 인증 역할을 한다(docs/dev/payment/portone/design.md).
                        .requestMatchers(HttpMethod.POST, "/api/webhooks/portone").permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }
}
