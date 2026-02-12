package com.subservice.imagehandle.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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
    private BigDecimal amount;
    
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
     * Trạng thái giao dịch
     */
    private String status;
}
