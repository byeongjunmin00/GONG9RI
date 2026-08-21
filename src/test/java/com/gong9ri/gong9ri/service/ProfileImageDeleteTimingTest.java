package com.gong9ri.gong9ri.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.repository.MemberRepository;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 프로필 사진 교체 시 <b>옛 파일을 언제 지우는지</b> 고정한다.
 *
 * <p>파일 시스템은 트랜잭션에 참여하지 않는다. 커밋 전에 지우면 <b>롤백돼도 파일은 돌아오지 않아</b>
 * DB는 옛 URL을 가리키는데 그 파일이 없는 상태가 된다(깨진 이미지). 반대로 커밋 후에 지우다 실패하면
 * 쓰이지 않는 파일이 남을 뿐이라 화면은 멀쩡하다 — 후자가 명백히 덜 나쁘다.
 *
 * <p>{@code ProductService}가 상품 삭제에서 내린 판단과 같은 원칙이다.
 */
@SpringBootTest
class ProfileImageDeleteTimingTest {

    @Autowired
    private MemberService memberService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Value("${app.upload.dir}")
    private String uploadDir;

    private Long memberIdToClean;

    @AfterEach
    void cleanUp() {
        if (memberIdToClean != null) {
            memberRepository.deleteById(memberIdToClean);
            memberIdToClean = null;
        }
    }

    private MockMultipartFile image(String name) throws IOException {
        BufferedImage img = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 200, 200);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return new MockMultipartFile("file", name, "image/png", out.toByteArray());
    }

    private Path pathOf(String url) {
        return Path.of(uploadDir).toAbsolutePath().normalize()
                .resolve(url.substring("/uploads/".length()));
    }

    @Test
    @DisplayName("트랜잭션이 롤백되면 옛 사진 파일이 살아있어야 한다 — 커밋 전에 지우면 안 된다")
    void oldFileSurvivesRollback() throws IOException {
        Member member = memberRepository.save(
                new Member("pf-rollback", "pw", "롤백테스트", "pf-rollback@test.com", Role.BUYER));
        memberIdToClean = member.getId();

        String firstUrl = memberService.updateProfileImage(member.getId(), image("first.png")).profileImageUrl();
        assertTrue(Files.exists(pathOf(firstUrl)), "첫 사진 파일이 저장돼야 한다");

        // 두 번째 교체를 시도하다 롤백시킨다.
        try {
            transactionTemplate.execute(status -> {
                try {
                    memberService.updateProfileImage(member.getId(), image("second.png"));
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
                status.setRollbackOnly();
                return null;
            });
        } catch (Exception ignored) {
            // 롤백 자체가 목적이라 예외는 무시한다.
        }

        assertTrue(Files.exists(pathOf(firstUrl)),
                "롤백됐으면 DB는 첫 사진을 가리킨다 — 그 파일이 지워져 있으면 깨진 이미지가 된다");
    }

    @Test
    @DisplayName("정상 커밋되면 옛 사진 파일은 지워진다 — 안 쓰는 파일이 쌓이지 않게")
    void oldFileDeletedAfterCommit() throws IOException {
        Member member = memberRepository.save(
                new Member("pf-commit", "pw", "커밋테스트", "pf-commit@test.com", Role.BUYER));
        memberIdToClean = member.getId();

        String firstUrl = memberService.updateProfileImage(member.getId(), image("a.png")).profileImageUrl();
        memberService.updateProfileImage(member.getId(), image("b.png"));

        assertFalse(Files.exists(pathOf(firstUrl)), "교체가 확정되면 옛 파일은 정리돼야 한다");
    }
}
