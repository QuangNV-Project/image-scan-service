package com.subservice.imagehandle;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Bill Scanner Service - Main Application
 * 
 * Simple OCR API: Upload image → Parse bill → Return JSON
 * 
 * Note: Không dùng OpenCV/Image Preprocessing - OCR trực tiếp từ ảnh gốc
 */
@Slf4j
@SpringBootApplication
public class ImageHandleServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImageHandleServiceApplication.class, args);
        log.info("Bill Scanner Service started successfully!");
    }
}
