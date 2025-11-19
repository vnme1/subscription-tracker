package com.subtracker.infrastructure.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 데이터베이스 연결 및 초기화 관리
 */
@Slf4j
public class DatabaseManager {

    private static final String DB_URL = "jdbc:h2:./data/subscriptions;AUTO_SERVER=TRUE";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";

    private static HikariDataSource dataSource;

    /**
     * 데이터베이스 초기화
     */
    public static void initialize() {
        if (dataSource != null) {
            return;
        }

        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(DB_URL);
            config.setUsername(DB_USER);
            config.setPassword(DB_PASSWORD);
            config.setMaximumPoolSize(10);
            config.setAutoCommit(true);

            dataSource = new HikariDataSource(config);
            createTables();

            log.info("데이터베이스 초기화 완료");
        } catch (Exception e) {
            log.error("데이터베이스 초기화 실패", e);
            throw new RuntimeException("데이터베이스 초기화 실패", e);
        }
    }

    /**
     * 데이터베이스 커넥션 획득
     */
    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            initialize();
        }
        return dataSource.getConnection();
    }

    /**
     * 데이터베이스 테이블 생성
     */
    private static void createTables() throws SQLException {
        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement()) {

            // 분석 이력 테이블
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS analysis_history (
                            id VARCHAR(36) PRIMARY KEY,
                            analysis_date TIMESTAMP NOT NULL,
                            file_name VARCHAR(500),
                            transaction_count INT NOT NULL,
                            subscription_count INT NOT NULL,
                            monthly_total DECIMAL(15,2),
                            annual_projection DECIMAL(15,2),
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                    """);

            // 구독 이력 테이블
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS subscription_history (
                            id VARCHAR(36) PRIMARY KEY,
                            analysis_id VARCHAR(36) NOT NULL,
                            subscription_id VARCHAR(36) NOT NULL,
                            service_name VARCHAR(200) NOT NULL,
                            monthly_amount DECIMAL(15,2),
                            billing_cycle VARCHAR(50),
                            status VARCHAR(50),
                            first_detected_date DATE,
                            last_charge_date DATE,
                            next_charge_date DATE,
                            transaction_count INT,
                            total_spent DECIMAL(15,2),
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            FOREIGN KEY (analysis_id) REFERENCES analysis_history(id)
                        )
                    """);

            // 구독 변화 추적 테이블
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS subscription_changes (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            subscription_id VARCHAR(36) NOT NULL,
                            change_type VARCHAR(50) NOT NULL,
                            old_value VARCHAR(500),
                            new_value VARCHAR(500),
                            change_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            notes TEXT
                        )
                    """);

            // 인덱스 생성
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_analysis_date ON analysis_history(analysis_date)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_subscription_service ON subscription_history(service_name)");
            stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_subscription_changes ON subscription_changes(subscription_id, change_date)");

            log.info("데이터베이스 테이블 생성 완료");
        }
    }

    /**
     * 데이터베이스 종료
     */
    public static void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("데이터베이스 연결 종료");
        }
    }
}