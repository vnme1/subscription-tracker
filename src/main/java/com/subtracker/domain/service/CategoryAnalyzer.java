package com.subtracker.domain.service;

import com.subtracker.domain.model.CategoryStats;
import com.subtracker.domain.model.CategoryStats.SubscriptionCategory;
import com.subtracker.domain.model.Subscription;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class CategoryAnalyzer {

    // 서비스명과 카테고리 매핑
    private static final Map<String, SubscriptionCategory> CATEGORY_MAP = new HashMap<>();

    static {
        // 엔터테인먼트
        CATEGORY_MAP.put("넷플릭스", SubscriptionCategory.ENTERTAINMENT);
        CATEGORY_MAP.put("왓챠", SubscriptionCategory.ENTERTAINMENT);
        CATEGORY_MAP.put("디즈니", SubscriptionCategory.ENTERTAINMENT);
        CATEGORY_MAP.put("웨이브", SubscriptionCategory.ENTERTAINMENT);
        CATEGORY_MAP.put("티빙", SubscriptionCategory.ENTERTAINMENT);

        // 음악
        CATEGORY_MAP.put("스포티파이", SubscriptionCategory.MUSIC);
        CATEGORY_MAP.put("멜론", SubscriptionCategory.MUSIC);
        CATEGORY_MAP.put("애플뮤직", SubscriptionCategory.MUSIC);
        CATEGORY_MAP.put("유튜브뮤직", SubscriptionCategory.MUSIC);

        // 동영상
        CATEGORY_MAP.put("유튜브", SubscriptionCategory.VIDEO);

        // 쇼핑
        CATEGORY_MAP.put("쿠팡", SubscriptionCategory.SHOPPING);
        CATEGORY_MAP.put("네이버", SubscriptionCategory.SHOPPING);
        CATEGORY_MAP.put("아마존", SubscriptionCategory.SHOPPING);

        // 소프트웨어
        CATEGORY_MAP.put("어도비", SubscriptionCategory.SOFTWARE);
        CATEGORY_MAP.put("마이크로소프트", SubscriptionCategory.SOFTWARE);
        CATEGORY_MAP.put("노션", SubscriptionCategory.SOFTWARE);
        CATEGORY_MAP.put("슬랙", SubscriptionCategory.SOFTWARE);

        // 클라우드
        CATEGORY_MAP.put("드롭박스", SubscriptionCategory.STORAGE);
        CATEGORY_MAP.put("구글드라이브", SubscriptionCategory.STORAGE);
        CATEGORY_MAP.put("아이클라우드", SubscriptionCategory.STORAGE);

        // 운동/건강
        CATEGORY_MAP.put("애플피트니스", SubscriptionCategory.FITNESS);
        CATEGORY_MAP.put("나이키", SubscriptionCategory.FITNESS);
    }

    /**
     * 구독 리스트를 카테고리별로 분석
     */
    public Map<SubscriptionCategory, CategoryStats> analyzeCategoryDistribution(
            List<Subscription> subscriptions) {

        if (subscriptions == null || subscriptions.isEmpty()) {
            return new HashMap<>();
        }

        // 전체 금액 계산
        BigDecimal totalAmount = subscriptions.stream()
                .filter(Subscription::isActive)
                .map(Subscription::getMonthlyAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 카테고리별 그룹화
        Map<SubscriptionCategory, List<Subscription>> grouped = subscriptions.stream()
                .filter(Subscription::isActive)
                .collect(Collectors.groupingBy(
                        sub -> detectCategory(sub.getServiceName())));

        // 카테고리별 통계 생성
        Map<SubscriptionCategory, CategoryStats> stats = new HashMap<>();

        for (Map.Entry<SubscriptionCategory, List<Subscription>> entry : grouped.entrySet()) {
            SubscriptionCategory category = entry.getKey();
            List<Subscription> subs = entry.getValue();

            BigDecimal categoryTotal = subs.stream()
                    .map(Subscription::getMonthlyAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            double percentage = 0;
            if (totalAmount.compareTo(BigDecimal.ZERO) > 0) {
                percentage = categoryTotal.divide(totalAmount, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .doubleValue();
            }

            CategoryStats stat = CategoryStats.builder()
                    .category(category)
                    .count(subs.size())
                    .totalAmount(categoryTotal)
                    .percentage(percentage)
                    .displayName(category.getEmoji() + " " + category.getKorean())
                    .build();

            stats.put(category, stat);
        }

        log.info("카테고리 분석 완료: {}개 카테고리", stats.size());
        return stats;
    }

    /**
     * 서비스명으로 카테고리 감지
     */
    private SubscriptionCategory detectCategory(String serviceName) {
        if (serviceName == null) {
            return SubscriptionCategory.OTHER;
        }

        String lowerName = serviceName.toLowerCase();

        // 정확한 매칭 먼저 시도
        for (Map.Entry<String, SubscriptionCategory> entry : CATEGORY_MAP.entrySet()) {
            if (lowerName.contains(entry.getKey().toLowerCase())) {
                return entry.getValue();
            }
        }

        // 키워드 기반 매칭
        if (lowerName.contains("tv") || lowerName.contains("영화")) {
            return SubscriptionCategory.VIDEO;
        }
        if (lowerName.contains("music") || lowerName.contains("음악")) {
            return SubscriptionCategory.MUSIC;
        }
        if (lowerName.contains("cloud") || lowerName.contains("드라이브")) {
            return SubscriptionCategory.STORAGE;
        }

        return SubscriptionCategory.OTHER;
    }

    /**
     * 가장 많은 지출 카테고리 찾기
     */
    public Optional<CategoryStats> getTopSpendingCategory(
            Map<SubscriptionCategory, CategoryStats> stats) {

        return stats.values().stream()
                .max(Comparator.comparing(CategoryStats::getTotalAmount));
    }

    /**
     * 카테고리별 절약 가능 금액 계산
     */
    public BigDecimal calculatePotentialSavings(
            Map<SubscriptionCategory, CategoryStats> stats,
            SubscriptionCategory category,
            int reductionPercent) {

        CategoryStats stat = stats.get(category);
        if (stat == null) {
            return BigDecimal.ZERO;
        }

        return stat.getTotalAmount()
                .multiply(BigDecimal.valueOf(reductionPercent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}