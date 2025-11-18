package com.subtracker.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 카드/은행 거래 내역을 나타내는 도메인 모델
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {
    
    private String transactionId;      // 거래 고유 ID
    private LocalDate transactionDate; // 거래 날짜
    private String merchant;            // 가맹점명
    private BigDecimal amount;          // 거래 금액
    private String category;            // 카테고리 (옵션)
    private String description;         // 거래 설명
    private String cardNumber;          // 카드 번호 (마스킹된 형태)
    
    /**
     * 거래가 특정 기간 내에 있는지 확인
     */
    public boolean isWithinPeriod(LocalDate startDate, LocalDate endDate) {
        return !transactionDate.isBefore(startDate) && !transactionDate.isAfter(endDate);
    }
    
    /**
     * 동일한 가맹점인지 확인 (대소문자 무시, 공백 제거)
     */
    public boolean isSameMerchant(String otherMerchant) {
        if (merchant == null || otherMerchant == null) {
            return false;
        }
        return merchant.replaceAll("\\s+", "").equalsIgnoreCase(
               otherMerchant.replaceAll("\\s+", ""));
    }
    
    /**
     * 금액이 유사한지 확인 (오차 범위 5% 이내)
     */
    public boolean isSimilarAmount(BigDecimal otherAmount, double tolerancePercent) {
        if (amount == null || otherAmount == null) {
            return false;
        }
        
        BigDecimal tolerance = amount.multiply(BigDecimal.valueOf(tolerancePercent / 100));
        BigDecimal difference = amount.subtract(otherAmount).abs();
        
        return difference.compareTo(tolerance) <= 0;
    }
}