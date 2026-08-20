package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드된 상품 이미지를 디스크에 저장한다 (product/image).
 *
 * <p><b>업로드는 신뢰할 수 없는 입력이다.</b> 파일 업로드는 전형적인 공격 표면이라 다음을 지킨다.
 * <ul>
 *   <li><b>클라이언트가 보낸 파일명을 쓰지 않는다.</b> 저장 이름은 서버가 UUID로 만든다 —
 *       {@code ../../etc/passwd} 같은 경로 탈출과, 확장자를 이용한 실행 파일 업로드를 원천 차단한다.</li>
 *   <li><b>확장자·Content-Type 선언을 믿지 않는다.</b> {@code ImageIO}로 실제 디코딩이 되는지로만
 *       판정한다 — 확장자만 {@code .jpg}로 바꾼 파일은 여기서 걸린다.</li>
 *   <li><b>크기 상한을 서버가 강제한다.</b> 프론트 검증은 우회 가능하므로 여기서 다시 확인한다.</li>
 * </ul>
 *
 * <p><b>원본을 그대로 저장하지 않고 축소·재인코딩한다.</b> 저장 공간(Railway 볼륨)이 유한해서, 휴대폰
 * 원본 사진(3~5MB)을 그대로 쌓으면 금방 찬다. 업로드는 5MB까지 받아들이되 긴 변을 {@value #MAX_DIMENSION}px로
 * 줄이고 JPEG로 다시 인코딩해서 저장한다. 재인코딩은 부수적으로 EXIF 등 원본 메타데이터도 떨궈낸다
 * (사진에 위치정보가 남아 공개되는 걸 막는다).
 */
@Slf4j
@Component
public class ProductImageStorage {

    /** 저장 시 긴 변 상한(px). 상세 페이지 표시에 충분하면서 용량을 크게 줄인다. */
    private static final int MAX_DIMENSION = 1600;
    private static final float JPEG_QUALITY = 0.82f;
    private static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;
    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM");

    /** 웹에서 이 이미지를 가리키는 경로 접두사. 저장 디렉터리와 1:1 대응한다. */
    public static final String URL_PREFIX = "/uploads/";

    private final Path baseDir;

    public ProductImageStorage(@Value("${app.upload.dir}") String uploadDir) {
        this.baseDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    /**
     * 업로드 파일을 저장하고 웹에서 접근할 경로를 돌려준다.
     *
     * @return {@code /uploads/2026/08/{uuid}.jpg} 형태의 경로
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_FILE);
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new BusinessException(ErrorCode.IMAGE_FILE_TOO_LARGE);
        }

        BufferedImage source = readImageOrThrow(file);
        BufferedImage resized = resizeIfNeeded(source);

        try {
            Path dir = baseDir.resolve(LocalDate.now().format(DATE_DIR));
            Files.createDirectories(dir);

            String fileName = UUID.randomUUID() + ".jpg";
            Path target = dir.resolve(fileName);
            writeJpeg(resized, target);

            String url = URL_PREFIX + baseDir.relativize(target).toString().replace('\\', '/');
            log.info("상품 이미지 저장 완료: url={}, 원본={}bytes, 저장={}bytes",
                    url, file.getSize(), Files.size(target));
            return url;
        } catch (IOException e) {
            log.error("상품 이미지 저장 실패: originalSize={}", file.getSize(), e);
            throw new BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }
    }

    // 확장자나 Content-Type이 아니라 "실제로 이미지로 읽히는가"로 판정한다.
    // ImageIO가 못 읽으면 null을 반환하므로, 그것이 곧 "이미지가 아님"의 판정 근거다.
    private BufferedImage readImageOrThrow(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                // 아이폰 HEIC 등 ImageIO가 지원하지 않는 형식도 여기로 온다 — 조용히 실패하지 않고
                // 명확히 거절해서 사용자가 다른 형식으로 다시 시도할 수 있게 한다.
                throw new BusinessException(ErrorCode.INVALID_IMAGE_FILE);
            }
            return image;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_FILE);
        }
    }

    private BufferedImage resizeIfNeeded(BufferedImage source) {
        int longSide = Math.max(source.getWidth(), source.getHeight());
        if (longSide <= MAX_DIMENSION) {
            return toRgb(source);
        }
        double ratio = (double) MAX_DIMENSION / longSide;
        int width = Math.max(1, (int) Math.round(source.getWidth() * ratio));
        int height = Math.max(1, (int) Math.round(source.getHeight() * ratio));

        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();
        return resized;
    }

    // JPEG는 알파 채널을 지원하지 않는다 — PNG 등 투명 배경 이미지를 그대로 쓰면 색이 깨지므로
    // 흰 배경 위에 합성해서 RGB로 변환한다.
    private BufferedImage toRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, source.getWidth(), source.getHeight());
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return rgb;
    }

    private void writeJpeg(BufferedImage image, Path target) throws IOException {
        javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY);
        try (javax.imageio.stream.ImageOutputStream out = ImageIO.createImageOutputStream(target.toFile())) {
            writer.setOutput(out);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}
