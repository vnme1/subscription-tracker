package com.subtracker.infrastructure.export;

import com.opencsv.CSVWriter;
import com.subtracker.domain.model.AnalysisHistory;
import com.subtracker.domain.model.CategoryStats;
import com.subtracker.domain.model.Subscription;
import com.subtracker.domain.service.CategoryAnalyzer;
import lombok.extern.slf4j.Slf4j;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class EnhancedCsvExporter {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final CategoryAnalyzer categoryAnalyzer;

    public EnhancedCsvExporter() {
        this.categoryAnalyzer = new CategoryAnalyzer();
    }

    /**
     * 상세 분석 보고서 CSV 내보내기
     */
    public void exportDetailedReport(String outputPath, AnalysisHistory history)
            throws ExportException {

        try (CSVWriter writer = new CSVWriter(new FileWriter(outputPath))) {

            // 1. 헤더 정보
            writeHeader(writer, history);

            // 2. 요약 정보
            writeSummary(writer, history);

            // 3. 카테고리별 분석
            writeCategoryAnalysis(writer, history);

            // 4. 구독 상세 정보
            writeSubscriptionDetails(writer, history);

            // 5. 취소 추천 목록
            writeCancellationRecommendations(writer, history);

            log.info("CSV 보고서 생성 완료: {}", outputPath);

        } catch (IOException e) {
            log.error("CSV 내보내기 실패", e);
            throw new ExportException("CSV 파일 생성 중 오류 발생", e);
        }
    }

    /**
     * 헤더 작성
     */
    private void writeHeader(CSVWriter writer, AnalysisHistory history) {
        writer.writeNext(new String[] {
                "=== 구독 서비스 분석 보고서 ==="
        });
        writer.writeNext(new String[] {
                "생성일시:", LocalDate.now().toString()
        });
        writer.writeNext(new String[] {
                "분석 파일:", history.getFileName()
        });
        writer.writeNext(new String[] {}); // 빈 줄
    }

    /**
     * 요약 정보 작성
     */
    private void writeSummary(CSVWriter writer, AnalysisHistory history) {
        writer.writeNext(new String[] { "[요약 정보]" });
        writer.writeNext(new String[] {
                "총 구독 수:", String.valueOf(history.getSubscriptionCount())
        });
        writer.writeNext(new String[] {
                "월 총액:", formatMoney(history.getMonthlyTotal())
        });
        writer.writeNext(new String[] {
                "연간 예상:", formatMoney(history.getAnnualProjection())
        });
        writer.writeNext(new String[] {});
    }

    /**
     * 카테고리별 분석 작성
     */
    private void writeCategoryAnalysis(CSVWriter writer, AnalysisHistory history) {
        Map<CategoryStats.SubscriptionCategory, CategoryStats> categoryStats = categoryAnalyzer
                .analyzeCategoryDistribution(history.getSubscriptions());

        writer.writeNext(new String[] { "[카테고리별 분석]" });
        writer.writeNext(new String[] {
                "카테고리", "구독 수", "월 금액", "비율(%)"
        });

        for (CategoryStats stats : categoryStats.values()) {
            writer.writeNext(new String[] {
                    stats.getDisplayName(),
                    String.valueOf(stats.getCount()),
                    formatMoney(stats.getTotalAmount()),
                    String.format("%.1f%%", stats.getPercentage())
            });
        }
        writer.writeNext(new String[] {});
    }

    /**
     * 구독 상세 정보 작성
     */
    private void writeSubscriptionDetails(CSVWriter writer, AnalysisHistory history) {
        writer.writeNext(new String[] { "[구독 상세 목록]" });
        writer.writeNext(new String[] {
                "서비스명", "월금액", "연간예상", "결제주기", "상태",
                "첫결제일", "최근결제일", "다음결제예정일",
                "총결제횟수", "총지출액", "카테고리", "취소추천"
        });

        for (Subscription sub : history.getSubscriptions()) {
            writer.writeNext(new String[] {
                    sub.getServiceName(),
                    formatMoney(sub.getMonthlyAmount()),
                    formatMoney(sub.calculateAnnualCost()),
                    sub.getBillingCycle().getKorean(),
                    sub.getStatus().getKorean(),
                    formatDate(sub.getFirstDetectedDate()),
                    formatDate(sub.getLastChargeDate()),
                    formatDate(sub.getNextChargeDate()),
                    String.valueOf(sub.getTransactionCount()),
                    formatMoney(sub.getTotalSpent()),
                    detectCategoryName(sub.getServiceName()),
                    sub.isCancellationCandidate(60) ? "Y" : "N"
            });
        }
        writer.writeNext(new String[] {});
    }

    /**
     * 취소 추천 목록 작성
     */
    private void writeCancellationRecommendations(CSVWriter writer,
            AnalysisHistory history) {
        List<Subscription> candidates = history.getSubscriptions().stream()
                .filter(s -> s.isCancellationCandidate(60))
                .toList();

        if (!candidates.isEmpty()) {
            writer.writeNext(new String[] { "[취소 검토 대상]" });
            writer.writeNext(new String[] {
                    "서비스명", "월금액", "마지막 결제일", "미사용 기간(일)"
            });

            for (Subscription sub : candidates) {
                long daysSinceLastCharge = 0;
                if (sub.getLastChargeDate() != null) {
                    daysSinceLastCharge = LocalDate.now().toEpochDay() -
                            sub.getLastChargeDate().toEpochDay();
                }

                writer.writeNext(new String[] {
                        sub.getServiceName(),
                        formatMoney(sub.getMonthlyAmount()),
                        formatDate(sub.getLastChargeDate()),
                        String.valueOf(daysSinceLastCharge)
                });
            }
        }
    }

    /**
     * 카테고리명 감지
     */
    private String detectCategoryName(String serviceName) {
        // CategoryAnalyzer의 로직 재사용
        CategoryAnalyzer analyzer = new CategoryAnalyzer();
        Map<CategoryStats.SubscriptionCategory, CategoryStats> stats = analyzer.analyzeCategoryDistribution(List.of(
                Subscription.builder()
                        .serviceName(serviceName)
                        .status(Subscription.SubscriptionStatus.ACTIVE)
                        .monthlyAmount(BigDecimal.ZERO)
                        .build()));

        return stats.isEmpty() ? "기타" : stats.keySet().iterator().next().getKorean();
    }

    /**
     * 금액 포맷팅
     */
    private String formatMoney(BigDecimal amount) {
        if (amount == null)
            return "0";
        return String.format("%,.0f", amount);
    }

    /**
     * 날짜 포맷팅
     */
    private String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FORMAT) : "";
    }

    /**
     * Export 예외 클래스
     */
    public static class ExportException extends Exception {
        public ExportException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}