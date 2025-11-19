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
 * Repository 테스트
 */
class RepositoryTest {

    private AnalysisHistoryRepository historyRepository;
    private SubscriptionRepository subscriptionRepository;
    private SubscriptionChangeRepository changeRepository;

    @BeforeAll
    static void setupDatabase() {
        DatabaseManager.initialize();
    }

    @BeforeEach
    void setUp() {
        historyRepository = new AnalysisHistoryRepository();
        subscriptionRepository = new SubscriptionRepository();
        changeRepository = new SubscriptionChangeRepository();
    }

    @Test
    @DisplayName("분석 이력 저장 및 조회 테스트")
    void testSaveAndFindAnalysisHistory() {
        // Given: 분석 이력 생성
        AnalysisHistory history = AnalysisHistory.builder()
                .id(UUID.randomUUID().toString())
                .analysisDate(LocalDateTime.now())
                .fileName("test.csv")
                .transactionCount(100)
                .subscriptionCount(5)
                .monthlyTotal(BigDecimal.valueOf(50000))
                .annualProjection(BigDecimal.valueOf(600000))
                .createdAt(LocalDateTime.now())
                .subscriptions(createSampleSubscriptions())
                .build();

        // When: 저장
        historyRepository.save(history);

        // Then: 조회하여 검증
        var found = historyRepository.findById(history.getId());
        assertTrue(found.isPresent());
        assertEquals(history.getFileName(), found.get().getFileName());
        assertEquals(history.getSubscriptionCount(), found.get().getSubscriptionCount());
    }

    @Test
    @DisplayName("최근 분석 이력 조회 테스트")
    void testFindRecentHistory() {
        // Given: 여러 분석 이력 저장
        for (int i = 0; i < 5; i++) {
            AnalysisHistory history = AnalysisHistory.builder()
                    .id(UUID.randomUUID().toString())
                    .analysisDate(LocalDateTime.now().minusDays(i))
                    .fileName("test" + i + ".csv")
                    .transactionCount(100)
                    .subscriptionCount(3)
                    .monthlyTotal(BigDecimal.valueOf(30000))
                    .annualProjection(BigDecimal.valueOf(360000))
                    .createdAt(LocalDateTime.now())
                    .subscriptions(new ArrayList<>())
                    .build();

            historyRepository.save(history);
        }

        // When: 최근 3개 조회
        List<AnalysisHistory> recent = historyRepository.findRecent(3);

        // Then: 3개가 조회되어야 함
        assertEquals(3, recent.size());
    }

    @Test
    @DisplayName("구독 변화 기록 및 조회 테스트")
    void testSubscriptionChanges() {
        // Given: 구독 변화 생성
        String subscriptionId = UUID.randomUUID().toString();

        SubscriptionChange change = SubscriptionChange.builder()
                .subscriptionId(subscriptionId)
                .changeType(SubscriptionChange.ChangeType.AMOUNT_CHANGED)
                .oldValue("₩10,000")
                .newValue("₩12,000")
                .changeDate(LocalDateTime.now())
                .notes("가격 인상")
                .build();

        // When: 저장
        changeRepository.save(change);

        // Then: 조회하여 검증
        List<SubscriptionChange> changes = changeRepository.findBySubscriptionId(subscriptionId);

        assertFalse(changes.isEmpty());
        assertEquals(subscriptionId, changes.get(0).getSubscriptionId());
        assertEquals(SubscriptionChange.ChangeType.AMOUNT_CHANGED, changes.get(0).getChangeType());
    }

    @Test
    @DisplayName("분석 이력 삭제 테스트")
    void testDeleteHistory() {
        // Given: 분석 이력 저장
        AnalysisHistory history = AnalysisHistory.builder()
                .id(UUID.randomUUID().toString())
                .analysisDate(LocalDateTime.now())
                .fileName("to-delete.csv")
                .transactionCount(50)
                .subscriptionCount(2)
                .monthlyTotal(BigDecimal.valueOf(20000))
                .annualProjection(BigDecimal.valueOf(240000))
                .createdAt(LocalDateTime.now())
                .subscriptions(new ArrayList<>())
                .build();

        historyRepository.save(history);

        // When: 삭제
        historyRepository.delete(history.getId());

        // Then: 조회되지 않아야 함
        var found = historyRepository.findById(history.getId());
        assertTrue(found.isEmpty());
    }

    // Helper 메소드
    private List<Subscription> createSampleSubscriptions() {
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

        subscriptions.add(Subscription.builder()
                .subscriptionId(UUID.randomUUID().toString())
                .serviceName("스포티파이")
                .monthlyAmount(BigDecimal.valueOf(9900))
                .billingCycle(Subscription.BillingCycle.MONTHLY)
                .status(Subscription.SubscriptionStatus.ACTIVE)
                .firstDetectedDate(LocalDate.now().minusMonths(2))
                .lastChargeDate(LocalDate.now())
                .transactionCount(2)
                .totalSpent(BigDecimal.valueOf(19800))
                .transactions(new ArrayList<>())
                .build());

        return subscriptions;
    }

    @AfterAll
    static void cleanup() {
        DatabaseManager.shutdown();
    }
}