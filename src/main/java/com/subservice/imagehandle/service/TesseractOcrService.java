package com.subservice.imagehandle.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;

/**
 * Service xử lý OCR sử dụng Tesseract
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TesseractOcrService {

    @Value("${ocr.tesseract.datapath:./tessdata}")
    private String tessdataPath;

    @Value("${ocr.tesseract.language:vie+eng}")
    private String language;

    @Value("${ocr.tesseract.psm:6}")
    private int psm;

    private ITesseract tesseract;

    @PostConstruct
    public void init() {
        tesseract = new Tesseract();
        tesseract.setDatapath(tessdataPath);
        tesseract.setLanguage(language);
        tesseract.setPageSegMode(psm);
        
        log.info("Tesseract OCR initialized: datapath={}, language={}, psm={}", 
            tessdataPath, language, psm);
    }

    /**
     * Extract text với confidence score
     */
    public OcrResult extractTextWithConfidence(File imageFile) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Perform OCR
            String text = tesseract.doOCR(imageFile);
            
            // Estimate confidence (simple heuristic)
            double confidence = estimateConfidence(text);
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("OCR completed in {}ms. Extracted {} characters", 
                duration, text != null ? text.length() : 0);
            
            return new OcrResult(text, confidence);
            
        } catch (TesseractException e) {
            log.error("OCR failed: {}", e.getMessage(), e);
            return new OcrResult("", 0.0);
        }
    }

    /**
     * Estimate confidence dựa trên text quality
     */
    private double estimateConfidence(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0.0;
        }
        
        int totalChars = text.length();
        int validChars = 0;
        
        for (char c : text.toCharArray()) {
            if (Character.isLetterOrDigit(c) || Character.isWhitespace(c) || 
                ".,!?;:-()[]{}\"'".indexOf(c) >= 0) {
                validChars++;
            }
        }
        
        return totalChars > 0 ? (double) validChars / totalChars : 0.0;
    }

    /**
     * OCR Result container
     */
    public static class OcrResult {
        private final String text;
        private final double confidence;
        
        public OcrResult(String text, double confidence) {
            this.text = text;
            this.confidence = confidence;
        }
        
        public String getText() {
            return text;
        }
        
        public double getConfidence() {
            return confidence;
        }
    }
}
