package com.subtracker.infrastructure.repository;

import com.subtracker.domain.model.AnalysisHistory;
import com.subtracker.domain.model.Subscription;
import com.subtracker.domain.model.SubscriptionChange;
import com.subtracker.infrastructure.database.DatabaseManager;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository 통합 테스트
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RepositoryTest {

    private static AnalysisHistoryRepository historyRepository;
    private static SubscriptionRepository subscriptionRepository;
    private static SubscriptionChangeRepository changeRepository;

    @BeforeAll
    static void setupDatabase() {
        DatabaseManager.initialize();
        historyRepository = new AnalysisHistoryRepository();
        subscriptionRepository = new SubscriptionRepository();
        changeRepository = new SubscriptionChangeRepository();
    }

    @Test
    @Order(1)
    @DisplayName("분석 이력 저장 및 조회 테스트")
    void testSaveAndFindAnalysisHistory() {
        // Given
        AnalysisHistory history = createSampleAnalysisHistory();

        // When
        historyRepository.save(history);

        // Then
        var found = historyRepository.findById(history.getId());
        assertTrue(found.isPresent(), "저장된 이력을 찾을 수 있어야 함");
        assertEquals(history.getFileName(), found.get().getFileName());
        assertEquals(history.getSubscriptionCount(), found.get().getSubscriptionCount());
    }

    @Test
    @Order(2)
    @DisplayName("최근 분석 이력 조회 테스트")
    void testFindRecentHistory() {
        // Given: 여러 이력 저장
        for (int i = 0; i < 3; i++) {
            AnalysisHistory history = createSampleAnalysisHistory();
            historyRepository.save(history);
        }

        // When
        List<AnalysisHistory> recent = historyRepository.findRecent(5);

        // Then
        assertNotNull(recent);
        assertTrue(recent.size() >= 3);
    }

    @Test
    @Order(3)
    @DisplayName("구독 변화 기록 및 조회 테스트")
    void testSubscriptionChanges() {
        // Given
        String subscriptionId = UUID.randomUUID().toString();
        SubscriptionChange change = SubscriptionChange.builder()
                .subscriptionId(subscriptionId)
                .changeType(SubscriptionChange.ChangeType.AMOUNT_CHANGED)
                .oldValue("₩10,000")
                .newValue("₩12,000")
                .changeDate(LocalDateTime.now())
                .notes("가격 인상")
                .build();

        // When
        changeRepository.save(change);

        // Then
        List<SubscriptionChange> changes = changeRepository.findBySubscriptionId(subscriptionId);
        assertFalse(changes.isEmpty());
        assertEquals(SubscriptionChange.ChangeType.AMOUNT_CHANGED, changes.get(0).getChangeType());
    }

    @Test
    @Order(4)
    @DisplayName("분석 이력 삭제 테스트")
    void testDeleteHistory() {
        // Given
        AnalysisHistory history = createSampleAnalysisHistory();
        historyRepository.save(history);

        // When
        historyRepository.delete(history.getId());

        // Then
        var found = historyRepository.findById(history.getId());
        assertTrue(found.isEmpty(), "삭제된 이력은 조회되지 않아야 함");
    }

    // Helper 메소드
    private AnalysisHistory createSampleAnalysisHistory() {
        List<Subscription> subscriptions = new ArrayList<>();

        subscriptions.add(Subscription.builder()
                .subscriptionId(UUID.randomUUID().toString())
                .serviceName("넷플릭스")
                .monthlyAmount(BigDecimal.valueOf(17000))
                .billingCycle(Subscription.BillingCycle.MONTHLY)
                .status(Subscription.SubscriptionStatus.ACTIVE)
                .firstDetectedDate(LocalDate.now().minusMonths(3))
                .lastChargeDate(LocalDate.now())
                .transactionCount(3)
                .totalSpent(BigDecimal.valueOf(51000))
                .transactions(new ArrayList<>())
                .build());

        return AnalysisHistory.builder()
                .id(UUID.randomUUID().toString())
                .analysisDate(LocalDateTime.now())
                .fileName("test_" + System.currentTimeMillis() + ".csv")
                .transactionCount(100)
                .subscriptionCount(subscriptions.size())
                .monthlyTotal(BigDecimal.valueOf(17000))
                .annualProjection(BigDecimal.valueOf(204000))
                .createdAt(LocalDateTime.now())
                .subscriptions(subscriptions)
                .build();
    }

    @AfterAll
    static void cleanup() {
        // 테스트 후 정리는 필요시 추가
        // DatabaseManager.shutdown();
    }
}