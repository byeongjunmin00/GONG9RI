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
                        .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
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
