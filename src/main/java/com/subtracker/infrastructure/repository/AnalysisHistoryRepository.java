package com.subtracker.infrastructure.repository;

import com.subtracker.domain.model.AnalysisHistory;
import com.subtracker.domain.model.Subscription;
import com.subtracker.infrastructure.database.DatabaseManager;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 분석 이력 데이터 접근 계층 (수정: 순환 참조 제거)
 */
@Slf4j
public class AnalysisHistoryRepository {

    /**
     * 분석 이력 저장
     */
    public void save(AnalysisHistory history) {
        String sql = """
                    INSERT INTO analysis_history
                    (id, analysis_date, file_name, transaction_count, subscription_count,
                     monthly_total, annual_projection, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, history.getId());
            pstmt.setTimestamp(2, Timestamp.valueOf(history.getAnalysisDate()));
            pstmt.setString(3, history.getFileName());
            pstmt.setInt(4, history.getTransactionCount());
            pstmt.setInt(5, history.getSubscriptionCount());
            pstmt.setBigDecimal(6, history.getMonthlyTotal());
            pstmt.setBigDecimal(7, history.getAnnualProjection());
            pstmt.setTimestamp(8, Timestamp.valueOf(history.getCreatedAt()));

            pstmt.executeUpdate();

            // ✅ 구독 정보는 외부에서 저장하도록 변경
            // 순환 참조 방지
            SubscriptionRepository subscriptionRepo = new SubscriptionRepository();
            for (Subscription subscription : history.getSubscriptions()) {
                subscriptionRepo.save(subscription, history.getId());
            }

            log.info("분석 이력 저장 완료: {}", history.getId());

        } catch (SQLException e) {
            log.error("분석 이력 저장 실패", e);
            throw new RuntimeException("분석 이력 저장 실패", e);
        }
    }

    /**
     * 모든 분석 이력 조회
     */
    public List<AnalysisHistory> findAll() {
        String sql = """
                    SELECT id, analysis_date, file_name, transaction_count, subscription_count,
                           monthly_total, annual_projection, created_at
                    FROM analysis_history
                    ORDER BY analysis_date DESC
                """;

        List<AnalysisHistory> histories = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                AnalysisHistory history = mapResultSet(rs);
                histories.add(history);
            }

        } catch (SQLException e) {
            log.error("분석 이력 조회 실패", e);
        }

        return histories;
    }

    /**
     * ID로 분석 이력 조회
     */
    public Optional<AnalysisHistory> findById(String id) {
        String sql = """
                    SELECT id, analysis_date, file_name, transaction_count, subscription_count,
                           monthly_total, annual_projection, created_at
                    FROM analysis_history
                    WHERE id = ?
                """;

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, id);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    AnalysisHistory history = mapResultSet(rs);
                    // 관련 구독 정보 로드
                    SubscriptionRepository subscriptionRepo = new SubscriptionRepository();
                    List<Subscription> subscriptions = subscriptionRepo.findByAnalysisId(id);
                    history.setSubscriptions(subscriptions);
                    return Optional.of(history);
                }
            }

        } catch (SQLException e) {
            log.error("분석 이력 조회 실패", e);
        }

        return Optional.empty();
    }

    /**
     * 최근 N개 분석 이력 조회
     */
    public List<AnalysisHistory> findRecent(int limit) {
        String sql = """
                    SELECT id, analysis_date, file_name, transaction_count, subscription_count,
                           monthly_total, annual_projection, created_at
                    FROM analysis_history
                    ORDER BY analysis_date DESC
                    LIMIT ?
                """;

        List<AnalysisHistory> histories = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, limit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    AnalysisHistory history = mapResultSet(rs);
                    // 구독 정보도 함께 로드
                    SubscriptionRepository subscriptionRepo = new SubscriptionRepository();
                    List<Subscription> subscriptions = subscriptionRepo.findByAnalysisId(history.getId());
                    history.setSubscriptions(subscriptions);
                    histories.add(history);
                }
            }

        } catch (SQLException e) {
            log.error("최근 분석 이력 조회 실패", e);
        }

        return histories;
    }

    /**
     * 기간별 분석 이력 조회
     */
    public List<AnalysisHistory> findByDateRange(LocalDate startDate, LocalDate endDate) {
        String sql = """
                    SELECT id, analysis_date, file_name, transaction_count, subscription_count,
                           monthly_total, annual_projection, created_at
                    FROM analysis_history
                    WHERE DATE(analysis_date) BETWEEN ? AND ?
                    ORDER BY analysis_date DESC
                """;

        List<AnalysisHistory> histories = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDate(1, Date.valueOf(startDate));
            pstmt.setDate(2, Date.valueOf(endDate));

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    AnalysisHistory history = mapResultSet(rs);
                    histories.add(history);
                }
            }

        } catch (SQLException e) {
            log.error("기간별 분석 이력 조회 실패", e);
        }

        return histories;
    }

    /**
     * 분석 이력 삭제
     */
    public void delete(String id) {
        String sql = "DELETE FROM analysis_history WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // 구독 정보 먼저 삭제 (CASCADE로 자동 삭제되지만 명시적으로)
            SubscriptionRepository subscriptionRepo = new SubscriptionRepository();
            subscriptionRepo.deleteByAnalysisId(id);

            pstmt.setString(1, id);
            pstmt.executeUpdate();

            log.info("분석 이력 삭제 완료: {}", id);

        } catch (SQLException e) {
            log.error("분석 이력 삭제 실패", e);
            throw new RuntimeException("분석 이력 삭제 실패", e);
        }
    }

    /**
     * ResultSet을 AnalysisHistory 객체로 매핑
     */
    private AnalysisHistory mapResultSet(ResultSet rs) throws SQLException {
        return AnalysisHistory.builder()
                .id(rs.getString("id"))
                .analysisDate(rs.getTimestamp("analysis_date").toLocalDateTime())
                .fileName(rs.getString("file_name"))
                .transactionCount(rs.getInt("transaction_count"))
                .subscriptionCount(rs.getInt("subscription_count"))
                .monthlyTotal(rs.getBigDecimal("monthly_total"))
                .annualProjection(rs.getBigDecimal("annual_projection"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .subscriptions(new ArrayList<>()) // ✅ 빈 리스트로 초기화
                .build();
    }
}