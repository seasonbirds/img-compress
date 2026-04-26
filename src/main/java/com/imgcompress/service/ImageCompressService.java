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
import java.awt.image.IndexColorModel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

@Slf4j
@Service
public class ImageCompressService {

    private static final String[] SUPPORTED_FORMATS = {"png", "jpg", "jpeg", "webp", "gif"};
    
    private static final float JPEG_QUALITY_START = 0.85f;
    private static final float JPEG_QUALITY_MIN = 0.5f;
    private static final float JPEG_QUALITY_STEP = 0.08f;
    
    private static final float PNG_QUALITY_START = 0.7f;
    private static final float PNG_QUALITY_MIN = 0.3f;
    private static final float PNG_QUALITY_STEP = 0.1f;
    
    private static final float WEBP_QUALITY_START = 0.8f;
    private static final float WEBP_QUALITY_MIN = 0.5f;
    private static final float WEBP_QUALITY_STEP = 0.08f;
    
    private static final double TARGET_COMPRESSION_RATIO = 0.65;
    private static final int MAX_DIMENSION = 4096;
    private static final int MEDIUM_DIMENSION = 2560;
    private static final int SMALL_DIMENSION = 1920;

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
        int width = image.getWidth();
        int height = image.getHeight();
        
        log.info("JPEG图片信息: {}x{} 像素, {} bytes", width, height, originalSize);

        double scale = calculateScale(width, height, originalSize);
        BufferedImage workingImage = image;
        
        if (scale < 1.0) {
            log.info("需要缩放图片，缩放比例: {}", scale);
            workingImage = scaleImage(image, scale);
        }

        float quality = JPEG_QUALITY_START;
        byte[] bestResult = null;
        float bestQuality = quality;

