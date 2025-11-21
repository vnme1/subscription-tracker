package com.subtracker.domain.model;

import lombok.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetAlert {

    public enum AlertType {
        SAFE("안전", "🟢"),
        WARNING("주의", "🟡"),
        CRITICAL("위험", "🟠"),
        EXCEEDED("초과", "🔴");

        private final String korean;
        private final String emoji;

        AlertType(String korean, String emoji) {
            this.korean = korean;
            this.emoji = emoji;
        }

        public String getKorean() {
            return korean;
        }

        public String getEmoji() {
            return emoji;
        }
    }

    private String id;
    private BigDecimal monthlyBudget;
    private BigDecimal currentSpending;
    private BigDecimal warningThreshold; // 예: 80%
    private BigDecimal criticalThreshold; // 예: 90%
    private AlertType alertType;
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 현재 지출 기준 예산 상태 확인
     */
    public AlertType checkBudgetStatus(BigDecimal spending) {
        if (monthlyBudget == null || monthlyBudget.compareTo(BigDecimal.ZERO) <= 0) {
            return AlertType.SAFE;
        }

        BigDecimal percentage = spending
                .multiply(BigDecimal.valueOf(100))
                .divide(monthlyBudget, 2, RoundingMode.HALF_UP);

        if (percentage.compareTo(BigDecimal.valueOf(100)) >= 0) {
            return AlertType.EXCEEDED;
        } else if (percentage.compareTo(criticalThreshold) >= 0) {
            return AlertType.CRITICAL;
        } else if (percentage.compareTo(warningThreshold) >= 0) {
            return AlertType.WARNING;
        }

        return AlertType.SAFE;
    }

    /**
     * 남은 예산 계산
     */
    public BigDecimal getRemainingBudget() {
        if (monthlyBudget == null || currentSpending == null) {
            return BigDecimal.ZERO;
        }
        return monthlyBudget.subtract(currentSpending);
    }

    /**
     * 사용률 계산 (%)
     */
    public double getUsagePercentage() {
        if (monthlyBudget == null || monthlyBudget.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }

        return currentSpending
                .multiply(BigDecimal.valueOf(100))
                .divide(monthlyBudget, 2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}