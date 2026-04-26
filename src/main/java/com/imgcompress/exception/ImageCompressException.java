package com.imgcompress.exception;

public class ImageCompressException extends RuntimeException {

    public ImageCompressException(String message) {
        super(message);
    }

    public ImageCompressException(String message, Throwable cause) {
        super(message, cause);
    }
}
