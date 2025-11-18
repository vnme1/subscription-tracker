package com.subtracker.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 감지된 구독 서비스를 나타내는 도메인 모델
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {
    
    public enum BillingCycle {
        MONTHLY("월간", 30),
        QUARTERLY("분기", 90),
        SEMI_ANNUAL("반기", 180),
        ANNUAL("연간", 365),
        UNKNOWN("미확인", 0);
        
        private final String korean;
        private final int days;
        
        BillingCycle(String korean, int days) {
            this.korean = korean;
            this.days = days;
        }
        
        public String getKorean() { return korean; }
        public int getDays() { return days; }
    }
    
    public enum SubscriptionStatus {
        ACTIVE("활성"),
        INACTIVE("비활성"),
        PENDING("대기중"),
        CANCELLED("취소됨");
        
        private final String korean;
        
        SubscriptionStatus(String korean) {
            this.korean = korean;
        }
        
        public String getKorean() { return korean; }
    }
    
    private String subscriptionId;           // 구독 ID
    private String serviceName;              // 서비스명 (가맹점명)
    private BigDecimal monthlyAmount;        // 월 평균 금액
    private BigDecimal lastAmount;           // 최근 결제 금액
    private BillingCycle billingCycle;       // 결제 주기
    private LocalDate firstDetectedDate;     // 최초 감지일
    private LocalDate lastChargeDate;        // 최근 결제일
    private LocalDate nextChargeDate;        // 다음 결제 예정일
    private SubscriptionStatus status;        // 구독 상태
    private int transactionCount;             // 총 거래 횟수
    private BigDecimal totalSpent;           // 총 지출액
    
    @Builder.Default
    private List<Transaction> transactions = new ArrayList<>();  // 관련 거래 내역
    
    /**
     * 구독이 활성 상태인지 확인
     */
    public boolean isActive() {
        return status == SubscriptionStatus.ACTIVE;
    }
    
    /**
     * 구독 취소 후보인지 확인 (최근 X일 이상 결제 없음)
     */
    public boolean isCancellationCandidate(int daysThreshold) {
        if (lastChargeDate == null) {
            return true;
        }
        return LocalDate.now().minusDays(daysThreshold).isAfter(lastChargeDate);
    }
    
    /**
     * 연간 예상 비용 계산
     */
    public BigDecimal calculateAnnualCost() {
        if (monthlyAmount == null) {
            return BigDecimal.ZERO;
        }
        
        return switch (billingCycle) {
            case MONTHLY -> monthlyAmount.multiply(BigDecimal.valueOf(12));
            case QUARTERLY -> monthlyAmount.multiply(BigDecimal.valueOf(4));
            case SEMI_ANNUAL -> monthlyAmount.multiply(BigDecimal.valueOf(2));
            case ANNUAL -> monthlyAmount;
            default -> BigDecimal.ZERO;
        };
    }
    
    /**
     * 다음 결제일 계산
     */
    public void calculateNextChargeDate() {
        if (lastChargeDate == null || billingCycle == BillingCycle.UNKNOWN) {
            this.nextChargeDate = null;
            return;
        }
        
        this.nextChargeDate = lastChargeDate.plusDays(billingCycle.getDays());
    }
    
    /**
     * 거래 내역 추가
     */
    public void addTransaction(Transaction transaction) {
        if (transactions == null) {
            transactions = new ArrayList<>();
        }
        transactions.add(transaction);
        updateStatistics();
    }
    
    /**
     * 통계 정보 업데이트
     */
    private void updateStatistics() {
        if (transactions == null || transactions.isEmpty()) {
            return;
        }
        
        // 거래 횟수 업데이트
        this.transactionCount = transactions.size();
        
        // 총 지출액 계산
        this.totalSpent = transactions.stream()
            .map(Transaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // 최근 거래 정보 업데이트
        transactions.stream()
            .map(Transaction::getTransactionDate)
            .max(LocalDate::compareTo)
            .ifPresent(date -> this.lastChargeDate = date);
    }
}