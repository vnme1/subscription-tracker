package com.subtracker.application;

import com.subtracker.domain.model.*;
import com.subtracker.domain.service.CsvParser;
import com.subtracker.domain.service.SubscriptionDetector;
import com.subtracker.infrastructure.database.DatabaseManager;
import com.subtracker.infrastructure.repository.AnalysisHistoryRepository;
import com.subtracker.infrastructure.repository.SubscriptionChangeRepository;
import com.subtracker.infrastructure.repository.SubscriptionRepository;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 구독 관리 비즈니스 로직 (트랜잭션 관리 개선)
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
     * CSV 파일 분석 및 저장 (트랜잭션 관리)
     */
    public AnalysisHistory analyzeAndSave(String filePath, String fileName, boolean hasHeader) {
        validateInput(filePath, fileName);

        log.info("CSV 분석 시작: {}", fileName);

        try {
            // 1. CSV 파싱
            List<Transaction> transactions = csvParser.parseTransactions(filePath, hasHeader);
            validateTransactions(transactions);

            // 2. 구독 감지
            List<Subscription> subscriptions = detector.detectSubscriptions(transactions);
            log.info("{}개의 구독 서비스 감지", subscriptions.size());

            // 3. 분석 이력 생성
            SubscriptionSummary summary = SubscriptionSummary.from(subscriptions);
            AnalysisHistory history = AnalysisHistory.fromSummary(summary, fileName, transactions.size());

            // 4. 트랜잭션 내에서 저장 및 변화 추적
            saveWithTransaction(history, subscriptions);

            log.info("분석 이력 저장 완료: {}", history.getId());
            return history;

        } catch (Exception e) {
            log.error("CSV 분석 실패: {}", fileName, e);
            throw new RuntimeException("파일 분석 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 트랜잭션 내에서 이력 저장 및 변화 추적
     */
    private void saveWithTransaction(AnalysisHistory history, List<Subscription> subscriptions) {
        Connection conn = null;
        try {
            conn = DatabaseManager.getConnection();
            conn.setAutoCommit(false);

            // 1. 분석 이력 저장
            historyRepository.save(history);

            // 2. 변화 추적
            trackChangesInTransaction(subscriptions);

            conn.commit();
            log.debug("트랜잭션 커밋 완료: {}", history.getId());

        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                    log.warn("트랜잭션 롤백 완료");
                } catch (SQLException ex) {
                    log.error("롤백 실패", ex);
                }
            }
            throw new RuntimeException("데이터 저장 실패", e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    log.error("연결 종료 실패", e);
                }
            }
        }
    }

    /**
     * 구독 변화 추적 (트랜잭션 내)
     */
    private void trackChangesInTransaction(List<Subscription> currentSubscriptions) {
        try {
            List<AnalysisHistory> recentHistories = historyRepository.findRecent(2);

            if (recentHistories.size() < 2) {
                // 첫 분석 - 모두 신규로 기록
                currentSubscriptions.forEach(sub -> recordChange(sub, SubscriptionChange.ChangeType.CREATED,
                        null, sub.getServiceName(), "신규 구독 감지"));
                return;
            }

            // 이전 구독과 비교
            List<Subscription> previousSubscriptions = recentHistories.get(1).getSubscriptions();
            detectAndRecordChanges(previousSubscriptions, currentSubscriptions);

        } catch (Exception e) {
            log.error("변화 추적 실패", e);
            throw new RuntimeException("변화 추적 실패", e); // 트랜잭션 롤백을 위해 예외 전파
        }
    }

    /**
     * 구독 변화 감지 및 기록
     */
    private void detectAndRecordChanges(List<Subscription> previous, List<Subscription> current) {
        Map<String, Subscription> prevMap = previous.stream()
                .collect(Collectors.toMap(Subscription::getServiceName, s -> s));

        Map<String, Subscription> currMap = current.stream()
                .collect(Collectors.toMap(Subscription::getServiceName, s -> s));

        // 신규 구독
        current.stream()
                .filter(sub -> !prevMap.containsKey(sub.getServiceName()))
                .forEach(sub -> recordChange(sub, SubscriptionChange.ChangeType.CREATED,
                        null, sub.getServiceName(), "신규 구독 감지"));

        // 취소된 구독
        previous.stream()
                .filter(sub -> !currMap.containsKey(sub.getServiceName()))
                .forEach(sub -> recordChange(sub, SubscriptionChange.ChangeType.CANCELLED,
                        sub.getServiceName(), null, "구독 취소 또는 미감지"));

        // 변경된 구독
        current.stream()
                .filter(sub -> prevMap.containsKey(sub.getServiceName()))
                .forEach(sub -> checkSubscriptionChanges(prevMap.get(sub.getServiceName()), sub));
    }

    /**
     * 개별 구독의 변화 확인
     */
    private void checkSubscriptionChanges(Subscription prev, Subscription curr) {
        // 금액 변화
        if (prev.getMonthlyAmount().compareTo(curr.getMonthlyAmount()) != 0) {
            BigDecimal diff = curr.getMonthlyAmount().subtract(prev.getMonthlyAmount());
            String note = diff.compareTo(BigDecimal.ZERO) > 0 ? "금액 인상" : "금액 인하";

            recordChange(curr, SubscriptionChange.ChangeType.AMOUNT_CHANGED,
                    formatAmount(prev.getMonthlyAmount()),
                    formatAmount(curr.getMonthlyAmount()), note);
        }

        // 상태 변화
        if (prev.getStatus() != curr.getStatus()) {
            recordChange(curr, SubscriptionChange.ChangeType.STATUS_CHANGED,
                    prev.getStatus().getKorean(),
                    curr.getStatus().getKorean(), "구독 상태 변경");
        }

        // 주기 변화
        if (prev.getBillingCycle() != curr.getBillingCycle()) {
            recordChange(curr, SubscriptionChange.ChangeType.CYCLE_CHANGED,
                    prev.getBillingCycle().getKorean(),
                    curr.getBillingCycle().getKorean(), "결제 주기 변경");
        }
    }

    /**
     * 변화 기록 (예외 전파)
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
        log.debug("변화 기록: {} - {}", subscription.getServiceName(), type);
    }

    /**
     * 분석 이력 조회
     */
    public List<AnalysisHistory> getAllHistory() {
        return historyRepository.findAll();
    }

    public List<AnalysisHistory> getRecentHistory(int limit) {
        validateLimit(limit);
        return historyRepository.findRecent(limit);
    }

    public AnalysisHistory getHistoryById(String id) {
        validateId(id);
        return historyRepository.findById(id).orElse(null);
    }

    /**
     * 구독 관련 조회
     */
    public List<Subscription> getSubscriptionHistory(String serviceName) {
        validateId(serviceName);
        return subscriptionRepository.findByServiceName(serviceName);
    }

    public List<SubscriptionChange> getSubscriptionChanges(String subscriptionId) {
        validateId(subscriptionId);
        return changeRepository.findBySubscriptionId(subscriptionId);
    }

    public List<SubscriptionChange> getRecentChanges(int limit) {
        validateLimit(limit);
        return changeRepository.findRecent(limit);
    }

    /**
     * 이력 관리
     */
    public void deleteHistory(String id) {
        validateId(id);
        historyRepository.delete(id);
        log.info("분석 이력 삭제 완료: {}", id);
    }

    public ComparisonResult compareHistory(String id1, String id2) {
        validateId(id1);
        validateId(id2);

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

        return ComparisonResult.compare(history1, history2);
    }

    // ===== 유틸리티 메소드 =====

    private void validateInput(String filePath, String fileName) {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("파일 경로는 필수입니다");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("파일 이름은 필수입니다");
        }
    }

    private void validateTransactions(List<Transaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            throw new IllegalArgumentException("거래 내역이 없습니다");
        }
    }

    private void validateId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ID는 필수입니다");
        }
    }

    private void validateLimit(int limit) {
        if (limit <= 0 || limit > 100) {
            throw new IllegalArgumentException("limit은 1-100 사이여야 합니다");
        }
    }

    private String formatAmount(BigDecimal amount) {
        return String.format("₩%,d", amount.longValue());
    }
}