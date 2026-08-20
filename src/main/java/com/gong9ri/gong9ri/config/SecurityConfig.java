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
                        .requestMatchers("/", "/*.html", "/**/*.html", "/css/**", "/js/**", "/partials/**", "/images/**").permitAll()
                        // 업로드된 상품 이미지(product/image) — 상품 목록·상세가 비로그인에도 공개되므로
                        // 그 이미지도 같은 등급으로 열어야 한다(로그인해야 상품 사진이 보이면 안 된다).
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                        // 검색엔진 크롤러가 인증 없이 읽어야 하는 것들(SEO).
                        .requestMatchers(HttpMethod.GET, "/robots.txt", "/sitemap.xml").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
                        // 로그아웃은 "이미 로그아웃된 상태"에서 호출돼도 성공해야 한다(멱등).
                        // 인증을 요구하면, 세션이 만료된 뒤 로그아웃 버튼을 눌렀을 때 401이 나면서
                        // 화면상 아무 일도 일어나지 않는다 — 사용자에겐 "로그아웃이 안 되는" 버그로 보인다
                        // (2026-08-20 실제 리포트: 페이지를 오래 열어두면 헤더는 로그인 상태로 그려져 있는데
                        //  서버 세션은 이미 만료돼 있어 이 상황이 생긴다).
                        // AuthController.logout()은 이미 session이 null이어도 안전하게 동작하도록 짜여 있는데
                        // (getSession(false) 후 null 체크) 인가 설정이 그 경로에 도달조차 못 하게 막고 있었다.
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
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
                        // 공구팀 참여자 목록 — 마스킹된 이름만 노출되고, 팀 목록의 currentCount도 이미
                        // 비로그인에 공개돼 있어 같은 정보 등급으로 취급한다(사용자 결정,
                        // docs/dev/team/crud/changes/ "공구팀 상세 — 참여자 목록 표시" 참고).
                        .requestMatchers(HttpMethod.GET, "/api/teams/*/participants").permitAll()
                        // 공구팀 정원 브로드캐스트용 STOMP 핸드셰이크 — 이미 GET /api/products/**로
                        // 공개된 정보를 실시간으로 밀어주는 것뿐이라 인증 불필요.
                        .requestMatchers("/ws-team/**").permitAll()
                        // /ws-support(관리자 1:1 상담)는 **일부러 여기에 넣지 않는다** — anyRequest()
                        // .authenticated()에 걸려 핸드셰이크부터 로그인을 요구한다. 공구팀 정원
                        // 브로드캐스트(/ws-team)는 공개 정보라 비로그인도 붙을 수 있지만, 상담은
                        // 사적인 대화라 그러면 안 되어 엔드포인트를 나눴다(support/chat).
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
