package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.dto.MemberResponse;
import com.gong9ri.gong9ri.dto.MemberSignupRequest;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Transactional
    public MemberResponse signup(MemberSignupRequest request) {
        if (memberRepository.existsByUsername(request.username())) {
            throw new BusinessException(ErrorCode.DUPLICATE_USERNAME);
        }

        Member member = new Member(
                request.username(),
                passwordEncoder.encode(request.password()),
                request.name(),
                request.email(),
                request.role()
        );
        Member saved = memberRepository.save(member);
        log.info("회원가입 완료: memberId={}, username={}", saved.getId(), saved.getUsername());
        return MemberResponse.from(saved);
    }
}
