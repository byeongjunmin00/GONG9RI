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

    /**
     * 회원번호(admin-identifier-codes, 2026-08-22). {@code "M" + PK 7자리 zero-pad}
     * ({@link com.gong9ri.gong9ri.common.identifier.IdentifierCodeFormatter#memberCode}) — 별도
     * 채번 테이블 없이 PK를 그대로 파생시킨 값이라 PK처럼 불변이다(`docs/policy/identifier-code.md`).
     *
     * <p><b>당장은 nullable이다(NOT NULL/UNIQUE 아님)</b> — 이 컬럼이 처음 생기는 시점에 이미 존재하는
     * 회원 행은 값이 없고, 회원마다 값이 다른 컬럼이라({@code emailVerified}/{@code suspended}처럼)
     * 상수 {@code @ColumnDefault}로 한 번에 채울 수 없다. 애플리케이션 레벨 백필
     * ({@code IdentifierCodeBackfillService})로 기존 행을 전부 채운 뒤에야 NOT NULL + UNIQUE 제약을
     * 안전하게 걸 수 있다 — 그 전에 걸면 기존 데이터가 있는 DB에서 `ddl-auto: update`의 컬럼 추가
     * 자체가 실패한다(자세한 사정은 `docs/deploy-guide.md`의 "회원번호·상품코드·주문번호·공구팀 번호
     * 배포 절차" 참고).
     */
    @Column(name = "member_code", length = 20)
    private String memberCode;

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

    // 관리자(admin) 계정 정지 — 기존 row 있는 테이블에 NOT NULL 컬럼을 추가하는 마이그레이션이라
    // @ColumnDefault로 SQL DEFAULT false를 만들어 기존 row도 안전하게 처리한다(emailVerified와 동일
    // 패턴). true면 비밀번호가 맞아도 로그인 거절(ACCOUNT_SUSPENDED) — docs/dev/admin/design.md.
    // 하드 삭제 대신 이걸 기본 관리 수단으로 쓴다(FK로 참조하는 테이블이 많아 삭제는 활동 기록이
    // 하나도 없을 때만 허용).
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean suspended;

    /**
     * 회원 탈퇴 시각 (member/withdraw, 2026-08-21). null이면 탈퇴하지 않은 회원.
     *
     * <p><b>행을 지우지 않는 이유</b>: 이 회원을 참조하는 테이블이 12개이고, 그중 결제·공구팀 참여는
     * <b>남의 화면에도 영향을 준다</b> — 지워버리면 판매자 정산 합계가 틀어지고, 같은 공구팀에 있던
     * 다른 사람들 화면에서 인원이 어긋난다. 관리자 삭제가 "활동 기록이 하나도 없을 때만" 하드 삭제를
     * 허용하는 것과 같은 판단이다(docs/dev/admin/design.md).
     *
     * <p>대신 <b>로그인을 막고 이름을 가린다.</b> 정지(suspended)와 컬럼을 나눈 이유는 둘의 뜻이
     * 다르기 때문 — 정지는 관리자가 건 제재라 관리자가 풀 수 있어야 하고, 탈퇴는 본인 의사라
     * 관리자가 임의로 되돌리면 안 된다. 한 컬럼으로 합치면 관리자 화면에서 구분이 안 된다.
     */
    private LocalDateTime withdrawnAt;

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

    /**
     * 회원번호 채번(가입/카카오 신규가입 직후 1회 호출) — PK가 확정된 뒤에만 호출할 수 있다
     * ({@code MemberService.signup}/{@code findOrCreateByKakao}). 백필 서비스도 기존 행에 한해 이걸
     * 재사용한다.
     */
    public void assignMemberCode(String memberCode) {
        this.memberCode = memberCode;
    }

    public void suspend() {
        this.suspended = true;
    }

    public void unsuspend() {
        this.suspended = false;
    }

    /**
     * 회원 탈퇴 (member/withdraw). 행은 남기고 로그인만 막는다.
     *
     * <p>이름·이메일을 지우는 게 아니라 <b>고정 문구로 덮어쓴다</b> — 남의 화면(리뷰 작성자, 공구팀
     * 참여자, 판매자의 주문 목록)에 이 회원의 이름이 이미 노출돼 있어서, 비워두면 그 자리가 빈칸이
     * 되어 화면이 깨진 것처럼 보인다. username은 그대로 둔다(UNIQUE 제약이 걸려 있고, 같은 아이디로
     * 재가입해 남의 기록을 이어받는 걸 막는다).
     *
     * <p>비밀번호는 다시 못 맞추도록 무효한 값으로 바꾼다 — 로그인은 아래 withdrawn 검사로도
     * 막히지만, 검사 하나에만 기대지 않는다.
     */
    public void withdraw(String unusablePassword) {
        this.withdrawnAt = LocalDateTime.now();
        this.name = "탈퇴한 회원";
        this.email = "withdrawn+" + this.id + "@gong9ri.invalid";
        this.password = unusablePassword;
        this.profileImageUrl = null;
        this.kakaoId = null;
    }

    public boolean isWithdrawn() {
        return withdrawnAt != null;
    }

    @Column(length = 500)
    private String profileImageUrl;

    // 정보수정(마이페이지) — 이름은 항상 갱신하고, 이메일은 바뀐 경우에만 emailVerified를 다시
    // false로 되돌린다(가입 때와 같은 원칙 — 실제로 그 주소를 본인이 쓸 수 있는지 재확인 전에는
    // 인증된 상태로 두면 안 됨). emailChanged 여부는 호출부(MemberService)가 변경 전/후 값을
    // 비교해서 넘겨준다 — 엔티티 안에서 다시 비교하면 "새 이메일을 이미 대입한 뒤" 시점이라
    // 원래 값과 비교할 수 없기 때문.
    public void updateProfile(String name, String email, boolean emailChanged) {
        this.name = name;
        this.email = email;
        if (emailChanged) {
            this.emailVerified = false;
        }
    }

    public void updateProfileImage(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    // 카카오 신규 가입 전용 팩토리 — 일반 가입(생성자)과 달리 이메일 인증을 건너뛴다(카카오 로그인
    // 자체가 본인 확인 수단이라 우리 쪽 인증 게이트가 의미 없음, 이메일 동의를 안 받은 경우 placeholder
    // 이메일이라 애초에 인증 메일을 보낼 수도 없다 — docs/dev/auth/social-login/design.md).
    // role은 호출부(카카오 로그인 진입 버튼)에서 구매자/판매자 중 어느 쪽으로 시작했는지 넘겨준다.
    public static Member ofKakao(String kakaoId, String username, String encodedPassword, String name, String email, Role role) {
        Member member = new Member(username, encodedPassword, name, email, role);
        member.kakaoId = kakaoId;
        member.emailVerified = true;
        return member;
    }
}