        while (quality >= JPEG_QUALITY_MIN) {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                Thumbnails.of(workingImage)
                        .scale(1.0)
                        .outputQuality(quality)
                        .outputFormat("jpg")
                        .toOutputStream(baos);
                
                byte[] currentResult = baos.toByteArray();
                double compressionRatio = (double) currentResult.length / originalSize;
                
                log.debug("JPEG压缩尝试: quality={}, 大小={} bytes, 压缩比={}", 
                        quality, currentResult.length, compressionRatio);

                if (compressionRatio <= TARGET_COMPRESSION_RATIO) {
                    log.info("达到目标压缩比，使用quality={}", quality);
                    return currentResult;
                }

                if (bestResult == null || currentResult.length < bestResult.length) {
                    bestResult = currentResult;
                    bestQuality = quality;
                }
                
                quality -= JPEG_QUALITY_STEP;
            }
        }

        if (bestResult != null && bestResult.length < originalSize) {
            log.info("使用最低质量压缩结果，quality={}", bestQuality);
            return bestResult;
        }

        log.info("压缩后大小未减小，返回原文件");
        return file.getBytes();
    }

    private byte[] compressPng(MultipartFile file) throws IOException {
        BufferedImage image = ImageIO.read(file.getInputStream());
        if (image == null) {
            throw new ImageCompressException("无法读取PNG图片");
        }

        long originalSize = file.getSize();
        int width = image.getWidth();
        int height = image.getHeight();
        
        log.info("PNG图片信息: {}x{} 像素, {} bytes", width, height, originalSize);

        boolean hasTransparency = (image.getTransparency() != Transparency.OPAQUE);
        log.info("PNG是否包含透明度: {}", hasTransparency);

        double scale = calculateScale(width, height, originalSize);
        BufferedImage workingImage = image;
        
        if (scale < 1.0) {
            log.info("需要缩放图片，缩放比例: {}", scale);
            workingImage = scaleImage(image, scale);
        }

        byte[] bestResult = null;

        if (!hasTransparency) {
            log.info("无透明度PNG，尝试转换为JPEG格式压缩");
            float jpegQuality = 0.85f;
            while (jpegQuality >= 0.5f) {
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    Thumbnails.of(workingImage)
                            .scale(1.0)
                            .outputQuality(jpegQuality)
                            .outputFormat("jpg")
                            .toOutputStream(baos);
                    
                    byte[] jpegResult = baos.toByteArray();
                    double compressionRatio = (double) jpegResult.length / originalSize;
                    
                    log.debug("PNG转JPEG尝试: quality={}, 大小={} bytes, 压缩比={}", 
                            jpegQuality, jpegResult.length, compressionRatio);

                    if (compressionRatio <= TARGET_COMPRESSION_RATIO) {
                        log.info("PNG转JPEG达到目标压缩比，使用quality={}", jpegQuality);
                        return jpegResult;
                    }

                    if (bestResult == null || jpegResult.length < bestResult.length) {
                        bestResult = jpegResult;
                    }
                    
                    jpegQuality -= 0.1f;
                }
            }
        }

        log.info("尝试PNG优化压缩策略");
        
        byte[] indexedResult = tryIndexedColor(workingImage);
        if (indexedResult != null) {
            double compressionRatio = (double) indexedResult.length / originalSize;
            log.info("索引色压缩结果: {} bytes, 压缩比: {}", indexedResult.length, compressionRatio);
            
            if (compressionRatio <= TARGET_COMPRESSION_RATIO) {
                return indexedResult;
            }
            
            if (bestResult == null || indexedResult.length < bestResult.length) {
                bestResult = indexedResult;
            }
        }

        float quality = PNG_QUALITY_START;
        while (quality >= PNG_QUALITY_MIN) {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                Thumbnails.of(workingImage)
                        .scale(1.0)
                        .outputQuality(quality)
                        .outputFormat("png")
                        .toOutputStream(baos);
                
                byte[] currentResult = baos.toByteArray();
                double compressionRatio = (double) currentResult.length / originalSize;
                
                log.debug("PNG压缩尝试: quality={}, 大小={} bytes, 压缩比={}", 
                        quality, currentResult.length, compressionRatio);

                if (compressionRatio <= TARGET_COMPRESSION_RATIO) {
                    log.info("PNG达到目标压缩比，使用quality={}", quality);
                    return currentResult;
                }

                if (bestResult == null || currentResult.length < bestResult.length) {
                    bestResult = currentResult;
                }
                
                quality -= PNG_QUALITY_STEP;
            }
        }

        if (bestResult != null && bestResult.length < originalSize) {
            log.info("使用最优压缩结果，压缩率: {}%", 
                    String.format("%.1f", (1 - (double) bestResult.length / originalSize) * 100));
            return bestResult;
        }

        log.info("所有压缩策略效果不佳，返回原文件");
        return file.getBytes();
    }

    private byte[] tryIndexedColor(BufferedImage image) {
        try {
            int width = image.getWidth();
            int height = image.getHeight();
            
            int[] pixels = new int[width * height];
            image.getRGB(0, 0, width, height, pixels, 0, width);
            
            int distinctColors = countDistinctColors(pixels);
            log.info("PNG不同颜色数量: {}", distinctColors);
            
            if (distinctColors <= 256) {
                log.info("颜色数量<=256，尝试索引色压缩");
                
                IndexColorModel icm = createIndexColorModel(pixels, distinctColors);
                if (icm != null) {
                    BufferedImage indexedImage = new BufferedImage(
                            width, height, 
                            BufferedImage.TYPE_BYTE_INDEXED, icm);
                    
                    indexedImage.setRGB(0, 0, width, height, pixels, 0, width);
                    
                    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        ImageIO.write(indexedImage, "PNG", baos);
                        return baos.toByteArray();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("索引色压缩尝试失败: {}", e.getMessage());
        }
        return null;
    }

    private int countDistinctColors(int[] pixels) {
        return (int) Arrays.stream(pixels).distinct().count();
    }

    private IndexColorModel createIndexColorModel(int[] pixels, int colorCount) {
        try {
            int[] distinctColors = Arrays.stream(pixels).distinct().toArray();
            
            if (distinctColors.length > 256) {
                return null;
            }
            
            int[] cmap = new int[256];
            System.arraycopy(distinctColors, 0, cmap, 0, distinctColors.length);
            
            for (int i = distinctColors.length; i < 256; i++) {
                cmap[i] = 0;
            }
            
            int bits = colorCount <= 2 ? 1 : 
                      colorCount <= 4 ? 2 : 
                      colorCount <= 16 ? 4 : 8;
            
            return new IndexColorModel(bits, distinctColors.length, cmap, 0, false, -1);
        } catch (Exception e) {
            log.warn("创建索引颜色模型失败: {}", e.getMessage());
            return null;
        }
    }

    private byte[] compressGif(MultipartFile file) throws IOException {
        long originalSize = file.getSize();

        BufferedImage image = ImageIO.read(file.getInputStream());
        if (image == null) {
            throw new ImageCompressException("无法读取GIF图片");
        }

        int width = image.getWidth();
        int height = image.getHeight();
        
        log.info("GIF图片信息: {}x{} 像素, {} bytes", width, height, originalSize);

        double scale = calculateScale(width, height, originalSize);
        BufferedImage workingImage = image;
        
        if (scale < 1.0) {
            log.info("需要缩放图片，缩放比例: {}", scale);
            workingImage = scaleImage(image, scale);
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            int newWidth = workingImage.getWidth();
            int newHeight = workingImage.getHeight();

            BufferedImage optimizedImage = optimizeGifImage(workingImage);

            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("gif");
            if (!writers.hasNext()) {
                throw new ImageCompressException("GIF写入器不可用");
            }

            ImageWriter writer = writers.next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.7f);
            }

            try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                writer.write(new IIOImage(optimizedImage, null, null));
            } finally {
                writer.dispose();
            }

            byte[] result = baos.toByteArray();
            
            log.info("GIF压缩结果: 原始={} bytes, 压缩后={} bytes, 尺寸={}x{}", 
                    originalSize, result.length, newWidth, newHeight);
            
            return result.length < originalSize ? result : file.getBytes();
        }
    }

    private BufferedImage optimizeGifImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        BufferedImage optimizedImage = new BufferedImage(
                width, height, BufferedImage.TYPE_BYTE_INDEXED);
        
        Graphics2D g = optimizedImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(image, 0, 0, null);
        g.dispose();
        
        return optimizedImage;
    }

    private byte[] compressWebp(MultipartFile file) throws IOException {
        BufferedImage image = ImageIO.read(file.getInputStream());
        if (image == null) {
            throw new ImageCompressException("无法读取WebP图片");
        }

        long originalSize = file.getSize();
        int width = image.getWidth();
        int height = image.getHeight();
        
        log.info("WebP图片信息: {}x{} 像素, {} bytes", width, height, originalSize);

        double scale = calculateScale(width, height, originalSize);
        BufferedImage workingImage = image;
        
        if (scale < 1.0) {
            log.info("需要缩放图片，缩放比例: {}", scale);
            workingImage = scaleImage(image, scale);
        }

        float quality = WEBP_QUALITY_START;
        byte[] bestResult = null;

        while (quality >= WEBP_QUALITY_MIN) {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                Thumbnails.of(workingImage)
                        .scale(1.0)
                        .outputQuality(quality)
                        .outputFormat("webp")
                        .toOutputStream(baos);
                
                byte[] currentResult = baos.toByteArray();
                double compressionRatio = (double) currentResult.length / originalSize;
                
                log.debug("WebP压缩尝试: quality={}, 大小={} bytes, 压缩比={}", 
                        quality, currentResult.length, compressionRatio);

                if (compressionRatio <= TARGET_COMPRESSION_RATIO) {
                    log.info("WebP达到目标压缩比，使用quality={}", quality);
                    return currentResult;
                }

                if (bestResult == null || currentResult.length < bestResult.length) {
                    bestResult = currentResult;
                }
                
                quality -= WEBP_QUALITY_STEP;
            }
        }

        if (bestResult != null && bestResult.length < originalSize) {
            return bestResult;
        }

        log.info("压缩后大小未减小，返回原文件");
        return file.getBytes();
    }

    private double calculateScale(int width, int height, long fileSize) {
        double scale = 1.0;
        
        int maxDim = Math.max(width, height);
        
        if (fileSize > 10 * 1024 * 1024) {
            if (maxDim > MAX_DIMENSION) {
                scale = (double) MAX_DIMENSION / maxDim;
            }
        } else if (fileSize > 5 * 1024 * 1024) {
            if (maxDim > MEDIUM_DIMENSION) {
                scale = (double) MEDIUM_DIMENSION / maxDim;
            }
        } else if (fileSize > 2 * 1024 * 1024) {
            if (maxDim > SMALL_DIMENSION) {
                scale = (double) SMALL_DIMENSION / maxDim;
            }
        }
        
        return Math.max(scale, 0.5);
    }

    private BufferedImage scaleImage(BufferedImage image, double scale) {
        int newWidth = (int) (image.getWidth() * scale);
        int newHeight = (int) (image.getHeight() * scale);
        
        int imageType = image.getTransparency() != Transparency.OPAQUE 
                ? BufferedImage.TYPE_INT_ARGB 
                : BufferedImage.TYPE_INT_RGB;
        
        BufferedImage scaledImage = new BufferedImage(newWidth, newHeight, imageType);
        Graphics2D g = scaledImage.createGraphics();
        
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        g.drawImage(image, 0, 0, newWidth, newHeight, null);
        g.dispose();
        
        log.info("图片已缩放: {}x{} -> {}x{}", 
                image.getWidth(), image.getHeight(), newWidth, newHeight);
        
        return scaledImage;
    }
}
