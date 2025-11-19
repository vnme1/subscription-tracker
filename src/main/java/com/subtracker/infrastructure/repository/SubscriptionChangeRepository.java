package com.subtracker.infrastructure.repository;

import com.subtracker.domain.model.SubscriptionChange;
import com.subtracker.infrastructure.database.DatabaseManager;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 구독 변화 추적 데이터 접근 계층
 */
@Slf4j
public class SubscriptionChangeRepository {

    /**
     * 구독 변화 기록
     */
    public void save(SubscriptionChange change) {
        String sql = """
                    INSERT INTO subscription_changes
                    (subscription_id, change_type, old_value, new_value, change_date, notes)
                    VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, change.getSubscriptionId());
            pstmt.setString(2, change.getChangeType().name());
            pstmt.setString(3, change.getOldValue());
            pstmt.setString(4, change.getNewValue());
            pstmt.setTimestamp(5, Timestamp.valueOf(change.getChangeDate()));
            pstmt.setString(6, change.getNotes());

            pstmt.executeUpdate();

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    change.setId(generatedKeys.getLong(1));
                }
            }

            log.info("구독 변화 기록: {} - {}", change.getSubscriptionId(), change.getChangeType());

        } catch (SQLException e) {
            log.error("구독 변화 기록 실패", e);
        }
    }

    /**
     * 구독 ID로 변화 이력 조회
     */
    public List<SubscriptionChange> findBySubscriptionId(String subscriptionId) {
        String sql = """
                    SELECT id, subscription_id, change_type, old_value, new_value, change_date, notes
                    FROM subscription_changes
                    WHERE subscription_id = ?
                    ORDER BY change_date DESC
                """;

        List<SubscriptionChange> changes = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, subscriptionId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    SubscriptionChange change = mapResultSet(rs);
                    changes.add(change);
                }
            }

        } catch (SQLException e) {
            log.error("구독 변화 이력 조회 실패", e);
        }

        return changes;
    }

    /**
     * 최근 변화 이력 조회
     */
    public List<SubscriptionChange> findRecent(int limit) {
        String sql = """
                    SELECT id, subscription_id, change_type, old_value, new_value, change_date, notes
                    FROM subscription_changes
                    ORDER BY change_date DESC
                    LIMIT ?
                """;

        List<SubscriptionChange> changes = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, limit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    SubscriptionChange change = mapResultSet(rs);
                    changes.add(change);
                }
            }

        } catch (SQLException e) {
            log.error("최근 변화 이력 조회 실패", e);
        }

        return changes;
    }

    /**
     * ResultSet을 SubscriptionChange 객체로 매핑
     */
    private SubscriptionChange mapResultSet(ResultSet rs) throws SQLException {
        return SubscriptionChange.builder()
                .id(rs.getLong("id"))
                .subscriptionId(rs.getString("subscription_id"))
                .changeType(SubscriptionChange.ChangeType.valueOf(rs.getString("change_type")))
                .oldValue(rs.getString("old_value"))
                .newValue(rs.getString("new_value"))
                .changeDate(rs.getTimestamp("change_date").toLocalDateTime())
                .notes(rs.getString("notes"))
                .build();
    }
}