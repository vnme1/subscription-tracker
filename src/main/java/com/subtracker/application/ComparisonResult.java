package com.subtracker.application;

import com.subtracker.domain.model.AnalysisHistory;
import com.subtracker.domain.model.Subscription;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 두 분석 결과 비교
 * StackOverflow 방지
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = { "newSubscriptions", "removedSubscriptions", "changedSubscriptions" })
@EqualsAndHashCode(exclude = { "newSubscriptions", "removedSubscriptions", "changedSubscriptions" })
public class ComparisonResult {

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    @EqualsAndHashCode
    public static class SubscriptionDiff {
        private String serviceName;
        private String changeType;
        private BigDecimal oldAmount;
        private BigDecimal newAmount;
        private String oldStatus;
        private String newStatus;
    }

    private LocalDateTime oldAnalysisDate;
    private LocalDateTime newAnalysisDate;

    private int oldSubscriptionCount;
    private int newSubscriptionCount;
    private int subscriptionCountDiff;

    private BigDecimal oldMonthlyTotal;
    private BigDecimal newMonthlyTotal;
    private BigDecimal monthlyTotalDiff;
    private double monthlyTotalChangePercent;

    private BigDecimal oldAnnualProjection;
    private BigDecimal newAnnualProjection;
    private BigDecimal annualProjectionDiff;

    @Builder.Default
    private List<SubscriptionDiff> newSubscriptions = new ArrayList<>();

    @Builder.Default
    private List<SubscriptionDiff> removedSubscriptions = new ArrayList<>();

    @Builder.Default
    private List<SubscriptionDiff> changedSubscriptions = new ArrayList<>();

