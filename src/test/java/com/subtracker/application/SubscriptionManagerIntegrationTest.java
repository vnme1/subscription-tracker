package com.subtracker.application;

//import com.subtracker.application.SubscriptionManager;
import com.subtracker.domain.model.AnalysisHistory;
import com.subtracker.domain.model.Subscription;
import com.subtracker.domain.model.SubscriptionChange;
import com.subtracker.infrastructure.database.DatabaseManager;
import org.junit.jupiter.api.*;

//import java.io.File;
//import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SubscriptionManager 통합 테스트
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SubscriptionManagerIntegrationTest {

    private static SubscriptionManager manager;
    private static Path tempCsvFile;
    private static String testHistoryId;

    @BeforeAll
    static void setup() throws Exception {
        // 데이터베이스 초기화
        DatabaseManager.initialize();

        // 매니저 인스턴스 생성
        manager = new SubscriptionManager();

        // 테스트용 CSV 파일 생성
        tempCsvFile = createTestCsvFile();
    }

    @Test
    @Order(1)
    @DisplayName("CSV 파일 분석 및 저장 - 성공 케이스")
    void testAnalyzeAndSave_Success() {
        // When
        AnalysisHistory history = manager.analyzeAndSave(
                tempCsvFile.toString(),
                "test_transactions.csv",
                true);

        // Then
        assertNotNull(history);
        assertNotNull(history.getId());
        testHistoryId = history.getId(); // 다음 테스트를 위해 저장

        assertTrue(history.getSubscriptionCount() > 0, "최소 1개 이상의 구독이 감지되어야 함");
        assertTrue(history.getTransactionCount() > 0, "거래 내역이 있어야 함");
        assertNotNull(history.getSubscriptions());
        assertFalse(history.getSubscriptions().isEmpty());

        System.out.println("✅ 분석 완료: " + history.getSubscriptionCount() + "개 구독 감지");
    }

    @Test
    @Order(2)
    @DisplayName("분석 이력 조회")
    void testGetHistoryById() {
        // Given
        assertNotNull(testHistoryId, "테스트 이력 ID가 설정되어야 함");

        // When
        AnalysisHistory history = manager.getHistoryById(testHistoryId);

        // Then
        assertNotNull(history);
        assertEquals(testHistoryId, history.getId());
        assertNotNull(history.getSubscriptions());

        System.out.println("✅ 이력 조회 성공: " + history.getFileName());
    }

    @Test
    @Order(3)
    @DisplayName("최근 이력 목록 조회")
    void testGetRecentHistory() {
        // When
        List<AnalysisHistory> histories = manager.getRecentHistory(10);

        // Then
        assertNotNull(histories);
        assertFalse(histories.isEmpty());

        // 날짜 역순 정렬 확인
        for (int i = 0; i < histories.size() - 1; i++) {
            assertTrue(
                    histories.get(i).getAnalysisDate()
                            .isAfter(histories.get(i + 1).getAnalysisDate())
                            || histories.get(i).getAnalysisDate()
                                    .isEqual(histories.get(i + 1).getAnalysisDate()),
                    "이력은 최신순으로 정렬되어야 함");
        }

        System.out.println("✅ 최근 이력 조회: " + histories.size() + "개");
    }

    @Test
    @Order(4)
    @DisplayName("두 번째 분석 및 변화 추적")
    void testSecondAnalysisAndChangeTracking() throws Exception {
        // Given: 약간 다른 데이터로 두 번째 CSV 생성
        Path secondCsv = createModifiedCsvFile();

        // When
        AnalysisHistory secondHistory = manager.analyzeAndSave(
                secondCsv.toString(),
                "test_transactions_2.csv",
                true);

        // Then
        assertNotNull(secondHistory);

        // 변화 이력 확인
        List<SubscriptionChange> changes = manager.getRecentChanges(10);
        assertNotNull(changes);

        if (!changes.isEmpty()) {
            System.out.println("✅ 변화 감지: " + changes.size() + "개");
            changes.forEach(c -> System.out.println("  - " + c.getChangeType().getKorean() + ": " + c.getNotes()));
        }

        // 정리
        Files.deleteIfExists(secondCsv);
    }

    @Test
    @Order(5)
    @DisplayName("이력 비교")
    void testCompareHistory() throws Exception {
        // Given: 두 개의 이력 생성
        AnalysisHistory history1 = manager.analyzeAndSave(
                tempCsvFile.toString(),
                "compare_test_1.csv",
                true);

        Path modifiedCsv = createModifiedCsvFile();
        AnalysisHistory history2 = manager.analyzeAndSave(
                modifiedCsv.toString(),
                "compare_test_2.csv",
                true);

        // When
        ComparisonResult comparison = manager.compareHistory(
                history1.getId(),
                history2.getId());

        // Then
        assertNotNull(comparison);
        assertNotNull(comparison.getOldAnalysisDate());
        assertNotNull(comparison.getNewAnalysisDate());

        System.out.println("✅ 이력 비교 완료");
        System.out.println("  구독 개수 변화: " + comparison.getSubscriptionCountDiff());
        System.out.println("  월 지출 변화: ₩" + comparison.getMonthlyTotalDiff());

        // 정리
        Files.deleteIfExists(modifiedCsv);
    }

    @Test
    @Order(6)
    @DisplayName("서비스별 이력 조회")
    void testGetSubscriptionHistory() {
        // Given
        String serviceName = "넷플릭스";

        // When
        List<Subscription> subscriptions = manager.getSubscriptionHistory(serviceName);

        // Then
        assertNotNull(subscriptions);

        if (!subscriptions.isEmpty()) {
            subscriptions.forEach(sub -> assertTrue(sub.getServiceName().contains(serviceName)));
            System.out.println("✅ " + serviceName + " 이력: " + subscriptions.size() + "개");
        }
    }

    @Test
    @Order(7)
    @DisplayName("변화 이력 조회")
    void testGetRecentChanges() {
        // When
        List<SubscriptionChange> changes = manager.getRecentChanges(20);

        // Then
        assertNotNull(changes);

        if (!changes.isEmpty()) {
            System.out.println("✅ 최근 변화: " + changes.size() + "개");

            // 변화 타입별 분류
            long created = changes.stream()
                    .filter(c -> c.getChangeType() == SubscriptionChange.ChangeType.CREATED)
                    .count();
            long changed = changes.stream()
                    .filter(c -> c.getChangeType() == SubscriptionChange.ChangeType.AMOUNT_CHANGED)
                    .count();

            System.out.println("  - 신규: " + created);
            System.out.println("  - 변경: " + changed);
        }
    }

    @Test
    @Order(8)
    @DisplayName("잘못된 파일 경로 - 예외 처리")
    void testAnalyzeWithInvalidPath() {
        // When & Then
        assertThrows(RuntimeException.class, () -> {
            manager.analyzeAndSave(
                    "/invalid/path/file.csv",
                    "invalid.csv",
                    true);
        });

        System.out.println("✅ 잘못된 경로 예외 처리 확인");
    }

    @Test
    @Order(9)
    @DisplayName("잘못된 limit 값 - 유효성 검사")
    void testInvalidLimit() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            manager.getRecentHistory(0);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            manager.getRecentHistory(101);
        });

        System.out.println("✅ limit 유효성 검사 확인");
    }

    @Test
    @Order(10)
    @DisplayName("이력 삭제")
    void testDeleteHistory() {
        // Given
        assertNotNull(testHistoryId);

        // When
        manager.deleteHistory(testHistoryId);

        // Then
        AnalysisHistory deleted = manager.getHistoryById(testHistoryId);
        assertNull(deleted, "삭제된 이력은 조회되지 않아야 함");

        System.out.println("✅ 이력 삭제 성공");
    }

    /**
     * 테스트용 CSV 파일 생성
     */
    private static Path createTestCsvFile() throws Exception {
        String csvContent = """
                거래일자,가맹점명,금액,카테고리,설명,카드번호
                2024-01-05,넷플릭스,17000,엔터테인먼트,월간구독,1234****5678
                2024-02-05,넷플릭스,17000,엔터테인먼트,월간구독,1234****5678
                2024-03-05,넷플릭스,17000,엔터테인먼트,월간구독,1234****5678
                2024-01-10,스포티파이,9900,엔터테인먼트,프리미엄,1234****5678
                2024-02-10,스포티파이,9900,엔터테인먼트,프리미엄,1234****5678
                2024-03-10,스포티파이,9900,엔터테인먼트,프리미엄,1234****5678
                2024-01-15,네이버플러스,4900,쇼핑,멤버십,1234****5678
                2024-02-15,네이버플러스,4900,쇼핑,멤버십,1234****5678
                2024-03-15,네이버플러스,4900,쇼핑,멤버십,1234****5678
                2024-01-25,스타벅스,5500,음료,일회성,1234****5678
                2024-02-10,CGV,13000,엔터테인먼트,일회성,1234****5678
                """;

        Path tempFile = Files.createTempFile("test_", ".csv");
        Files.writeString(tempFile, csvContent);
        return tempFile;
    }

    /**
     * 수정된 테스트용 CSV 파일 생성 (금액 변경)
     */
    private static Path createModifiedCsvFile() throws Exception {
        String csvContent = """
                거래일자,가맹점명,금액,카테고리,설명,카드번호
                2024-01-05,넷플릭스,19000,엔터테인먼트,월간구독,1234****5678
                2024-02-05,넷플릭스,19000,엔터테인먼트,월간구독,1234****5678
                2024-03-05,넷플릭스,19000,엔터테인먼트,월간구독,1234****5678
                2024-01-10,스포티파이,9900,엔터테인먼트,프리미엄,1234****5678
                2024-02-10,스포티파이,9900,엔터테인먼트,프리미엄,1234****5678
                2024-03-10,스포티파이,9900,엔터테인먼트,프리미엄,1234****5678
                2024-01-20,유튜브프리미엄,14900,엔터테인먼트,개인플랜,1234****5678
                2024-02-20,유튜브프리미엄,14900,엔터테인먼트,개인플랜,1234****5678
                2024-03-20,유튜브프리미엄,14900,엔터테인먼트,개인플랜,1234****5678
                """;

        Path tempFile = Files.createTempFile("test_modified_", ".csv");
        Files.writeString(tempFile, csvContent);
        return tempFile;
    }

    @AfterAll
    static void cleanup() throws Exception {
        // 임시 파일 삭제
        if (tempCsvFile != null && Files.exists(tempCsvFile)) {
            Files.deleteIfExists(tempCsvFile);
        }

        System.out.println("\n✅ 모든 테스트 완료");
    }
}