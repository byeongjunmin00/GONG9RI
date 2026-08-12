package com.gong9ri.gong9ri.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    // 로그인 고도화 2단계(이메일 인증) — 기존 row 있는 테이블에 NOT NULL 컬럼을 추가하는 마이그레이션이라
    // @ColumnDefault로 실제 SQL DEFAULT false 절을 만들어서 기존 row도 안전하게 처리되게 한다
    // (docs/dev/auth/email-verification/design.md에 로컬 dev DB 실측 결과 기록).
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean emailVerified;

    // 로그인 고도화 3단계(소셜 로그인) — 카카오 계정과 연동된 회원만 값이 있다(일반 회원가입 계정은
    // null). UNIQUE라 같은 카카오 계정으로 중복 가입되지 않는다(docs/dev/auth/social-login/design.md).
    @Column(unique = true, length = 100)
    private String kakaoId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Member(String username, String password, String name, String email, Role role) {
        this.username = username;
        this.password = password;
        this.name = name;
        this.email = email;
        this.role = role;
        this.emailVerified = false;
    }

    public void verifyEmail() {
        this.emailVerified = true;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    // 카카오 신규 가입 전용 팩토리 — 일반 가입(생성자)과 달리 이메일 인증을 건너뛴다(카카오 로그인
    // 자체가 본인 확인 수단이라 우리 쪽 인증 게이트가 의미 없음, 이메일 동의를 안 받은 경우 placeholder
    // 이메일이라 애초에 인증 메일을 보낼 수도 없다 — docs/dev/auth/social-login/design.md).
    public static Member ofKakao(String kakaoId, String username, String encodedPassword, String name, String email) {
        Member member = new Member(username, encodedPassword, name, email, Role.BUYER);
        member.kakaoId = kakaoId;
        member.emailVerified = true;
        return member;
    }
}
