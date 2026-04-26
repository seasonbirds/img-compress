package com.imgcompress.service;

import com.imgcompress.exception.ImageCompressException;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

@Slf4j
@Service
public class ImageCompressService {

    private static final String[] SUPPORTED_FORMATS = {"png", "jpg", "jpeg", "webp", "gif"};
    private static final float JPEG_QUALITY_START = 0.9f;
    private static final float JPEG_QUALITY_MIN = 0.6f;
    private static final float PNG_COMPRESSION_START = 0.8f;
    private static final float PNG_COMPRESSION_MIN = 0.5f;
    private static final float WEBP_QUALITY_START = 0.9f;
    private static final float WEBP_QUALITY_MIN = 0.6f;
    private static final long TARGET_SIZE_THRESHOLD = 1024 * 1024;

    public byte[] compressImage(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new ImageCompressException("文件名不能为空");
        }

        String extension = getFileExtension(originalFilename).toLowerCase();
        validateFormat(extension);

        log.info("开始压缩图片: {}, 原始大小: {} bytes", originalFilename, file.getSize());

        byte[] result = switch (extension) {
            case "jpg", "jpeg" -> compressJpeg(file);
            case "png" -> compressPng(file);
            case "gif" -> compressGif(file);
            case "webp" -> compressWebp(file);
            default -> throw new ImageCompressException("不支持的图片格式: " + extension);
        };

        log.info("图片压缩完成: 原始大小 {} bytes, 压缩后大小 {} bytes, 压缩率: {}%",
                file.getSize(), result.length, 
                String.format("%.1f", (1 - (double) result.length / file.getSize()) * 100));

        return result;
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1 || lastDot == filename.length() - 1) {
            throw new ImageCompressException("文件名缺少扩展名: " + filename);
        }
        return filename.substring(lastDot + 1);
    }

    private void validateFormat(String extension) {
        for (String format : SUPPORTED_FORMATS) {
            if (format.equals(extension)) {
                return;
            }
        }
        throw new ImageCompressException("不支持的图片格式，仅支持: " + String.join(", ", SUPPORTED_FORMATS));
    }

    private byte[] compressJpeg(MultipartFile file) throws IOException {
        BufferedImage image = ImageIO.read(file.getInputStream());
        if (image == null) {
            throw new ImageCompressException("无法读取JPEG图片");
        }

        long originalSize = file.getSize();
        if (originalSize <= TARGET_SIZE_THRESHOLD) {
            log.info("图片较小，无需压缩: {} bytes", originalSize);
            return file.getBytes();
        }

        float quality = JPEG_QUALITY_START;
        byte[] result = null;

        while (quality >= JPEG_QUALITY_MIN) {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                Thumbnails.of(image)
                        .scale(1.0)
                        .outputQuality(quality)
                        .outputFormat("jpg")
                        .toOutputStream(baos);
                
                result = baos.toByteArray();
                
                if (result.length <= originalSize * 0.7 || result.length <= TARGET_SIZE_THRESHOLD * 2) {
                    break;
                }
                
                quality -= 0.1f;
            }
        }

        return result != null && result.length < originalSize ? result : file.getBytes();
    }

    private byte[] compressPng(MultipartFile file) throws IOException {
        BufferedImage image = ImageIO.read(file.getInputStream());
        if (image == null) {
            throw new ImageCompressException("无法读取PNG图片");
        }

        long originalSize = file.getSize();
        if (originalSize <= TARGET_SIZE_THRESHOLD) {
            log.info("图片较小，无需压缩: {} bytes", originalSize);
            return file.getBytes();
        }

        float quality = PNG_COMPRESSION_START;
        byte[] result = null;

        while (quality >= PNG_COMPRESSION_MIN) {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                Thumbnails.of(image)
                        .scale(1.0)
                        .outputQuality(quality)
                        .outputFormat("png")
                        .toOutputStream(baos);
                
                result = baos.toByteArray();
                
                if (result.length <= originalSize * 0.7 || result.length <= TARGET_SIZE_THRESHOLD * 2) {
                    break;
                }
                
                quality -= 0.1f;
            }
        }

        return result != null && result.length < originalSize ? result : file.getBytes();
    }

    private byte[] compressGif(MultipartFile file) throws IOException {
        long originalSize = file.getSize();
        if (originalSize <= TARGET_SIZE_THRESHOLD) {
            log.info("图片较小，无需压缩: {} bytes", originalSize);
            return file.getBytes();
        }

        BufferedImage image = ImageIO.read(file.getInputStream());
        if (image == null) {
            throw new ImageCompressException("无法读取GIF图片");
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            int width = image.getWidth();
            int height = image.getHeight();
            
            double scale = 1.0;
            if (width > 1920 || height > 1920) {
                scale = Math.min(1920.0 / width, 1920.0 / height);
            }
            
            int newWidth = (int) (width * scale);
            int newHeight = (int) (height * scale);

            BufferedImage scaledImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaledImage.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(image, 0, 0, newWidth, newHeight, null);
            g.dispose();

            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("gif");
            if (!writers.hasNext()) {
                throw new ImageCompressException("GIF写入器不可用");
            }

            ImageWriter writer = writers.next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.8f);
            }

            try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                writer.write(new IIOImage(scaledImage, null, null));
            } finally {
                writer.dispose();
            }

            byte[] result = baos.toByteArray();
            return result.length < originalSize ? result : file.getBytes();
        }
    }

    private byte[] compressWebp(MultipartFile file) throws IOException {
        BufferedImage image = ImageIO.read(file.getInputStream());
        if (image == null) {
            throw new ImageCompressException("无法读取WebP图片");
        }

        long originalSize = file.getSize();
        if (originalSize <= TARGET_SIZE_THRESHOLD) {
            log.info("图片较小，无需压缩: {} bytes", originalSize);
            return file.getBytes();
        }

        float quality = WEBP_QUALITY_START;
        byte[] result = null;

        while (quality >= WEBP_QUALITY_MIN) {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                Thumbnails.of(image)
                        .scale(1.0)
                        .outputQuality(quality)
                        .outputFormat("webp")
                        .toOutputStream(baos);
                
                result = baos.toByteArray();
                
                if (result.length <= originalSize * 0.7 || result.length <= TARGET_SIZE_THRESHOLD * 2) {
                    break;
                }
                
                quality -= 0.1f;
            }
        }

        return result != null && result.length < originalSize ? result : file.getBytes();
    }
}
