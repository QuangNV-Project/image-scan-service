package com.subservice.imagehandle.controller;

import com.subservice.imagehandle.dto.BillTransactionDTO;
import com.subservice.imagehandle.service.BillScanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * REST Controller cho Bill Scanning
 * 
 * Chức năng duy nhất: Nhận ảnh bill → Trả về thông tin structured (JSON)
 */
@Slf4j
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final BillScanService billScanService;
    
    /**
     * API chính: Scan bill và parse thông tin structured
     * POST /api/scan-bill
     * 
     * Trả về thông tin chi tiết:
     * - Số tiền (amount)
     * - Người nhận (recipientName)
     * - Số tài khoản (accountNumber)
     * - Ngân hàng (bankName)
     * - Mã giao dịch (transactionCode)
     * - Nội dung chuyển khoản (transferContent)
     * - Trạng thái (status)
     * 
     * @param file ảnh bill chuyển khoản (JPG/PNG)
     * @return BillTransactionDTO chứa thông tin structured
     */
    @PostMapping(value = "/scan-bill", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BillTransactionDTO> scanBill(
        @RequestParam("file") MultipartFile file
    ) {
        try {
            log.info("Received scan-bill request: filename={}, size={} bytes", 
                file.getOriginalFilename(), file.getSize());
            
            // Validate file
            if (file.isEmpty()) {
                throw new BadRequestException("File is empty");
            }
            
            String contentType = file.getContentType();
            if (contentType == null || 
                (!contentType.equals("image/jpeg") && 
                 !contentType.equals("image/png") &&
                 !contentType.equals("image/jpg"))) {
                throw new BadRequestException("Only JPG/PNG images are supported");
            }
            
            // Scan bill
            BillTransactionDTO result = billScanService.scanBillStructured(file);
            
            log.info("Scan completed: amount={}, recipient={}, account={}", 
                result.getAmount(), result.getRecipientName(), result.getAccountNumber());
            
            return ResponseEntity.ok(result);
            
        } catch (IllegalArgumentException e) {
            log.error("Invalid file: {}", e.getMessage());
            throw new BadRequestException(e.getMessage());
            
        } catch (IOException e) {
            log.error("Error processing file: {}", e.getMessage(), e);
            throw new InternalServerException("Failed to process file: " + e.getMessage());
        }
    }
    
    // Custom exceptions
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) {
            super(message);
        }
    }
    
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public static class InternalServerException extends RuntimeException {
        public InternalServerException(String message) {
            super(message);
        }
    }
}
