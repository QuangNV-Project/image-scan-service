package com.subservice.imagehandle.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO chứa thông tin bill đã parse (structured data)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillTransactionDTO {
    
    /**
     * Số tiền giao dịch (VNĐ)
     */
    private Long amount;
    
    /**
     * Người nhận
     */
    private String recipientName;
    
    /**
     * Số tài khoản người nhận
     */
    private String accountNumber;
    
    /**
     * Ngân hàng nhận
     */
    private String bankName;
    
    /**
     * Nội dung chuyển khoản
     */
    private String transferContent;
    
    /**
     * Mã giao dịch
     */
    private String transactionCode;
    
    /**
     * Phí chuyển tiền
     */
    private String transferFee;
    
    /**
     * Trạng thái giao dịch
     */
    private String status;
    
    /**
     * Toàn bộ raw text từ OCR
     */
    private String rawText;
    
    /**
     * Confidence score (0.0 - 1.0)
     */
    private Double confidence;
    
    /**
     * Đường dẫn ảnh đã upload (optional - null nếu không lưu)
     */
    private String imagePath;
}
