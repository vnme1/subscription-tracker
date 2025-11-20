package com.subtracker.domain.service;

import com.subtracker.config.AppConfig;
import com.subtracker.domain.model.Subscription;
import com.subtracker.domain.model.Transaction;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 거래 내역에서 구독 서비스를 자동으로 감지하는 서비스 (설정 외부화)
 */
@Slf4j
public class SubscriptionDetector {

    // 구독 감지 설정값 (외부 설정 사용)
    private final int minOccurrence;
    private final double amountTolerance;
    private final int maxDayVariance;

    public SubscriptionDetector() {
        this.minOccurrence = AppConfig.getSubscriptionMinOccurrence();
        this.amountTolerance = AppConfig.getSubscriptionAmountTolerance();
        this.maxDayVariance = AppConfig.getSubscriptionMaxDayVariance();

        log.info("SubscriptionDetector 초기화 - 최소발생: {}, 금액오차: {}%, 일수오차: {}일",
                minOccurrence, amountTolerance, maxDayVariance);
    }

    /**
     * 거래 내역에서 구독 서비스 감지
     */
    public List<Subscription> detectSubscriptions(List<Transaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return new ArrayList<>();
        }

        log.debug("총 {}건의 거래 내역 분석 시작", transactions.size());

        // 1. 가맹점별로 거래 그룹화
        Map<String, List<Transaction>> merchantGroups = groupByMerchant(transactions);
        log.debug("{}개의 가맹점 그룹 생성", merchantGroups.size());

        // 2. 각 가맹점 그룹 분석하여 구독 패턴 찾기
        List<Subscription> subscriptions = new ArrayList<>();

        for (Map.Entry<String, List<Transaction>> entry : merchantGroups.entrySet()) {
            String merchant = entry.getKey();
            List<Transaction> merchantTransactions = entry.getValue();

            // 최소 발생 횟수 체크
            if (merchantTransactions.size() < minOccurrence) {
                log.trace("가맹점 '{}': 발생 횟수 부족 ({}회 < {}회)",
                        merchant, merchantTransactions.size(), minOccurrence);
                continue;
            }

            // 구독 패턴 분석
            Subscription subscription = analyzeSubscriptionPattern(merchant, merchantTransactions);
            if (subscription != null) {
                subscriptions.add(subscription);
                log.info("구독 감지: {} - {}원/{}",
                        subscription.getServiceName(),
                        subscription.getMonthlyAmount(),
                        subscription.getBillingCycle().getKorean());
            }
        }

