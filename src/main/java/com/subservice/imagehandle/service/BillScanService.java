package com.subservice.imagehandle.service;

import com.subservice.imagehandle.dto.BillTransactionDTO;
import com.subservice.imagehandle.service.TesseractOcrService.OcrResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Service chính để xử lý scan bill
 * Workflow: MultipartFile → Temp File → OCR → Parse → JSON Response
 * 
 * Note: Không lưu file upload - chỉ dùng temp file để OCR
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillScanService {

    private final TesseractOcrService tesseractOcrService;
    private final BillParser billParser;

    /**
     * Validate uploaded file
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("Filename is null");
        }
        
        String lowercaseName = filename.toLowerCase();
        if (!lowercaseName.endsWith(".jpg") && 
            !lowercaseName.endsWith(".jpeg") && 
            !lowercaseName.endsWith(".png")) {
            throw new IllegalArgumentException("Only JPG, JPEG, PNG files are allowed");
        }
        
        // Check file size (max 10MB)
        long maxSize = 10 * 1024 * 1024; // 10MB
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("File size exceeds 10MB limit");
        }
    }
    
    /**
     * Xử lý scan bill và trả về structured data
     * 
     * Workflow:
     * 1. Validate file
     * 2. Tạo temp file từ MultipartFile
     * 3. OCR từ temp file
     * 4. Parse structured data
     * 5. Xóa temp file
     * 6. Trả về JSON
     * 
     * Note: KHÔNG lưu file upload - chỉ dùng temp file cho OCR
     * 
     * @param file ảnh bill từ client (JPG/PNG, max 10MB)
     * @return BillTransactionDTO chứa thông tin structured
     */
    public BillTransactionDTO scanBillStructured(MultipartFile file) throws IOException {
        log.info("Starting bill scan for file: {}, size: {} bytes", 
            file.getOriginalFilename(), file.getSize());
        
        // 1. Validate file
        validateFile(file);
        
        File tempFile = null;
        try {
            // 2. Tạo temp file từ MultipartFile
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".") 
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".jpg";
            
            tempFile = Files.createTempFile("bill-scan-", extension).toFile();
            file.transferTo(tempFile);
            
            log.info("Created temp file for OCR: {}", tempFile.getAbsolutePath());
            
            // 3. Thực hiện OCR trực tiếp từ temp file
            // KHÔNG preprocessing - dùng ảnh gốc cho quality tốt hơn
            OcrResult ocrResult = tesseractOcrService.extractTextWithConfidence(tempFile);
            String rawText = ocrResult.getText();
            double confidence = ocrResult.getConfidence();
            
            log.info("OCR completed: {} characters, confidence: {}", 
                rawText != null ? rawText.length() : 0, confidence);
            
            if (rawText == null || rawText.trim().isEmpty()) {
                log.warn("OCR returned empty text");
                return BillTransactionDTO.builder()
                    .rawText("")
                    .confidence(0.0)
                    .imagePath(null)
                    .build();
            }
            
            // 4. Parse structured data từ OCR text
            BillTransactionDTO result = billParser.parse(rawText, null, confidence);
            
            log.info("Bill scan completed: amount={}, recipient={}, account={}, bank={}", 
                result.getAmount(), result.getRecipientName(), 
                result.getAccountNumber(), result.getBankName());
            
            return result;
            
        } finally {
            // 5. Xóa temp file
            if (tempFile != null && tempFile.exists()) {
                boolean deleted = tempFile.delete();
                log.debug("Temp file deleted: {}", deleted);
            }
        }
    }
}
