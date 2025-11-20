package com.subtracker.infrastructure.repository;

import com.subtracker.domain.model.Subscription;
import com.subtracker.infrastructure.database.DatabaseManager;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 구독 정보 데이터 접근 계층 (개선 버전)
 */
@Slf4j
public class SubscriptionRepository {

    /**
     * 구독 정보 저장 (예외 전파)
     */
    public void save(Subscription subscription, String analysisId) {
        String sql = """
                    INSERT INTO subscription_history
                    (id, analysis_id, subscription_id, service_name, monthly_amount,
                     billing_cycle, status, first_detected_date, last_charge_date,
                     next_charge_date, transaction_count, total_spent, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // subscription_id를 PK로 사용
            pstmt.setString(1, subscription.getSubscriptionId());
            pstmt.setString(2, analysisId);
            pstmt.setString(3, subscription.getSubscriptionId());
            pstmt.setString(4, subscription.getServiceName());
            pstmt.setBigDecimal(5, subscription.getMonthlyAmount());
            pstmt.setString(6, subscription.getBillingCycle().name());
            pstmt.setString(7, subscription.getStatus().name());
            pstmt.setDate(8,
                    subscription.getFirstDetectedDate() != null
                            ? Date.valueOf(subscription.getFirstDetectedDate())
                            : null);
            pstmt.setDate(9,
                    subscription.getLastChargeDate() != null
                            ? Date.valueOf(subscription.getLastChargeDate())
                            : null);
            pstmt.setDate(10,
                    subscription.getNextChargeDate() != null
                            ? Date.valueOf(subscription.getNextChargeDate())
                            : null);
            pstmt.setInt(11, subscription.getTransactionCount());
            pstmt.setBigDecimal(12, subscription.getTotalSpent());
            pstmt.setTimestamp(13, Timestamp.valueOf(java.time.LocalDateTime.now()));

            pstmt.executeUpdate();

            log.debug("구독 정보 저장 완료: {}", subscription.getServiceName());

        } catch (SQLException e) {
            log.error("구독 정보 저장 실패: {}", subscription.getServiceName(), e);
            throw new RuntimeException("구독 정보 저장 실패: " + subscription.getServiceName(), e);
        }
    }

    /**
     * 분석 ID로 구독 정보 조회
     */
    public List<Subscription> findByAnalysisId(String analysisId) {
        String sql = """
                    SELECT id, subscription_id, service_name, monthly_amount, billing_cycle,
                           status, first_detected_date, last_charge_date, next_charge_date,
                           transaction_count, total_spent
                    FROM subscription_history
                    WHERE analysis_id = ?
                    ORDER BY monthly_amount DESC
                """;

        List<Subscription> subscriptions = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, analysisId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Subscription subscription = mapResultSet(rs);
                    subscriptions.add(subscription);
                }
            }

            log.debug("{}개의 구독 정보 조회 완료 (analysisId: {})", subscriptions.size(), analysisId);

        } catch (SQLException e) {
            log.error("구독 정보 조회 실패 (analysisId: {})", analysisId, e);
        }

        return subscriptions;
    }

    /**
     * 특정 서비스의 구독 이력 조회
     */
    public List<Subscription> findByServiceName(String serviceName) {
        String sql = """
                    SELECT id, subscription_id, service_name, monthly_amount, billing_cycle,
                           status, first_detected_date, last_charge_date, next_charge_date,
                           transaction_count, total_spent
                    FROM subscription_history
                    WHERE service_name LIKE ?
                    ORDER BY created_at DESC
                    LIMIT 50
                """;

        List<Subscription> subscriptions = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, "%" + serviceName + "%");

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Subscription subscription = mapResultSet(rs);
                    subscriptions.add(subscription);
                }
            }

            log.debug("{}개의 구독 정보 조회 완료 (serviceName: {})", subscriptions.size(), serviceName);

        } catch (SQLException e) {
            log.error("서비스별 구독 조회 실패 (serviceName: {})", serviceName, e);
        }

        return subscriptions;
    }

    /**
     * 분석 ID로 구독 정보 삭제
     */
    public void deleteByAnalysisId(String analysisId) {
        String sql = "DELETE FROM subscription_history WHERE analysis_id = ?";

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, analysisId);
            int deleted = pstmt.executeUpdate();

            log.info("{}개의 구독 정보 삭제 완료 (analysisId: {})", deleted, analysisId);

        } catch (SQLException e) {
            log.error("구독 정보 삭제 실패 (analysisId: {})", analysisId, e);
            throw new RuntimeException("구독 정보 삭제 실패", e);
        }
    }

    /**
     * 전체 구독 수 조회
     */
    public int countAll() {
        String sql = "SELECT COUNT(*) FROM subscription_history";

        try (Connection conn = DatabaseManager.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt(1);
            }

        } catch (SQLException e) {
            log.error("구독 수 조회 실패", e);
        }

        return 0;
    }

    /**
     * 특정 분석의 구독 수 조회
     */
    public int countByAnalysisId(String analysisId) {
        String sql = "SELECT COUNT(*) FROM subscription_history WHERE analysis_id = ?";

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, analysisId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }

        } catch (SQLException e) {
            log.error("구독 수 조회 실패 (analysisId: {})", analysisId, e);
        }

        return 0;
    }

    /**
     * ResultSet을 Subscription 객체로 매핑
     */
    private Subscription mapResultSet(ResultSet rs) throws SQLException {
        Date firstDate = rs.getDate("first_detected_date");
        Date lastDate = rs.getDate("last_charge_date");
        Date nextDate = rs.getDate("next_charge_date");

        return Subscription.builder()
                .subscriptionId(rs.getString("subscription_id"))
                .serviceName(rs.getString("service_name"))
                .monthlyAmount(rs.getBigDecimal("monthly_amount"))
                .billingCycle(Subscription.BillingCycle.valueOf(rs.getString("billing_cycle")))
                .status(Subscription.SubscriptionStatus.valueOf(rs.getString("status")))
                .firstDetectedDate(firstDate != null ? firstDate.toLocalDate() : null)
                .lastChargeDate(lastDate != null ? lastDate.toLocalDate() : null)
                .nextChargeDate(nextDate != null ? nextDate.toLocalDate() : null)
                .transactionCount(rs.getInt("transaction_count"))
                .totalSpent(rs.getBigDecimal("total_spent"))
                .transactions(new ArrayList<>())
                .build();
    }
}