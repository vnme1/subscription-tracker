package com.subtracker.domain.service;

import com.subtracker.domain.model.BudgetAlert;
import com.subtracker.domain.model.BudgetAlert.AlertType;
import com.subtracker.domain.model.Subscription;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Slf4j
public class BudgetService {

    private static final BigDecimal DEFAULT_WARNING_THRESHOLD = BigDecimal.valueOf(80);
    private static final BigDecimal DEFAULT_CRITICAL_THRESHOLD = BigDecimal.valueOf(90);

    /**
     * 예산 알림 생성
     */
    public BudgetAlert createBudgetAlert(BigDecimal monthlyBudget,
            List<Subscription> activeSubscriptions) {

        // 현재 월간 지출 계산
        BigDecimal currentSpending = calculateMonthlySpending(activeSubscriptions);

        BudgetAlert alert = BudgetAlert.builder()
                .id(UUID.randomUUID().toString())
                .monthlyBudget(monthlyBudget)
                .currentSpending(currentSpending)
                .warningThreshold(DEFAULT_WARNING_THRESHOLD)
                .criticalThreshold(DEFAULT_CRITICAL_THRESHOLD)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // 알림 상태 설정
        alert.setAlertType(alert.checkBudgetStatus(currentSpending));

        log.info("예산 알림 생성: 예산 {}원, 현재 지출 {}원, 상태: {}",
                monthlyBudget, currentSpending, alert.getAlertType().getKorean());

        return alert;
    }

    /**
     * 월간 구독 지출 계산
     */
    private BigDecimal calculateMonthlySpending(List<Subscription> subscriptions) {
        if (subscriptions == null || subscriptions.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return subscriptions.stream()
                .filter(Subscription::isActive)
                .map(Subscription::getMonthlyAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 예산 초과 예측 (다음 결제 포함)
     */
    public BudgetPrediction predictBudgetStatus(BudgetAlert currentAlert,
            List<Subscription> upcomingPayments) {

        BigDecimal upcomingAmount = upcomingPayments.stream()
                .map(Subscription::getMonthlyAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal projectedSpending = currentAlert.getCurrentSpending()
                .add(upcomingAmount);

        AlertType projectedStatus = currentAlert.checkBudgetStatus(projectedSpending);

        return BudgetPrediction.builder()
                .currentSpending(currentAlert.getCurrentSpending())
                .projectedSpending(projectedSpending)
                .additionalSpending(upcomingAmount)
                .currentStatus(currentAlert.getAlertType())
                .projectedStatus(projectedStatus)
                .willExceedBudget(projectedStatus == AlertType.EXCEEDED)
                .build();
    }

    /**
     * 예산 절약 제안
     */
    public BudgetRecommendation generateRecommendation(BudgetAlert alert,
            List<Subscription> subscriptions) {

        BudgetRecommendation.BudgetRecommendationBuilder builder = BudgetRecommendation.builder();

        BigDecimal deficit = alert.getCurrentSpending()
                .subtract(alert.getMonthlyBudget());

        if (deficit.compareTo(BigDecimal.ZERO) > 0) {
            // 예산 초과 상태 - 취소 추천
            builder.recommendationType("REDUCE")
                    .message(String.format("예산을 %,d원 초과했습니다. 구독 정리가 필요합니다.",
                            deficit.intValue()));

            // 가장 비싼 구독 찾기
            subscriptions.stream()
                    .filter(Subscription::isActive)
                    .max((s1, s2) -> s1.getMonthlyAmount().compareTo(s2.getMonthlyAmount()))
                    .ifPresent(expensive -> {
                        builder.targetService(expensive.getServiceName())
                                .potentialSaving(expensive.getMonthlyAmount());
                    });

        } else {
            BigDecimal remaining = alert.getRemainingBudget();
            builder.recommendationType("MAINTAIN")
                    .message(String.format("예산 내에서 잘 관리하고 있습니다. 여유: %,d원",
                            remaining.intValue()));
        }

        return builder.build();
    }

    // 내부 클래스들
    @Data
    @Builder
    public static class BudgetPrediction {
        private BigDecimal currentSpending;
        private BigDecimal projectedSpending;
        private BigDecimal additionalSpending;
        private AlertType currentStatus;
        private AlertType projectedStatus;
        private boolean willExceedBudget;
    }

    @Data
    @Builder
    public static class BudgetRecommendation {
        private String recommendationType;
        private String message;
        private String targetService;
        private BigDecimal potentialSaving;
    }
}