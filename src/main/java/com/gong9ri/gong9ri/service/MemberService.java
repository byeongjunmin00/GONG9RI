package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.client.KakaoUserInfo;
import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.dto.MemberResponse;
import com.gong9ri.gong9ri.dto.MemberSignupRequest;
import com.gong9ri.gong9ri.entity.Member;
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
     * 카카오 로그인 — 이미 연동된 계정이면 그대로 로그인, 처음이면 새로 만든다(로그인 고도화 3단계,
     * docs/dev/auth/social-login/design.md). 이메일 동의를 받았는데 그 이메일이 이미 다른 계정에서
     * 쓰이고 있으면 자동 연동하지 않고 거부한다 — 이메일 소유권을 우리가 검증한 게 아니라서(카카오가
     * 검증했다는 것과 우리 DB의 그 계정이 같은 사람이라는 보장이 없음) 계정 탈취 방지 원칙.
     */
    @Transactional
    public Member findOrCreateByKakao(KakaoUserInfo kakaoUserInfo) {
        String kakaoId = String.valueOf(kakaoUserInfo.id());
        Optional<Member> existing = memberRepository.findByKakaoId(kakaoId);
        if (existing.isPresent()) {
            return existing.get();
        }

        String email = kakaoUserInfo.email();
        if (email != null && memberRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        String resolvedEmail = email != null ? email : "kakao_" + kakaoId + "@kakao.local";
        String username = "kakao_" + kakaoId;
        String name = kakaoUserInfo.nickname() != null ? kakaoUserInfo.nickname() : username;
        String randomPassword = passwordEncoder.encode(UUID.randomUUID().toString());

        Member member = Member.ofKakao(kakaoId, username, randomPassword, name, resolvedEmail);
        Member saved = memberRepository.save(member);
        log.info("카카오 신규 가입 완료: memberId={}, kakaoId={}", saved.getId(), kakaoId);
        return saved;
    }
}
