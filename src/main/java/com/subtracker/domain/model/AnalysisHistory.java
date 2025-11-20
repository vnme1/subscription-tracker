package com.subtracker.domain.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 분석 이력을 나타내는 도메인 모델
 * StackOverflow 방지: @ToString.Exclude, @EqualsAndHashCode.Exclude 적용
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "subscriptions") // ✅ subscriptions 제외
@EqualsAndHashCode(exclude = "subscriptions") // ✅ subscriptions 제외
public class AnalysisHistory {

    private String id;
    private LocalDateTime analysisDate;
    private String fileName;
    private int transactionCount;
    private int subscriptionCount;
    private BigDecimal monthlyTotal;
    private BigDecimal annualProjection;
    private LocalDateTime createdAt;

    @Builder.Default
    private List<Subscription> subscriptions = new ArrayList<>();

    /**
     * SubscriptionSummary로부터 AnalysisHistory 생성
     */
    public static AnalysisHistory fromSummary(SubscriptionSummary summary, String fileName, int transactionCount) {
        return AnalysisHistory.builder()
                .id(java.util.UUID.randomUUID().toString())
                .analysisDate(LocalDateTime.now())
                .fileName(fileName)
                .transactionCount(transactionCount)
                .subscriptionCount(summary.getTotalSubscriptions())
                .monthlyTotal(summary.getMonthlyTotal())
                .annualProjection(summary.getAnnualProjection())
                .subscriptions(summary.getSubscriptions())
                .createdAt(LocalDateTime.now())
                .build();
    }
}