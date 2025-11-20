package com.subtracker.domain.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 감지된 구독 서비스를 나타내는 도메인 모델
 * StackOverflow 방지: transactions 필드 제외
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "transactions") // ✅ transactions 제외
@EqualsAndHashCode(exclude = "transactions") // ✅ transactions 제외
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

        public String getKorean() {
            return korean;
        }

        public int getDays() {
            return days;
        }
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

        public String getKorean() {
            return korean;
        }
    }

    private String subscriptionId;
    private String serviceName;
    private BigDecimal monthlyAmount;
    private BigDecimal lastAmount;
    private BillingCycle billingCycle;
    private LocalDate firstDetectedDate;
    private LocalDate lastChargeDate;
    private LocalDate nextChargeDate;
    private SubscriptionStatus status;
    private int transactionCount;
    private BigDecimal totalSpent;

    @Builder.Default
    private List<Transaction> transactions = new ArrayList<>();

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

        this.transactionCount = transactions.size();

        this.totalSpent = transactions.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        transactions.stream()
                .map(Transaction::getTransactionDate)
                .max(LocalDate::compareTo)
                .ifPresent(date -> this.lastChargeDate = date);
    }
}