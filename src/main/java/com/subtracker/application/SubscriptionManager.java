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
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 구독 관리 비즈니스 로직 (개선 버전)
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

        log.info("SubscriptionManager 초기화 완료");
    }

    /**
     * CSV 파일 분석 및 저장
     */
    public AnalysisHistory analyzeAndSave(String filePath, String fileName, boolean hasHeader) {
        Objects.requireNonNull(filePath, "파일 경로는 null일 수 없습니다");
        Objects.requireNonNull(fileName, "파일 이름은 null일 수 없습니다");

        log.info("CSV 분석 시작: {}", fileName);

        try {
            // 1. CSV 파싱
            List<Transaction> transactions = csvParser.parseTransactions(filePath, hasHeader);

            if (transactions.isEmpty()) {
                log.warn("거래 내역이 비어있습니다: {}", fileName);
                throw new IllegalArgumentException("거래 내역이 없습니다");
            }

            log.debug("{}개의 거래 내역 파싱 완료", transactions.size());

            // 2. 구독 감지
            List<Subscription> subscriptions = detector.detectSubscriptions(transactions);
            log.info("{}개의 구독 서비스 감지", subscriptions.size());

            // 3. 요약 정보 생성
            SubscriptionSummary summary = SubscriptionSummary.from(subscriptions);

            // 4. 분석 이력 생성
            AnalysisHistory history = AnalysisHistory.fromSummary(
                    summary, fileName, transactions.size());

            // 5. 데이터베이스에 저장
            historyRepository.save(history);
            log.info("분석 이력 저장 완료: {}", history.getId());

            // 6. 이전 분석과 비교하여 변화 추적
            trackChanges(subscriptions);

            return history;

        } catch (Exception e) {
            log.error("CSV 분석 중 오류 발생: {}", fileName, e);
            throw new RuntimeException("파일 분석 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 구독 변화 추적
     */
    private void trackChanges(List<Subscription> currentSubscriptions) {
        try {
            List<AnalysisHistory> recentHistories = historyRepository.findRecent(2);

            if (recentHistories.size() < 2) {
                // 첫 번째 분석 - 모든 구독을 신규로 기록
                recordNewSubscriptions(currentSubscriptions);
                return;
            }

            AnalysisHistory previousHistory = recentHistories.get(1);
            List<Subscription> previousSubscriptions = previousHistory.getSubscriptions();

            Map<String, Subscription> previousMap = previousSubscriptions.stream()
                    .collect(Collectors.toMap(Subscription::getServiceName, s -> s));

            Map<String, Subscription> currentMap = currentSubscriptions.stream()
                    .collect(Collectors.toMap(Subscription::getServiceName, s -> s));

            // 신규 및 변경된 구독 감지
            for (Subscription current : currentSubscriptions) {
                Subscription previous = previousMap.get(current.getServiceName());

                if (previous == null) {
                    recordChange(current, SubscriptionChange.ChangeType.CREATED,
                            null, current.getServiceName(), "신규 구독 감지");
                } else {
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

            log.debug("구독 변화 추적 완료");

        } catch (Exception e) {
            log.error("변화 추적 중 오류 발생", e);
            // 변화 추적 실패는 전체 프로세스를 중단시키지 않음
        }
    }

    /**
     * 신규 구독 기록
     */
    private void recordNewSubscriptions(List<Subscription> subscriptions) {
        for (Subscription subscription : subscriptions) {
            recordChange(subscription, SubscriptionChange.ChangeType.CREATED,
                    null, subscription.getServiceName(), "신규 구독 감지");
        }
    }

    /**
     * 개별 구독의 변화 감지
     */
    private void detectSubscriptionChanges(Subscription previous, Subscription current) {
        // 금액 변화
        if (previous.getMonthlyAmount().compareTo(current.getMonthlyAmount()) != 0) {
            BigDecimal diff = current.getMonthlyAmount().subtract(previous.getMonthlyAmount());
            String note = diff.compareTo(BigDecimal.ZERO) > 0 ? "금액 인상" : "금액 인하";

            recordChange(current, SubscriptionChange.ChangeType.AMOUNT_CHANGED,
                    formatAmount(previous.getMonthlyAmount()),
                    formatAmount(current.getMonthlyAmount()),
                    note);
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
        try {
            SubscriptionChange change = SubscriptionChange.builder()
                    .subscriptionId(subscription.getSubscriptionId())
                    .changeType(type)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .changeDate(java.time.LocalDateTime.now())
                    .notes(notes)
                    .build();

            changeRepository.save(change);
            log.debug("변화 기록: {} - {}", subscription.getServiceName(), type);

        } catch (Exception e) {
            log.error("변화 기록 실패: {}", subscription.getServiceName(), e);
        }
    }

    /**
     * 금액 포맷팅
     */
    private String formatAmount(BigDecimal amount) {
        return String.format("₩%,d", amount.longValue());
    }

    /**
     * 모든 분석 이력 조회
     */
    public List<AnalysisHistory> getAllHistory() {
        try {
            return historyRepository.findAll();
        } catch (Exception e) {
            log.error("분석 이력 조회 실패", e);
            throw new RuntimeException("이력 조회 실패", e);
        }
    }

    /**
     * 최근 분석 이력 조회
     */
    public List<AnalysisHistory> getRecentHistory(int limit) {
        if (limit <= 0 || limit > 100) {
            throw new IllegalArgumentException("limit은 1-100 사이여야 합니다");
        }

        try {
            return historyRepository.findRecent(limit);
        } catch (Exception e) {
            log.error("최근 이력 조회 실패", e);
            throw new RuntimeException("이력 조회 실패", e);
        }
    }

    /**
     * 특정 분석 이력 조회
     */
    public AnalysisHistory getHistoryById(String id) {
        Objects.requireNonNull(id, "ID는 null일 수 없습니다");

        try {
            return historyRepository.findById(id).orElse(null);
        } catch (Exception e) {
            log.error("이력 조회 실패: {}", id, e);
            throw new RuntimeException("이력 조회 실패", e);
        }
    }

    /**
     * 특정 서비스의 이력 조회
     */
    public List<Subscription> getSubscriptionHistory(String serviceName) {
        Objects.requireNonNull(serviceName, "서비스명은 null일 수 없습니다");

        try {
            return subscriptionRepository.findByServiceName(serviceName);
        } catch (Exception e) {
            log.error("구독 이력 조회 실패: {}", serviceName, e);
            throw new RuntimeException("구독 이력 조회 실패", e);
        }
    }

    /**
     * 구독 변화 이력 조회
     */
    public List<SubscriptionChange> getSubscriptionChanges(String subscriptionId) {
        Objects.requireNonNull(subscriptionId, "구독 ID는 null일 수 없습니다");

        try {
            return changeRepository.findBySubscriptionId(subscriptionId);
        } catch (Exception e) {
            log.error("변화 이력 조회 실패: {}", subscriptionId, e);
            throw new RuntimeException("변화 이력 조회 실패", e);
        }
    }

    /**
     * 최근 변화 이력 조회
     */
    public List<SubscriptionChange> getRecentChanges(int limit) {
        if (limit <= 0 || limit > 100) {
            throw new IllegalArgumentException("limit은 1-100 사이여야 합니다");
        }

        try {
            return changeRepository.findRecent(limit);
        } catch (Exception e) {
            log.error("최근 변화 조회 실패", e);
            throw new RuntimeException("변화 조회 실패", e);
        }
    }

    /**
     * 분석 이력 삭제
     */
    public void deleteHistory(String id) {
        Objects.requireNonNull(id, "ID는 null일 수 없습니다");

        try {
            historyRepository.delete(id);
            log.info("분석 이력 삭제 완료: {}", id);
        } catch (Exception e) {
            log.error("이력 삭제 실패: {}", id, e);
            throw new RuntimeException("이력 삭제 실패", e);
        }
    }

    /**
     * 두 분석 이력 비교
     */
    public ComparisonResult compareHistory(String id1, String id2) {
        Objects.requireNonNull(id1, "첫 번째 ID는 null일 수 없습니다");
        Objects.requireNonNull(id2, "두 번째 ID는 null일 수 없습니다");

        try {
            AnalysisHistory history1 = historyRepository.findById(id1).orElse(null);
            AnalysisHistory history2 = historyRepository.findById(id2).orElse(null);

            if (history1 == null || history2 == null) {
                log.warn("비교할 이력을 찾을 수 없음: {} 또는 {}", id1, id2);
                return null;
            }

            // 날짜순 정렬 (이전 → 최신)
            if (history1.getAnalysisDate().isAfter(history2.getAnalysisDate())) {
                AnalysisHistory temp = history1;
                history1 = history2;
                history2 = temp;
            }

            ComparisonResult result = ComparisonResult.compare(history1, history2);
            log.info("이력 비교 완료: {} vs {}", id1, id2);

            return result;

        } catch (Exception e) {
            log.error("이력 비교 실패: {} vs {}", id1, id2, e);
            throw new RuntimeException("이력 비교 실패", e);
        }
    }
}