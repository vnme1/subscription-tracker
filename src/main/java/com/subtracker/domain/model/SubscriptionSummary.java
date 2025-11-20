package com.subtracker.domain.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 구독 현황 요약 정보
 * StackOverflow 방지
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = { "subscriptions", "cancellationCandidates", "upcomingPayments" })
@EqualsAndHashCode(exclude = { "subscriptions", "cancellationCandidates", "upcomingPayments" })
public class SubscriptionSummary {

    private LocalDate analysisDate;
    private int totalSubscriptions;
    private int activeSubscriptions;
    private BigDecimal monthlyTotal;
    private BigDecimal annualProjection;

    @Builder.Default
    private List<Subscription> subscriptions = new ArrayList<>();

    @Builder.Default
    private List<Subscription> cancellationCandidates = new ArrayList<>();

    @Builder.Default
    private List<Subscription> upcomingPayments = new ArrayList<>();

    /**
     * 요약 정보 생성
     */
    public static SubscriptionSummary from(List<Subscription> subscriptions) {
        SubscriptionSummary summary = new SubscriptionSummary();
        summary.analysisDate = LocalDate.now();
        summary.subscriptions = subscriptions;

        List<Subscription> activeList = subscriptions.stream()
                .filter(Subscription::isActive)
                .collect(Collectors.toList());

        summary.totalSubscriptions = subscriptions.size();
        summary.activeSubscriptions = activeList.size();

        summary.monthlyTotal = activeList.stream()
                .map(Subscription::getMonthlyAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        summary.annualProjection = activeList.stream()
                .map(Subscription::calculateAnnualCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        summary.cancellationCandidates = subscriptions.stream()
                .filter(sub -> sub.isCancellationCandidate(60))
                .collect(Collectors.toList());

        LocalDate weekLater = LocalDate.now().plusDays(7);
        summary.upcomingPayments = activeList.stream()
                .filter(sub -> sub.getNextChargeDate() != null)
                .filter(sub -> !sub.getNextChargeDate().isAfter(weekLater))
                .collect(Collectors.toList());

        return summary;
    }

    /**
     * 결제 주기별 그룹화
     */
    public Map<Subscription.BillingCycle, List<Subscription>> groupByBillingCycle() {
        return subscriptions.stream()
                .filter(Subscription::isActive)
                .collect(Collectors.groupingBy(Subscription::getBillingCycle));
    }

    /**
     * 금액 상위 N개 구독 추출
     */
    public List<Subscription> getTopExpensiveSubscriptions(int limit) {
        return subscriptions.stream()
                .filter(Subscription::isActive)
                .sorted((s1, s2) -> s2.getMonthlyAmount().compareTo(s1.getMonthlyAmount()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 보고서 형식으로 출력
     */
    public String generateReport() {
        StringBuilder report = new StringBuilder();
        report.append("\n========================================\n");
        report.append("           구독 서비스 분석 보고서\n");
        report.append("========================================\n");
        report.append(String.format("분석 날짜: %s\n", analysisDate));
        report.append(String.format("총 구독 개수: %d개\n", totalSubscriptions));
        report.append(String.format("활성 구독: %d개\n", activeSubscriptions));
        report.append(String.format("월 총 지출: ₩%,.0f\n", monthlyTotal));
        report.append(String.format("연간 예상 지출: ₩%,.0f\n", annualProjection));

        if (!cancellationCandidates.isEmpty()) {
            report.append("\n📌 취소 검토 대상 (60일 이상 미결제):\n");
            for (Subscription sub : cancellationCandidates) {
                report.append(String.format("  - %s (마지막 결제: %s)\n",
                        sub.getServiceName(), sub.getLastChargeDate()));
            }
        }

        if (!upcomingPayments.isEmpty()) {
            report.append("\n💳 7일 내 결제 예정:\n");
            for (Subscription sub : upcomingPayments) {
                report.append(String.format("  - %s: ₩%,.0f (%s 예정)\n",
                        sub.getServiceName(), sub.getMonthlyAmount(), sub.getNextChargeDate()));
            }
        }

        report.append("\n💰 지출 TOP 5:\n");
        List<Subscription> topExpensive = getTopExpensiveSubscriptions(5);
        for (int i = 0; i < topExpensive.size(); i++) {
            Subscription sub = topExpensive.get(i);
            report.append(String.format("  %d. %s: ₩%,.0f/월\n",
                    i + 1, sub.getServiceName(), sub.getMonthlyAmount()));
        }

        report.append("========================================\n");
        return report.toString();
    }
}