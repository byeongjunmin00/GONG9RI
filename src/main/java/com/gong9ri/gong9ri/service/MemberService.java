package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.dto.MemberResponse;
import com.gong9ri.gong9ri.dto.MemberSignupRequest;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.event.MemberSignedUpEvent;
import com.gong9ri.gong9ri.repository.MemberRepository;
import java.util.Optional;
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
}
