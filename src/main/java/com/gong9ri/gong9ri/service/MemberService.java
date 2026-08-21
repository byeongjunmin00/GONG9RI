package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.client.KakaoUserInfo;
import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.dto.KakaoLoginResult;
import com.gong9ri.gong9ri.dto.MemberInfoUpdateRequest;
import com.gong9ri.gong9ri.dto.MemberResponse;
import com.gong9ri.gong9ri.dto.MemberSignupRequest;
import com.gong9ri.gong9ri.config.CacheConfig;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.event.MemberEmailChangedEvent;
import com.gong9ri.gong9ri.event.MemberSignedUpEvent;
import com.gong9ri.gong9ri.repository.MemberRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import com.gong9ri.gong9ri.repository.ProductRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    // 판매자 탈퇴 시 그 사람 상품을 숨기기 위해 필요하다(배송할 사람 없는 상품이 계속 팔리는 걸 막는다).
    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ProductImageStorage productImageStorage;

    @Transactional
    public MemberResponse updateProfileImage(Long memberId, MultipartFile file) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        String oldUrl = member.getProfileImageUrl();
        String url = productImageStorage.store(file);
        member.updateProfileImage(url);
        // 옛 파일 삭제는 **커밋 이후**로 미룬다. 커밋 전에 지우면 롤백됐을 때 DB는 옛 URL을 가리키는데
        // 그 파일은 이미 없는 상태가 되어 깨진 이미지가 남는다. 상품 삭제에서 내린 판단과 같은 원칙이다
        // (ProductService.deleteInternal 주석 참고 — "삭제 실패 시 파일만 사라지는 상태를 만들지 않는다").
        deleteFileAfterCommit(oldUrl);
        log.info("프로필 사진 변경 완료: memberId={}, url={}", memberId, url);
        return MemberResponse.from(member);
    }

    @Transactional
    public MemberResponse deleteProfileImage(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        String oldUrl = member.getProfileImageUrl();
        member.updateProfileImage(null);
        deleteFileAfterCommit(oldUrl);
        log.info("프로필 사진 삭제 완료: memberId={}", memberId);
        return MemberResponse.from(member);
    }

    /**
     * 회원 탈퇴 (member/withdraw, 2026-08-21).
     *
     * <p><b>행을 지우지 않는다.</b> 이 회원을 참조하는 테이블이 12개이고, 결제·공구팀 참여는 남의
     * 화면에도 영향을 준다 — 지우면 판매자 정산 합계가 틀어지고 같은 팀에 있던 다른 사람들 화면에서
     * 인원이 어긋난다. 관리자 하드 삭제를 "활동 기록이 하나도 없을 때만" 허용한 것과 같은 판단이다.
     *
     * <p><b>비밀번호를 다시 확인시키는 이유</b>: 되돌릴 수 없는 동작이고, 로그인된 브라우저를 잠깐
     * 빌려 쓴 사람이 누를 수 있다. 카카오 계정은 비밀번호가 없으므로(랜덤 값으로 채워져 있다)
     * 이 확인을 건너뛴다 — 대신 프론트가 한 번 더 확인을 받는다.
     *
     * <p>탈퇴 후 프로필 사진 파일은 커밋 뒤에 지운다(교체·삭제와 같은 이유 — 롤백되면 DB는 옛 URL을
     * 가리키는데 파일이 없는 상태가 된다).
     */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.PRODUCT_LIST_CACHE, allEntries = true)
    public void withdraw(Long memberId, String rawPassword) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (member.isWithdrawn()) {
            throw new BusinessException(ErrorCode.ACCOUNT_WITHDRAWN);
        }
        // 관리자는 스스로 탈퇴할 수 없다 — 마지막 관리자가 나가면 아무도 관리자 화면에 들어갈 수 없다.
        if (member.getRole() == Role.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        boolean socialAccount = member.getKakaoId() != null;
        if (!socialAccount) {
            if (rawPassword == null || !passwordEncoder.matches(rawPassword, member.getPassword())) {
                throw new BusinessException(ErrorCode.LOGIN_FAILED);
            }
        }

        // 판매자가 나가면 그 사람 상품을 전부 숨긴다. 안 그러면 **배송할 사람이 없는 상품이 계속
        // 팔린다** — 탈퇴는 로그인만 막을 뿐 상품 목록과는 무관하기 때문. 지우지 않고 숨기는 이유는
        // 기존 주문의 상품 정보(이름·가격)가 주문 내역·정산에 그대로 필요해서다. 관리자 숨김과 같은
        // 플래그를 재사용한다(2026-08-21 탈퇴 기능 작업 중 발견).
        int hiddenCount = 0;
        if (member.getRole() == Role.SELLER) {
            for (Product product : productRepository.findAllBySellerIdOrderByCreatedAtDesc(memberId)) {
                if (!product.isHidden()) {
                    product.hide();
                    hiddenCount++;
                }
            }
        }

        String oldProfileImage = member.getProfileImageUrl();
        member.withdraw(passwordEncoder.encode(UUID.randomUUID().toString()));
        deleteFileAfterCommit(oldProfileImage);
        log.info("회원 탈퇴 완료: memberId={}, social={}, 숨긴 상품={}", memberId, socialAccount, hiddenCount);
    }

    @Transactional
    public MemberResponse signup(MemberSignupRequest request) {
        // 관리자는 공개 가입으로 절대 만들 수 없다 — 안 그러면 누구나 {"role":"ADMIN"}으로 회원가입해
        // 관리자가 될 수 있다. 최초 관리자 계정은 배포 후 DB에 직접 심는다(docs/dev/admin/design.md).
        if (request.role() == Role.ADMIN) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
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

    /**
     * 커밋이 확정된 뒤에 파일을 지운다.
     *
     * <p>파일 시스템은 트랜잭션에 참여하지 않으므로, 커밋 전에 지우면 롤백돼도 파일은 돌아오지 않는다.
     * 반대로 커밋 후에 지우다 실패하면 <b>쓰이지 않는 파일이 남을 뿐</b>이라 화면은 멀쩡하다.
     * 둘 중 후자가 명백히 덜 나쁘다.
     *
     * <p>트랜잭션 밖에서 호출되면(동기화가 없으면) 즉시 지운다.
     */
    private void deleteFileAfterCommit(String url) {
        if (url == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            productImageStorage.delete(url);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                productImageStorage.delete(url);
            }
        });
    }
}
