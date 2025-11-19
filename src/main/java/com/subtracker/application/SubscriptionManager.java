package com.subtracker.application;

import com.subtracker.domain.model.*;
import com.subtracker.domain.service.CsvParser;
import com.subtracker.domain.service.SubscriptionDetector;
import com.subtracker.infrastructure.repository.AnalysisHistoryRepository;
import com.subtracker.infrastructure.repository.SubscriptionChangeRepository;
import com.subtracker.infrastructure.repository.SubscriptionRepository;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 구독 관리 비즈니스 로직
 */
@Slf4j
public class SubscriptionManager {

    private final CsvParser csvParser;
    private final SubscriptionDetector detector;
    private final AnalysisHistoryRepository historyRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionChangeRepository changeRepository;

    public SubscriptionManager() {
        this.csvParser = new CsvParser();
        this.detector = new SubscriptionDetector();
        this.historyRepository = new AnalysisHistoryRepository();
        this.subscriptionRepository = new SubscriptionRepository();
        this.changeRepository = new SubscriptionChangeRepository();
    }

    /**
     * CSV 파일 분석 및 저장
     */
    public AnalysisHistory analyzeAndSave(String filePath, String fileName, boolean hasHeader) {
        // 1. CSV 파싱
        List<Transaction> transactions = csvParser.parseTransactions(filePath, hasHeader);

        // 2. 구독 감지
        List<Subscription> subscriptions = detector.detectSubscriptions(transactions);

        // 3. 요약 정보 생성
        SubscriptionSummary summary = SubscriptionSummary.from(subscriptions);

        // 4. 분석 이력 생성
        AnalysisHistory history = AnalysisHistory.fromSummary(summary, fileName, transactions.size());

        // 5. 데이터베이스에 저장
        historyRepository.save(history);

        // 6. 이전 분석과 비교하여 변화 추적
        trackChanges(subscriptions);

        log.info("분석 완료 및 저장: {} 개의 구독 발견", subscriptions.size());

        return history;
    }

    /**
     * 구독 변화 추적
     */
    private void trackChanges(List<Subscription> currentSubscriptions) {
        // 이전 분석 결과 조회
        List<AnalysisHistory> recentHistories = historyRepository.findRecent(2);

        if (recentHistories.size() < 2) {
            // 첫 번째 분석이거나 비교할 이전 데이터가 없음
            for (Subscription subscription : currentSubscriptions) {
                recordChange(subscription, SubscriptionChange.ChangeType.CREATED,
                        null, subscription.getServiceName(), "신규 구독 감지");
            }
            return;
        }

        AnalysisHistory previousHistory = recentHistories.get(1); // 이전 분석
        List<Subscription> previousSubscriptions = previousHistory.getSubscriptions();

        // 서비스명으로 매핑
        Map<String, Subscription> previousMap = previousSubscriptions.stream()
                .collect(Collectors.toMap(Subscription::getServiceName, s -> s));

        Map<String, Subscription> currentMap = currentSubscriptions.stream()
                .collect(Collectors.toMap(Subscription::getServiceName, s -> s));

        // 신규 구독 감지
        for (Subscription current : currentSubscriptions) {
            Subscription previous = previousMap.get(current.getServiceName());

            if (previous == null) {
                // 신규 구독
                recordChange(current, SubscriptionChange.ChangeType.CREATED,
                        null, current.getServiceName(), "신규 구독 감지");
            } else {
                // 변화 감지
                detectSubscriptionChanges(previous, current);
            }
        }

        // 취소된 구독 감지
        for (Subscription previous : previousSubscriptions) {
            if (!currentMap.containsKey(previous.getServiceName())) {
                recordChange(previous, SubscriptionChange.ChangeType.CANCELLED,
                        previous.getServiceName(), null, "구독 취소 또는 미감지");
            }
        }
    }

    /**
     * 개별 구독의 변화 감지
     */
    private void detectSubscriptionChanges(Subscription previous, Subscription current) {
        // 금액 변화
        if (previous.getMonthlyAmount().compareTo(current.getMonthlyAmount()) != 0) {
            recordChange(current, SubscriptionChange.ChangeType.AMOUNT_CHANGED,
                    formatAmount(previous.getMonthlyAmount()),
                    formatAmount(current.getMonthlyAmount()),
                    "월 금액 변경");
        }

        // 상태 변화
        if (previous.getStatus() != current.getStatus()) {
            recordChange(current, SubscriptionChange.ChangeType.STATUS_CHANGED,
                    previous.getStatus().getKorean(),
                    current.getStatus().getKorean(),
                    "구독 상태 변경");
        }

        // 주기 변화
        if (previous.getBillingCycle() != current.getBillingCycle()) {
            recordChange(current, SubscriptionChange.ChangeType.CYCLE_CHANGED,
                    previous.getBillingCycle().getKorean(),
                    current.getBillingCycle().getKorean(),
                    "결제 주기 변경");
        }
    }

    /**
     * 변화 기록
     */
    private void recordChange(Subscription subscription, SubscriptionChange.ChangeType type,
            String oldValue, String newValue, String notes) {
        SubscriptionChange change = SubscriptionChange.builder()
                .subscriptionId(subscription.getSubscriptionId())
                .changeType(type)
                .oldValue(oldValue)
                .newValue(newValue)
                .changeDate(java.time.LocalDateTime.now())
                .notes(notes)
                .build();

        changeRepository.save(change);
    }

    /**
     * 금액 포맷팅
     */
    private String formatAmount(BigDecimal amount) {
        return String.format("₩%,.0f", amount);
    }

    /**
     * 모든 분석 이력 조회
     */
    public List<AnalysisHistory> getAllHistory() {
        return historyRepository.findAll();
    }

    /**
     * 최근 분석 이력 조회
     */
    public List<AnalysisHistory> getRecentHistory(int limit) {
        return historyRepository.findRecent(limit);
    }

    /**
     * 특정 분석 이력 조회
     */
    public AnalysisHistory getHistoryById(String id) {
        return historyRepository.findById(id).orElse(null);
    }

    /**
     * 특정 서비스의 이력 조회
     */
    public List<Subscription> getSubscriptionHistory(String serviceName) {
        return subscriptionRepository.findByServiceName(serviceName);
    }

    /**
     * 구독 변화 이력 조회
     */
    public List<SubscriptionChange> getSubscriptionChanges(String subscriptionId) {
        return changeRepository.findBySubscriptionId(subscriptionId);
    }

    /**
     * 최근 변화 이력 조회
     */
    public List<SubscriptionChange> getRecentChanges(int limit) {
        return changeRepository.findRecent(limit);
    }

    /**
     * 분석 이력 삭제
     */
    public void deleteHistory(String id) {
        historyRepository.delete(id);
    }

    /**
     * 두 분석 이력 비교
     */
    public ComparisonResult compareHistory(String id1, String id2) {
        AnalysisHistory history1 = historyRepository.findById(id1).orElse(null);
        AnalysisHistory history2 = historyRepository.findById(id2).orElse(null);

        if (history1 == null || history2 == null) {
            return null;
        }

        return ComparisonResult.compare(history1, history2);
    }
}