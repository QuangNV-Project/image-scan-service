package com.subservice.imagehandle.service;

import com.subservice.imagehandle.dto.BillTransactionDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service parse structured data từ OCR text
 */
@Slf4j
@Service
public class BillParser {

    // Patterns
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile(
        "(?i)(?:số|s[oố]|stk|t[àa]i\\s*kho[aả]n|account)\\s*[:\\-]?\\s*([0-9]{10,16})",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern BANK_PATTERN = Pattern.compile(
        "(?i)(vietcombank|vcb|techcombank|mbbank|mb|acb|vietinbank|bidv|agribank|tpbank|vpbank|sacombank|ocb|msb|scb|seabank|vib|shb|hdbank|lienvietpostbank|tmcp)",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern RECIPIENT_PATTERN = Pattern.compile(
        "(?i)(?:tên|ten|người|nguoi)\\s*(?:nhận|nhan|nh[aậ]n)\\s*[:\\-]?\\s*([A-Z\\s]{4,50})",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern TRANSACTION_CODE_PATTERN = Pattern.compile(
        "(?i)(?:mã|ma)\\s*(?:giao|gd)\\s*(?:dịch|d[iị]ch)\\s*[:\\-]?\\s*([A-Z0-9]{8,20})",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern CONTENT_PATTERN = Pattern.compile(
        "(?i)(?:nội|noi)\\s*dung\\s*[:\\-]?\\s*(.{5,100})",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern STATUS_PATTERN = Pattern.compile(
        "(?i)(?:giao|gd)\\s*(?:dịch|d[iị]ch)\\s*(?:thành|thanh)\\s*(?:công|cong)",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Parse OCR text thành structured data
     */
    public BillTransactionDTO parse(String ocrText, String imagePath, double confidence) {
        log.debug("Parsing bill transaction from OCR text");
        
        String normalizedText = normalizeText(ocrText);
        
        // Extract các thông tin
        BigDecimal amount = extractAmount(normalizedText);
        String accountNumber = extractAccountNumber(normalizedText);
        String recipientName = extractRecipientName(normalizedText);
        String bankName = extractBankName(normalizedText);
        String transactionCode = extractTransactionCode(normalizedText);
        String transferContent = extractContent(normalizedText);
        String status = extractStatus(normalizedText);
        
        // Smart swap: Nếu accountNumber null nhưng transactionCode giống STK
        if ((accountNumber == null || accountNumber.isEmpty()) && 
            transactionCode != null && 
            isLikelyAccountNumber(transactionCode)) {
            
            log.info("Swapping: transactionCode looks like account number");
            accountNumber = transactionCode;
            transactionCode = null;
            log.info("After swap: accountNumber={}, transactionCode={}", accountNumber, transactionCode);
        }
        
        BillTransactionDTO result = BillTransactionDTO.builder()
            .amount(amount)
            .accountNumber(accountNumber)
            .recipientName(recipientName)
            .bankName(bankName)
            .transactionCode(transactionCode)
            .transferContent(transferContent)
            .status(status)
            .build();
        
        log.info("Parsed bill: amount={}, recipient={}, account={}, bank={}, code={}", 
            amount, recipientName, accountNumber, bankName, transactionCode);
        
        return result;
    }

    private String normalizeText(String text) {
        return text
            .replaceAll("\\s+", " ")
            .replaceAll("\\n+", "\n")
            .trim();
    }

    /**
     * Extract số tiền - 5 patterns
     */
    private BigDecimal extractAmount(String text) {
        log.debug("Extracting amount from text");
        
        List<Long> candidates = new ArrayList<>();
        
        // Pattern 0: Standalone number ở đầu
        String[] lines = text.split("\\n");
        for (int i = 0; i < Math.min(5, lines.length); i++) {
            String line = lines[i].trim();
            Pattern standalone = Pattern.compile("^\\s*([0-9]{1,3}[.,\\s]?[0-9]{3}(?:[.,\\s][0-9]{3})*)\\s*(?:VND|đ|dong|d)?\\s*$", Pattern.CASE_INSENSITIVE);
            Matcher m = standalone.matcher(line);
            if (m.find()) {
                Long amount = parseAmount(m.group(1));
                if (amount != null && amount >= 1000 && amount <= 1_000_000_000) {
                    candidates.add(amount);
                }
            }
        }
        
        // Pattern 1: "Giao dịch thành công" + số
        Pattern pattern1 = Pattern.compile(
            "(?i)giao\\s*[dđ][iịĩỉ]ch\\s*th[àaả]nh\\s*c[ôo]ng[^0-9]{0,50}([0-9]{1,3}(?:[.,\\s][0-9]{3})*)",
            Pattern.DOTALL
        );
        Matcher matcher = pattern1.matcher(text);
        while (matcher.find()) {
            Long amount = parseAmount(matcher.group(1));
            if (amount != null && amount >= 1000) {
                candidates.add(amount);
            }
        }
        
        // Pattern 2: Số + VND
        Pattern pattern2 = Pattern.compile(
            "([0-9]{1,3}(?:[.,\\s][0-9]{3})+)\\s*(?:VND|đ|vnd|dong|đồng)",
            Pattern.CASE_INSENSITIVE
        );
        matcher = pattern2.matcher(text);
        while (matcher.find()) {
            Long amount = parseAmount(matcher.group(1));
            if (amount != null && amount >= 1000) {
                candidates.add(amount);
            }
        }
        
        // Pattern 3: Formatted number
        Pattern pattern3 = Pattern.compile("\\b([0-9]{1,3}[.,][0-9]{3}(?:[.,][0-9]{3})*)\\b");
        matcher = pattern3.matcher(text);
        while (matcher.find()) {
            Long amount = parseAmount(matcher.group(1));
            if (amount != null && amount >= 1000) {
                candidates.add(amount);
            }
        }
        
        // Pattern 4: Raw number % 1000
        Pattern pattern4 = Pattern.compile("\\b([0-9]{4,10})\\b");
        matcher = pattern4.matcher(text);
        while (matcher.find()) {
            Long amount = parseAmount(matcher.group(1));
            if (amount != null && amount >= 1000 && amount % 1000 == 0) {
                // Check not account number
                if (matcher.group(1).length() < 10 || amount % 1000 == 0) {
                    candidates.add(amount);
                }
            }
        }
        
        if (candidates.isEmpty()) {
            log.warn("No amount found in text");
            return null;
        }
        
        // Lấy số lớn nhất
        BigDecimal result = BigDecimal.valueOf(candidates.stream().max(Long::compare).orElse(null));
        log.info("Extracted amount: {}", result);
        return result;
    }

    private Long parseAmount(String amountStr) {
        try {
            String cleaned = amountStr.replaceAll("[.,\\s]", "");
            return Long.parseLong(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Extract account number
     */
    private String extractAccountNumber(String text) {
        Matcher matcher = ACCOUNT_PATTERN.matcher(text);
        
        List<String> candidates = new ArrayList<>();
        while (matcher.find()) {
            String account = matcher.group(1);
            if (account.length() >= 10 && account.length() <= 16) {
                candidates.add(account);
            }
        }
        
        // Fallback: tìm số 10-16 chữ số
        if (candidates.isEmpty()) {
            Pattern fallback = Pattern.compile("\\b([0-9]{10,16})\\b");
            matcher = fallback.matcher(text);
            while (matcher.find()) {
                candidates.add(matcher.group(1));
            }
        }
        
        // Ưu tiên số 10-13 chữ số
        return candidates.stream()
            .filter(s -> s.length() >= 10 && s.length() <= 13)
            .findFirst()
            .orElse(candidates.isEmpty() ? null : candidates.get(0));
    }

    /**
     * Extract recipient name với Vietnamese splitting
     */
    private String extractRecipientName(String text) {
        List<String> candidates = new ArrayList<>();
        
        // Pattern 1: "Tên người nhận" + tên
        Matcher matcher = RECIPIENT_PATTERN.matcher(text);
        if (matcher.find()) {
            String name = matcher.group(1).trim().replaceAll("\\s+", " ");
            candidates.add(name);
        }
        
        // Pattern 2: IN HOA có space
        Pattern pattern2 = Pattern.compile("\\b([A-Z]{2,}(?:\\s+[A-Z]{2,}){1,5})\\b");
        matcher = pattern2.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group(1).trim().replaceAll("\\s+", " ");
            String[] words = candidate.split("\\s+");
            
            if (words.length >= 2 && words.length <= 5) {
                boolean valid = true;
                for (String word : words) {
                    if (word.length() < 2) {
                        valid = false;
                        break;
                    }
                }
                if (valid && !isCommonWord(candidate)) {
                    candidates.add(candidate);
                }
            }
        }
        
        // Pattern 3: DÍNH LIỀN (LEVANNAM)
        Pattern pattern3 = Pattern.compile("\\b([A-Z]{6,20})\\b");
        matcher = pattern3.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            
            if (isCommonWord(candidate) || isBankName(candidate)) {
                continue;
            }
            
            String formatted = splitVietnameseName(candidate);
            if (formatted != null) {
                candidates.add(formatted);
            }
        }
        
        if (candidates.isEmpty()) {
            return null;
        }
        
        // Lấy tên có nhiều từ nhất
        return candidates.stream()
            .max((a, b) -> {
                int wordCountA = a.split("\\s+").length;
                int wordCountB = b.split("\\s+").length;
                if (wordCountA != wordCountB) {
                    return Integer.compare(wordCountA, wordCountB);
                }
                return Integer.compare(a.length(), b.length());
            })
            .orElse(null);
    }

    private boolean isCommonWord(String word) {
        String upper = word.toUpperCase();
        return upper.contains("NGUOI") || upper.contains("NHAN") ||
               upper.contains("TAI") || upper.contains("KHOAN") ||
               upper.contains("NGAN") || upper.contains("HANG") ||
               upper.contains("CHUYEN") || upper.contains("TIEN") ||
               upper.contains("GIAO") || upper.contains("DICH") ||
               upper.equals("VND") || upper.equals("DONG");
    }

    private boolean isBankName(String word) {
        String upper = word.toUpperCase();
        return upper.contains("VIETCOMBANK") || upper.contains("TECHCOMBANK") ||
               upper.contains("BIDV") || upper.contains("AGRIBANK") ||
               upper.contains("VIETINBANK") || upper.contains("TMCP");
    }

    private String splitVietnameseName(String concatenated) {
        if (concatenated == null || concatenated.length() < 6) {
            return null;
        }
        
        String[] surnames = {"LE", "LA", "LY", "LU", "LO", "LAM", "LAI",
                            "NGUYEN", "TRAN", "PHAM", "HOANG", "VU", "VO", "DANG",
                            "BUI", "DO", "HO", "NGO", "DUONG", "DINH"};
        
        for (String surname : surnames) {
            if (concatenated.startsWith(surname)) {
                String remaining = concatenated.substring(surname.length());
                
                if (remaining.length() >= 4 && remaining.length() <= 12) {
                    int mid = remaining.length() / 2;
                    
                    for (int offset = 0; offset <= 2; offset++) {
                        int breakPoint = mid + offset;
                        if (breakPoint > 2 && breakPoint < remaining.length() - 2) {
                            String part1 = remaining.substring(0, breakPoint);
                            String part2 = remaining.substring(breakPoint);
                            
                            if (part1.length() >= 2 && part2.length() >= 2) {
                                return surname + " " + part1 + " " + part2;
                            }
                        }
                    }
                }
            }
        }
        
        return null;
    }

    private String extractBankName(String text) {
        Matcher matcher = BANK_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String extractTransactionCode(String text) {
        Matcher matcher = TRANSACTION_CODE_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        // Fallback: số dài 10-20
        Pattern fallback = Pattern.compile("\\b([0-9]{10,20})\\b");
        matcher = fallback.matcher(text);
        
        List<String> candidates = new ArrayList<>();
        while (matcher.find()) {
            String code = matcher.group(1);
            if (code.length() >= 10 && code.length() <= 20) {
                candidates.add(code);
            }
        }
        
        return candidates.stream()
            .max((a, b) -> Integer.compare(a.length(), b.length()))
            .orElse(null);
    }

    private String extractContent(String text) {
        Matcher matcher = CONTENT_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        Pattern fallback = Pattern.compile(
            "([A-Z]{3,}(?:\\s+[A-Z]{3,}){1,5}\\s+(?:chuyen|tien|chuyen\\s*tien))",
            Pattern.CASE_INSENSITIVE
        );
        matcher = fallback.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        return null;
    }

    private String extractStatus(String text) {
        Matcher matcher = STATUS_PATTERN.matcher(text);
        if (matcher.find()) {
            return "Thành công";
        }
        return "Unknown";
    }

    private boolean isLikelyAccountNumber(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        
        String cleaned = value.replaceAll("[\\s.\\-]", "");
        
        if (!cleaned.matches("^[0-9]+$")) {
            return false;
        }
        
        int length = cleaned.length();
        if (length < 10 || length > 16) {
            return false;
        }
        
        try {
            long number = Long.parseLong(cleaned);
            
            if (number % 1000 != 0) {
                log.debug("Value {} looks like account number (length={}, not divisible by 1000)", value, length);
                return true;
            }
            
        } catch (NumberFormatException e) {
            return false;
        }
        
        return false;
    }
}
