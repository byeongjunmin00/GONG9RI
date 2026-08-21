package com.gong9ri.gong9ri.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 업로드 저장·검증 (product/image).
 *
 * <p><b>파일 업로드는 전형적인 공격 표면</b>이라, 이 테스트의 상당수가 "정상 동작"이 아니라
 * <b>거절해야 할 입력을 실제로 거절하는지</b>를 고정한다 — 확장자만 이미지인 파일, 경로 탈출을 노린
 * 파일명, 크기 초과.
 */
class ProductImageStorageTest {

    @TempDir
    Path tempDir;

    private ProductImageStorage storage() {
        return new ProductImageStorage(tempDir.toString());
    }

    private byte[] pngBytes(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("이미지를 올리면 /uploads/ 아래 경로를 돌려주고 실제 파일이 생긴다")
    void store_returnsUrlAndWritesFile() throws IOException {
        String url = storage().store(new MockMultipartFile("file", "photo.png", "image/png", pngBytes(800, 600)));

        assertTrue(url.startsWith("/uploads/"), "웹에서 접근할 경로를 돌려줘야 한다: " + url);
        Path saved = tempDir.resolve(url.substring("/uploads/".length()));
        assertTrue(Files.exists(saved), "실제 파일이 저장돼야 한다");
        assertTrue(Files.size(saved) > 0);
    }

    @Test
    @DisplayName("[보안] 클라이언트가 보낸 파일명은 저장 경로에 쓰이지 않는다 — 경로 탈출 차단")
    void store_ignoresClientFileName() throws IOException {
        // 파일명에 상위 디렉터리 탈출을 심어 보낸다. 저장 이름을 서버가 UUID로 만들기 때문에
        // 이 문자열은 경로에 전혀 반영되지 않아야 한다.
        String url = storage().store(
                new MockMultipartFile("file", "../../evil.png", "image/png", pngBytes(100, 100)));

        assertFalse(url.contains(".."), "경로 탈출 문자열이 경로에 남으면 안 된다: " + url);
        assertFalse(url.contains("evil"), "클라이언트 파일명이 그대로 쓰이면 안 된다: " + url);
        Path saved = tempDir.resolve(url.substring("/uploads/".length())).normalize();
        assertTrue(saved.startsWith(tempDir), "저장 위치가 업로드 루트를 벗어나면 안 된다");
    }

    @Test
    @DisplayName("[보안] 확장자만 이미지인 파일은 거절한다 — 선언이 아니라 실제 디코딩으로 판정")
    void store_rejectsNonImageWithImageExtension() {
        // Content-Type과 확장자를 image/png로 위장했지만 내용은 텍스트다.
        MockMultipartFile fake = new MockMultipartFile(
                "file", "not-an-image.png", "image/png", "<?php system($_GET['c']); ?>".getBytes());

        BusinessException e = assertThrows(BusinessException.class, () -> storage().store(fake));
        assertEquals(ErrorCode.INVALID_IMAGE_FILE, e.getErrorCode());
    }

    @Test
    @DisplayName("[보안] 5MB를 넘는 파일은 거절한다")
    void store_rejectsTooLargeFile() {
        byte[] tooLarge = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile big = new MockMultipartFile("file", "big.png", "image/png", tooLarge);

        BusinessException e = assertThrows(BusinessException.class, () -> storage().store(big));
        assertEquals(ErrorCode.IMAGE_FILE_TOO_LARGE, e.getErrorCode());
    }

    @Test
    @DisplayName("빈 파일은 거절한다")
    void store_rejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        BusinessException e = assertThrows(BusinessException.class, () -> storage().store(empty));
        assertEquals(ErrorCode.INVALID_IMAGE_FILE, e.getErrorCode());
    }

    @Test
    @DisplayName("큰 이미지는 긴 변 1600px로 줄여서 저장한다 — 볼륨 용량이 실제 제약이라 필수")
    void store_downscalesLargeImage() throws IOException {
        String url = storage().store(
                new MockMultipartFile("file", "huge.png", "image/png", pngBytes(4000, 3000)));

        Path saved = tempDir.resolve(url.substring("/uploads/".length()));
        BufferedImage stored = ImageIO.read(saved.toFile());
        assertEquals(1600, Math.max(stored.getWidth(), stored.getHeight()), "긴 변이 1600px로 맞춰져야 한다");
        assertEquals(1200, Math.min(stored.getWidth(), stored.getHeight()), "가로세로 비율이 유지돼야 한다");
    }

    @Test
    @DisplayName("작은 이미지는 억지로 키우지 않는다")
    void store_doesNotUpscaleSmallImage() throws IOException {
        String url = storage().store(
                new MockMultipartFile("file", "small.png", "image/png", pngBytes(320, 240)));

        BufferedImage stored = ImageIO.read(tempDir.resolve(url.substring("/uploads/".length())).toFile());
        assertEquals(320, stored.getWidth());
        assertEquals(240, stored.getHeight());
    }

    @Test
    @DisplayName("delete()는 저장된 파일을 실제로 지운다")
    void delete_removesStoredFile() throws IOException {
        ProductImageStorage storage = storage();
        String url = storage.store(new MockMultipartFile("file", "photo.png", "image/png", pngBytes(100, 100)));
        Path saved = tempDir.resolve(url.substring("/uploads/".length()));
        assertTrue(Files.exists(saved), "삭제 전엔 파일이 있어야 한다");

        storage.delete(url);

        assertFalse(Files.exists(saved), "delete() 후엔 파일이 없어야 한다");
    }

    @Test
    @DisplayName("[보안] URL 접두사가 다르거나 경로 탈출을 노린 값은 무시하고 예외를 던지지 않는다")
    void delete_ignoresInvalidOrTraversalUrls() {
        ProductImageStorage storage = storage();

        assertDoesNotThrow(() -> storage.delete(null));
        assertDoesNotThrow(() -> storage.delete(""));
        assertDoesNotThrow(() -> storage.delete("/etc/passwd"));
        assertDoesNotThrow(() -> storage.delete("/uploads/../../../../etc/passwd"));
    }

    @Test
    @DisplayName("delete()는 이미 없는 파일을 가리켜도 예외를 던지지 않는다")
    void delete_doesNotThrowWhenFileAlreadyGone() {
        assertDoesNotThrow(() -> storage().delete("/uploads/2026/08/nonexistent.jpg"));
    }
}
