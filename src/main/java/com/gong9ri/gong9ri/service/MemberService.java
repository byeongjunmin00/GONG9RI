package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.client.KakaoUserInfo;
import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.dto.KakaoLoginResult;
import com.gong9ri.gong9ri.dto.MemberInfoUpdateRequest;
import com.gong9ri.gong9ri.dto.MemberResponse;
import com.gong9ri.gong9ri.dto.MemberSignupRequest;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.event.MemberEmailChangedEvent;
import com.gong9ri.gong9ri.event.MemberSignedUpEvent;
import com.gong9ri.gong9ri.repository.MemberRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public MemberResponse signup(MemberSignupRequest request) {
        if (memberRepository.existsByUsername(request.username())) {
            throw new BusinessException(ErrorCode.DUPLICATE_USERNAME);
        }
        if (memberRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        Member member = new Member(
                request.username(),
                passwordEncoder.encode(request.password()),
                request.name(),
                request.email(),
                request.role()
        );
        Member saved = memberRepository.save(member);
        eventPublisher.publishEvent(new MemberSignedUpEvent(saved.getId(), saved.getEmail()));
        log.info("회원가입 완료: memberId={}, username={}", saved.getId(), saved.getUsername());
        return MemberResponse.from(saved);
    }

    // 로그인 고도화 2단계 — 이메일 인증 재발송/로그인 차단 여부 확인용 조회.
    public Optional<Member> findByUsername(String username) {
        return memberRepository.findByUsername(username);
    }

    // 비밀번호 재설정 요청 시 계정을 찾는 용도(항상 동일한 응답을 반환해 계정 존재 여부를 노출하지 않는
    // 건 컨트롤러 책임).
    public Optional<Member> findByEmail(String email) {
        return memberRepository.findByEmail(email);
    }

    @Transactional
    public void verifyEmail(Long memberId) {
        memberRepository.findById(memberId).ifPresent(member -> {
            member.verifyEmail();
            log.info("이메일 인증 완료: memberId={}", memberId);
        });
    }

    @Transactional
    public void changePassword(Long memberId, String rawPassword) {
        memberRepository.findById(memberId).ifPresent(member -> {
            member.changePassword(passwordEncoder.encode(rawPassword));
            log.info("비밀번호 재설정 완료: memberId={}", memberId);
        });
    }

    /**
     * 마이페이지 — 이름/이메일 정보수정. memberId는 인증된 세션의 principal에서만 오므로 항상
     * 존재가 보장되지만({@code findById} 실패는 재현 불가능한 상태), 방어적으로 예외 처리한다.
     * 이메일이 실제로 바뀐 경우에만 {@code emailVerified}를 초기화하고 재인증 메일을 발송한다 —
     * 기존 값 그대로 제출하거나 이름만 바꾼 경우까지 매번 재인증을 강제하면 불필요하게 번거롭다.
     */
    @Transactional
    public Member updateInfo(Long memberId, MemberInfoUpdateRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalStateException("인증된 회원인데 존재하지 않음: memberId=" + memberId));

        boolean emailChanged = !member.getEmail().equals(request.email());
        if (emailChanged && memberRepository.existsByEmailAndIdNot(request.email(), memberId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        member.updateProfile(request.name(), request.email(), emailChanged);
        if (emailChanged) {
            eventPublisher.publishEvent(new MemberEmailChangedEvent(memberId, request.email()));
        }
        log.info("회원정보 수정 완료: memberId={}, emailChanged={}", memberId, emailChanged);
        return member;
    }

    /**
     * 카카오 로그인 — 이미 연동된 계정이면 그대로 로그인, 처음이면 새로 만든다(로그인 고도화 3단계,
     * docs/dev/auth/social-login/design.md). 이메일 동의를 받았는데 그 이메일이 이미 다른 계정에서
     * 쓰이고 있으면 자동 연동하지 않고 거부한다 — 이메일 소유권을 우리가 검증한 게 아니라서(카카오가
     * 검증했다는 것과 우리 DB의 그 계정이 같은 사람이라는 보장이 없음) 계정 탈취 방지 원칙.
     * <p>이미 연동된 계정으로 다시 들어오면 {@code intendedRole}은 무시하고 로그인은 그대로 진행한다 —
     * 로그인 진입 버튼(구매자용/판매자용)을 잘못 눌러도 기존 계정 role이 바뀌면 안 된다. 다만 그 경우
     * 호출부가 안내를 띄울 수 있도록 {@link KakaoLoginResult#roleMismatch()}로 불일치 여부를 알려준다.
     */
    @Transactional
    public KakaoLoginResult findOrCreateByKakao(KakaoUserInfo kakaoUserInfo, Role intendedRole) {
        String kakaoId = String.valueOf(kakaoUserInfo.id());
        Optional<Member> existing = memberRepository.findByKakaoId(kakaoId);
        if (existing.isPresent()) {
            Member member = existing.get();
            boolean roleMismatch = member.getRole() != intendedRole;
            return new KakaoLoginResult(member, roleMismatch);
        }

        String email = kakaoUserInfo.email();
        if (email != null && memberRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        String username = "kakao_" + kakaoId;
        if (memberRepository.existsByUsername(username)) {
            // 일반 회원가입으로 이미 같은 합성 username을 누가 선점했을 가능성(희박하지만 실존) — signup()의
            // 기존 existsByUsername 사전검증 패턴과 동일하게 조기에 명확히 거부한다.
            throw new BusinessException(ErrorCode.DUPLICATE_USERNAME);
        }
        String resolvedEmail = email != null ? email : "kakao_" + kakaoId + "@kakao.local";
        String name = kakaoUserInfo.nickname() != null ? kakaoUserInfo.nickname() : username;
        String randomPassword = passwordEncoder.encode(UUID.randomUUID().toString());

        Member member = Member.ofKakao(kakaoId, username, randomPassword, name, resolvedEmail, intendedRole);
        Member saved = memberRepository.save(member);
        log.info("카카오 신규 가입 완료: memberId={}, kakaoId={}, role={}", saved.getId(), kakaoId, intendedRole);
        return new KakaoLoginResult(saved, false);
    }
}
