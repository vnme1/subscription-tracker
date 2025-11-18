package com.subtracker.domain.service;

import com.subtracker.domain.model.Subscription;
import com.subtracker.domain.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SubscriptionDetector 단위 테스트
 */
class SubscriptionDetectorTest {

    private SubscriptionDetector detector;

    @BeforeEach
    void setUp() {
        detector = new SubscriptionDetector();
    }

    @Test
    @DisplayName("월간 구독 서비스 감지 테스트")
    void testDetectMonthlySubscription() {
        // Given: 넷플릭스 월간 결제 내역
        List<Transaction> transactions = createMonthlyTransactions(
                "넷플릭스",
                BigDecimal.valueOf(17000),
                LocalDate.of(2024, 1, 5),
                5 // 5개월간의 거래
        );

        // When: 구독 감지 실행
        List<Subscription> subscriptions = detector.detectSubscriptions(transactions);

        // Then: 1개의 월간 구독이 감지되어야 함
        assertNotNull(subscriptions);
        assertEquals(1, subscriptions.size());

        Subscription netflix = subscriptions.get(0);
        assertEquals("넷플릭스", netflix.getServiceName());
        assertEquals(Subscription.BillingCycle.MONTHLY, netflix.getBillingCycle());
        assertEquals(0, BigDecimal.valueOf(17000).compareTo(netflix.getMonthlyAmount()));
    }

    @Test
    @DisplayName("여러 구독 서비스 동시 감지 테스트")
    void testDetectMultipleSubscriptions() {
        // Given: 여러 서비스의 거래 내역
        List<Transaction> allTransactions = new ArrayList<>();

        // 넷플릭스
        allTransactions.addAll(createMonthlyTransactions(
                "넷플릭스", BigDecimal.valueOf(17000),
                LocalDate.of(2024, 1, 5), 3));

        // 스포티파이
        allTransactions.addAll(createMonthlyTransactions(
                "스포티파이", BigDecimal.valueOf(9900),
                LocalDate.of(2024, 1, 10), 3));

        // 일회성 거래 (구독 아님)
        allTransactions.add(createTransaction(
                "스타벅스", BigDecimal.valueOf(5500),
                LocalDate.of(2024, 1, 15)));

        // When: 구독 감지 실행
        List<Subscription> subscriptions = detector.detectSubscriptions(allTransactions);

        // Then: 2개의 구독만 감지되어야 함
        assertEquals(2, subscriptions.size());

        boolean hasNetflix = subscriptions.stream()
                .anyMatch(s -> s.getServiceName().equals("넷플릭스"));
        boolean hasSpotify = subscriptions.stream()
                .anyMatch(s -> s.getServiceName().equals("스포티파이"));

        assertTrue(hasNetflix);
        assertTrue(hasSpotify);
    }

    @Test
    @DisplayName("금액 변동이 있는 구독 감지 테스트")
    void testDetectSubscriptionWithAmountVariation() {
        // Given: 약간의 금액 변동이 있는 거래
        List<Transaction> transactions = new ArrayList<>();
        LocalDate date = LocalDate.of(2024, 1, 1);

        transactions.add(createTransaction("유튜브프리미엄", BigDecimal.valueOf(14900), date));
        transactions.add(createTransaction("유튜브프리미엄", BigDecimal.valueOf(14900), date.plusDays(30)));
        transactions.add(createTransaction("유튜브프리미엄", BigDecimal.valueOf(15400), date.plusDays(60))); // 3% 인상
        transactions.add(createTransaction("유튜브프리미엄", BigDecimal.valueOf(15400), date.plusDays(90)));

        // When: 구독 감지 실행
        List<Subscription> subscriptions = detector.detectSubscriptions(transactions);

        // Then: 금액 변동에도 불구하고 구독으로 감지되어야 함
        assertEquals(1, subscriptions.size());
        assertEquals("유튜브프리미엄", subscriptions.get(0).getServiceName());
    }

    @Test
    @DisplayName("분기별 구독 감지 테스트")
    void testDetectQuarterlySubscription() {
        // Given: 3개월마다 결제되는 서비스
        List<Transaction> transactions = new ArrayList<>();
        LocalDate date = LocalDate.of(2024, 1, 1);

        for (int i = 0; i < 4; i++) {
            transactions.add(createTransaction(
                    "어도비CC",
                    BigDecimal.valueOf(99000),
                    date.plusDays(90 * i) // 90일 간격
            ));
        }

        // When: 구독 감지 실행
        List<Subscription> subscriptions = detector.detectSubscriptions(transactions);

        // Then: 분기별 구독으로 감지되어야 함
        assertEquals(1, subscriptions.size());
        assertEquals(Subscription.BillingCycle.QUARTERLY, subscriptions.get(0).getBillingCycle());
    }

    @Test
    @DisplayName("최소 발생 횟수 미달 테스트")
    void testNotEnoughOccurrences() {
        // Given: 1회만 발생한 거래
        List<Transaction> transactions = List.of(
                createTransaction("넷플릭스", BigDecimal.valueOf(17000), LocalDate.now()));

        // When: 구독 감지 실행
        List<Subscription> subscriptions = detector.detectSubscriptions(transactions);

        // Then: 구독으로 감지되지 않아야 함
        assertTrue(subscriptions.isEmpty());
    }

    @Test
    @DisplayName("빈 거래 목록 처리 테스트")
    void testEmptyTransactions() {
        // Given: 빈 리스트
        List<Transaction> transactions = new ArrayList<>();

        // When: 구독 감지 실행
        List<Subscription> subscriptions = detector.detectSubscriptions(transactions);

        // Then: 빈 결과 반환
        assertNotNull(subscriptions);
        assertTrue(subscriptions.isEmpty());
    }

    @Test
    @DisplayName("null 입력 처리 테스트")
    void testNullTransactions() {
        // When: null 입력
        List<Subscription> subscriptions = detector.detectSubscriptions(null);

        // Then: 빈 결과 반환
        assertNotNull(subscriptions);
        assertTrue(subscriptions.isEmpty());
    }

    // 헬퍼 메소드들

    private List<Transaction> createMonthlyTransactions(String merchant, BigDecimal amount,
            LocalDate startDate, int months) {
        List<Transaction> transactions = new ArrayList<>();

        for (int i = 0; i < months; i++) {
            transactions.add(createTransaction(
                    merchant,
                    amount,
                    startDate.plusMonths(i)));
        }

        return transactions;
    }

    private Transaction createTransaction(String merchant, BigDecimal amount, LocalDate date) {
        return Transaction.builder()
                .transactionId(UUID.randomUUID().toString())
                .merchant(merchant)
                .amount(amount)
                .transactionDate(date)
                .category("테스트")
                .build();
    }
}