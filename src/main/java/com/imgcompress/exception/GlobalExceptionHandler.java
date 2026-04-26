package com.imgcompress.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ImageCompressException.class)
    public String handleImageCompressException(ImageCompressException ex, RedirectAttributes redirectAttributes) {
        log.error("图片压缩异常: {}", ex.getMessage(), ex);
        redirectAttributes.addFlashAttribute("error", ex.getMessage());
        return "redirect:/";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex, RedirectAttributes redirectAttributes) {
        log.error("文件上传大小超出限制: {}", ex.getMessage(), ex);
        redirectAttributes.addFlashAttribute("error", "上传的文件超出最大限制150M");
        return "redirect:/";
    }

    @ExceptionHandler(Exception.class)
    public String handleException(Exception ex, RedirectAttributes redirectAttributes) {
        log.error("系统异常: {}", ex.getMessage(), ex);
        redirectAttributes.addFlashAttribute("error", "系统处理失败，请稍后重试");
        return "redirect:/";
    }
}