        log.info("총 {}개의 구독 서비스 감지 완료", subscriptions.size());
        return subscriptions;
    }

    /**
     * 가맹점명으로 거래 내역 그룹화
     */
    private Map<String, List<Transaction>> groupByMerchant(List<Transaction> transactions) {
        Map<String, List<Transaction>> groups = new HashMap<>();

        for (Transaction transaction : transactions) {
            String normalizedMerchant = normalizeMerchantName(transaction.getMerchant());

            // 기존 그룹에 속하는지 확인
            boolean found = false;
            for (Map.Entry<String, List<Transaction>> entry : groups.entrySet()) {
                if (isSimilarMerchant(normalizedMerchant, entry.getKey())) {
                    entry.getValue().add(transaction);
                    found = true;
                    break;
                }
            }

            // 새 그룹 생성
            if (!found) {
                List<Transaction> list = new ArrayList<>();
                list.add(transaction);
                groups.put(normalizedMerchant, list);
            }
        }

        return groups;
    }

    /**
     * 가맹점별 거래 패턴을 분석하여 구독 여부 판단
     */
    private Subscription analyzeSubscriptionPattern(String merchant, List<Transaction> transactions) {
        // 날짜순 정렬
        transactions.sort(Comparator.comparing(Transaction::getTransactionDate));

        // 금액 일관성 체크
        if (!hasConsistentAmount(transactions)) {
            log.trace("가맹점 '{}': 금액 일관성 부족", merchant);
            return null;
        }

        // 주기성 체크
        Subscription.BillingCycle cycle = detectBillingCycle(transactions);
        if (cycle == Subscription.BillingCycle.UNKNOWN) {
            log.trace("가맹점 '{}': 결제 주기 미감지", merchant);
            return null;
        }

        // 구독 객체 생성
        return createSubscription(merchant, transactions, cycle);
    }

    /**
     * 금액 일관성 체크 (대부분의 거래가 비슷한 금액인지)
     */
    private boolean hasConsistentAmount(List<Transaction> transactions) {
        if (transactions.size() < 2) {
            return false;
        }

        // 금액 리스트 (양수만)
        List<BigDecimal> amounts = transactions.stream()
                .map(Transaction::getAmount)
                .filter(amount -> amount.compareTo(BigDecimal.ZERO) > 0)
                .sorted()
                .collect(Collectors.toList());

        if (amounts.isEmpty()) {
            return false;
        }

        // 중앙값 계산
        BigDecimal median = amounts.get(amounts.size() / 2);

        // 중앙값 기준으로 오차 범위 내에 있는 거래 비율 계산
        long consistentCount = amounts.stream()
                .filter(amount -> isWithinTolerance(amount, median, amountTolerance))
                .count();

        double consistencyRatio = (double) consistentCount / amounts.size();

        // 80% 이상이 일관된 금액이면 구독으로 판단
        return consistencyRatio >= 0.8;
    }

    /**
     * 결제 주기 감지
     */
    private Subscription.BillingCycle detectBillingCycle(List<Transaction> transactions) {
        if (transactions.size() < 2) {
            return Subscription.BillingCycle.UNKNOWN;
        }

        // 거래 간 일수 계산
        List<Long> intervals = new ArrayList<>();
        for (int i = 1; i < transactions.size(); i++) {
            long days = ChronoUnit.DAYS.between(
                    transactions.get(i - 1).getTransactionDate(),
                    transactions.get(i).getTransactionDate());
            if (days > 0) {
                intervals.add(days);
            }
        }

        if (intervals.isEmpty()) {
            return Subscription.BillingCycle.UNKNOWN;
        }

        // 평균 간격 계산
        double avgInterval = intervals.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);

        // 주기 판단 (오차 범위 고려)
        if (isInRange(avgInterval, 30, maxDayVariance)) {
            return Subscription.BillingCycle.MONTHLY;
        } else if (isInRange(avgInterval, 90, maxDayVariance * 2)) {
            return Subscription.BillingCycle.QUARTERLY;
        } else if (isInRange(avgInterval, 180, maxDayVariance * 3)) {
            return Subscription.BillingCycle.SEMI_ANNUAL;
        } else if (isInRange(avgInterval, 365, maxDayVariance * 5)) {
            return Subscription.BillingCycle.ANNUAL;
        }

        return Subscription.BillingCycle.UNKNOWN;
    }

    /**
     * 구독 객체 생성
     */
    private Subscription createSubscription(String merchant,
            List<Transaction> transactions,
            Subscription.BillingCycle cycle) {
        // 금액 계산
        BigDecimal avgAmount = transactions.stream()
                .map(Transaction::getAmount)
                .filter(amount -> amount.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(transactions.size()), 2, RoundingMode.HALF_UP);

        // 월 금액 환산
        BigDecimal monthlyAmount = calculateMonthlyAmount(avgAmount, cycle);

        // 최초/최근 거래 날짜
        LocalDate firstDate = transactions.stream()
                .map(Transaction::getTransactionDate)
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now());

        LocalDate lastDate = transactions.stream()
                .map(Transaction::getTransactionDate)
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now());

        // 총 지출액
        BigDecimal totalSpent = transactions.stream()
                .map(Transaction::getAmount)
                .filter(amount -> amount.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 구독 생성
        Subscription subscription = Subscription.builder()
                .subscriptionId(UUID.randomUUID().toString())
                .serviceName(merchant)
                .monthlyAmount(monthlyAmount)
                .lastAmount(avgAmount)
                .billingCycle(cycle)
                .firstDetectedDate(firstDate)
                .lastChargeDate(lastDate)
                .status(determineStatus(lastDate))
                .transactionCount(transactions.size())
                .totalSpent(totalSpent)
                .transactions(new ArrayList<>(transactions))
                .build();

        // 다음 결제일 계산
        subscription.calculateNextChargeDate();

        return subscription;
    }

    /**
     * 월 금액 환산
     */
    private BigDecimal calculateMonthlyAmount(BigDecimal amount, Subscription.BillingCycle cycle) {
        return switch (cycle) {
            case MONTHLY -> amount;
            case QUARTERLY -> amount.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
            case SEMI_ANNUAL -> amount.divide(BigDecimal.valueOf(6), 2, RoundingMode.HALF_UP);
            case ANNUAL -> amount.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            default -> amount;
        };
    }

    /**
     * 구독 상태 결정
     */
    private Subscription.SubscriptionStatus determineStatus(LocalDate lastChargeDate) {
        long daysSinceLastCharge = ChronoUnit.DAYS.between(lastChargeDate, LocalDate.now());

        if (daysSinceLastCharge <= 60) {
            return Subscription.SubscriptionStatus.ACTIVE;
        } else if (daysSinceLastCharge <= 90) {
            return Subscription.SubscriptionStatus.PENDING;
        } else {
            return Subscription.SubscriptionStatus.INACTIVE;
        }
    }

    /**
     * 가맹점명 정규화
     */
    private String normalizeMerchantName(String merchant) {
        if (merchant == null) {
            return "";
        }

        return merchant
                .toUpperCase()
                .replaceAll("[^A-Z0-9가-힣]", "")
                .replaceAll("\\s+", "");
    }

    /**
     * 가맹점명 유사도 체크
     */
    private boolean isSimilarMerchant(String merchant1, String merchant2) {
        if (merchant1.equals(merchant2)) {
            return true;
        }

        // 한쪽이 다른 쪽을 포함하는지 체크
        return merchant1.contains(merchant2) || merchant2.contains(merchant1);
    }

    /**
     * 금액 오차 범위 체크
     */
    private boolean isWithinTolerance(BigDecimal value, BigDecimal target, double tolerancePercent) {
        BigDecimal tolerance = target.multiply(BigDecimal.valueOf(tolerancePercent / 100));
        BigDecimal difference = value.subtract(target).abs();
        return difference.compareTo(tolerance) <= 0;
    }

    /**
     * 값이 특정 범위 내에 있는지 체크
     */
    private boolean isInRange(double value, int target, int variance) {
        return value >= (target - variance) && value <= (target + variance);
    }
}