    /**
     * 두 분석 이력 비교
     */
    public static ComparisonResult compare(AnalysisHistory older, AnalysisHistory newer) {
        ComparisonResult result = new ComparisonResult();

        result.oldAnalysisDate = older.getAnalysisDate();
        result.newAnalysisDate = newer.getAnalysisDate();

        result.oldSubscriptionCount = older.getSubscriptionCount();
        result.newSubscriptionCount = newer.getSubscriptionCount();
        result.subscriptionCountDiff = newer.getSubscriptionCount() - older.getSubscriptionCount();

        result.oldMonthlyTotal = older.getMonthlyTotal();
        result.newMonthlyTotal = newer.getMonthlyTotal();
        result.monthlyTotalDiff = newer.getMonthlyTotal().subtract(older.getMonthlyTotal());

        if (older.getMonthlyTotal().compareTo(BigDecimal.ZERO) > 0) {
            result.monthlyTotalChangePercent = result.monthlyTotalDiff
                    .divide(older.getMonthlyTotal(), 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }

        result.oldAnnualProjection = older.getAnnualProjection();
        result.newAnnualProjection = newer.getAnnualProjection();
        result.annualProjectionDiff = newer.getAnnualProjection().subtract(older.getAnnualProjection());

        compareSubscriptions(older, newer, result);

        return result;
    }

    /**
     * 구독 목록 비교
     */
    private static void compareSubscriptions(AnalysisHistory older, AnalysisHistory newer,
            ComparisonResult result) {

        // ✅ 명시적 타입 선언
        Map<String, Subscription> oldSubs = older.getSubscriptions().stream()
                .collect(Collectors.toMap(
                        Subscription::getServiceName,
                        sub -> sub));

        Map<String, Subscription> newSubs = newer.getSubscriptions().stream()
                .collect(Collectors.toMap(
                        Subscription::getServiceName,
                        sub -> sub));

        // 신규 구독
        for (Subscription newSub : newer.getSubscriptions()) {
            if (!oldSubs.containsKey(newSub.getServiceName())) {
                result.newSubscriptions.add(SubscriptionDiff.builder()
                        .serviceName(newSub.getServiceName())
                        .changeType("NEW")
                        .newAmount(newSub.getMonthlyAmount())
                        .newStatus(newSub.getStatus().getKorean())
                        .build());
            }
        }

        // 제거된 구독
        for (Subscription oldSub : older.getSubscriptions()) {
            if (!newSubs.containsKey(oldSub.getServiceName())) {
                result.removedSubscriptions.add(SubscriptionDiff.builder()
                        .serviceName(oldSub.getServiceName())
                        .changeType("REMOVED")
                        .oldAmount(oldSub.getMonthlyAmount())
                        .oldStatus(oldSub.getStatus().getKorean())
                        .build());
            }
        }

        // 변경된 구독
        for (Subscription newSub : newer.getSubscriptions()) {
            Subscription oldSub = oldSubs.get(newSub.getServiceName());
            if (oldSub != null) {
                boolean changed = false;
                SubscriptionDiff.SubscriptionDiffBuilder diff = SubscriptionDiff.builder()
                        .serviceName(newSub.getServiceName());

                if (oldSub.getMonthlyAmount().compareTo(newSub.getMonthlyAmount()) != 0) {
                    diff.oldAmount(oldSub.getMonthlyAmount())
                            .newAmount(newSub.getMonthlyAmount());
                    changed = true;
                }

                if (oldSub.getStatus() != newSub.getStatus()) {
                    diff.oldStatus(oldSub.getStatus().getKorean())
                            .newStatus(newSub.getStatus().getKorean());
                    changed = true;
                }

                if (changed) {
                    diff.changeType("CHANGED");
                    result.changedSubscriptions.add(diff.build());
                }
            }
        }
    }

    /**
     * 비교 결과 요약 생성
     */
    public String generateSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("\n").append("=".repeat(60)).append("\n");
        summary.append("                 분석 비교 결과\n");
        summary.append("=".repeat(60)).append("\n\n");

        summary.append(String.format("📅 비교 기간: %s → %s\n\n",
                oldAnalysisDate.toLocalDate(), newAnalysisDate.toLocalDate()));

        summary.append("📊 전체 통계:\n");
        summary.append(String.format("  구독 개수: %d → %d (%+d)\n",
                oldSubscriptionCount, newSubscriptionCount, subscriptionCountDiff));
        summary.append(String.format("  월 지출: ₩%,.0f → ₩%,.0f (%+,.0f, %.1f%%)\n",
                oldMonthlyTotal, newMonthlyTotal, monthlyTotalDiff, monthlyTotalChangePercent));
        summary.append(String.format("  연간 예상: ₩%,.0f → ₩%,.0f (%+,.0f)\n\n",
                oldAnnualProjection, newAnnualProjection, annualProjectionDiff));

        if (!newSubscriptions.isEmpty()) {
            summary.append("✨ 신규 구독 (").append(newSubscriptions.size()).append("개):\n");
            for (SubscriptionDiff sub : newSubscriptions) {
                // ✅ getter 메서드 사용
                summary.append(String.format("  + %s: ₩%,.0f/월\n",
                        sub.getServiceName(), sub.getNewAmount()));
            }
            summary.append("\n");
        }

        if (!removedSubscriptions.isEmpty()) {
            summary.append("❌ 제거된 구독 (").append(removedSubscriptions.size()).append("개):\n");
            for (SubscriptionDiff sub : removedSubscriptions) {
                // ✅ getter 메서드 사용
                summary.append(String.format("  - %s: ₩%,.0f/월\n",
                        sub.getServiceName(), sub.getOldAmount()));
            }
            summary.append("\n");
        }

        if (!changedSubscriptions.isEmpty()) {
            summary.append("🔄 변경된 구독 (").append(changedSubscriptions.size()).append("개):\n");
            for (SubscriptionDiff sub : changedSubscriptions) {
                // ✅ getter 메서드 사용
                if (sub.getOldAmount() != null && sub.getNewAmount() != null) {
                    summary.append(String.format("  • %s: ₩%,.0f → ₩%,.0f\n",
                            sub.getServiceName(), sub.getOldAmount(), sub.getNewAmount()));
                }
                if (sub.getOldStatus() != null && sub.getNewStatus() != null) {
                    summary.append(String.format("    상태: %s → %s\n",
                            sub.getOldStatus(), sub.getNewStatus()));
                }
            }
        }

        summary.append("=".repeat(60)).append("\n");
        return summary.toString();
    }
}