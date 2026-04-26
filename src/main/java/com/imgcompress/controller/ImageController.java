package com.imgcompress.controller;

import com.imgcompress.service.ImageCompressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ImageController {

    private final ImageCompressService imageCompressService;

    @GetMapping("/")
    public String index(Model model) {
        return "index";
    }

    @PostMapping("/compress")
    public ResponseEntity<byte[]> compressImage(@RequestParam("imageFile") MultipartFile file) throws IOException {
        log.info("收到上传文件: {}, 大小: {} bytes", file.getOriginalFilename(), file.getSize());

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("请选择要上传的图片文件".getBytes(StandardCharsets.UTF_8));
        }

        byte[] compressedImage = imageCompressService.compressImage(file);
        String originalFilename = file.getOriginalFilename();

        log.info("压缩完成，准备下载: {}, 压缩后大小: {} bytes", originalFilename, compressedImage.length);

        String contentType = getContentType(originalFilename);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentLength(compressedImage.length);
        headers.setContentDispositionFormData("attachment", encodeFileName(originalFilename));
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

        return ResponseEntity.ok()
                .headers(headers)
                .body(compressedImage);
    }

    private String getContentType(String filename) {
        String extension = getFileExtension(filename).toLowerCase();
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1 || lastDot == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot + 1);
    }

    private String encodeFileName(String fileName) {
        try {
            return URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                    .replace("+", "%20");
        } catch (Exception e) {
            return fileName;
        }
    }
}
