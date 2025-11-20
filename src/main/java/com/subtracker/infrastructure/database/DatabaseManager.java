package com.subtracker.infrastructure.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 데이터베이스 연결 관리자 (수정 버전)
 * - 무한 재귀 제거
 * - 명확한 초기화 순서
 * - 안전한 연결 관리
 */
@Slf4j
public class DatabaseManager {

    private static final String DB_URL = "jdbc:h2:./data/subscriptions;AUTO_SERVER=TRUE;MODE=MySQL";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";

    private static final int MAX_POOL_SIZE = 10;
    private static final int MIN_IDLE = 2;
    private static final long CONNECTION_TIMEOUT = 30000;
    private static final long IDLE_TIMEOUT = 600000;
    private static final long MAX_LIFETIME = 1800000;

    private static volatile HikariDataSource dataSource;
    private static volatile boolean initialized = false;
    private static final Object LOCK = new Object();

    /**
     * 데이터베이스 초기화 (Thread-safe)
     */
    public static void initialize() {
        if (initialized) {
            return;
        }

        synchronized (LOCK) {
            if (initialized) {
                return;
            }

            try {
                log.info("데이터베이스 초기화 시작");

                // 1단계: HikariCP 설정 및 DataSource 생성
                HikariConfig config = createHikariConfig();
                dataSource = new HikariDataSource(config);

                // 2단계: 연결 테스트
                testConnection();

                // 3단계: 테이블 생성 (직접 연결 사용)
                createTablesInternal();

                initialized = true;
                log.info("데이터베이스 초기화 완료 (Pool: {})", MAX_POOL_SIZE);

            } catch (Exception e) {
                log.error("데이터베이스 초기화 실패", e);
                if (dataSource != null && !dataSource.isClosed()) {
                    dataSource.close();
                }
                throw new RuntimeException("데이터베이스 초기화 실패", e);
            }
        }
    }

    /**
     * HikariCP 설정 생성
     */
    private static HikariConfig createHikariConfig() {
        HikariConfig config = new HikariConfig();

        config.setJdbcUrl(DB_URL);
        config.setUsername(DB_USER);
        config.setPassword(DB_PASSWORD);

        config.setMaximumPoolSize(MAX_POOL_SIZE);
        config.setMinimumIdle(MIN_IDLE);
        config.setConnectionTimeout(CONNECTION_TIMEOUT);
        config.setIdleTimeout(IDLE_TIMEOUT);
        config.setMaxLifetime(MAX_LIFETIME);

        config.setAutoCommit(true);
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("SubTrackerPool");

        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        return config;
    }

    /**
     * 연결 테스트
     */
    private static void testConnection() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            if (!conn.isValid(5)) {
                throw new SQLException("데이터베이스 연결이 유효하지 않습니다");
            }
            log.debug("데이터베이스 연결 테스트 성공");
        }
    }

    /**
     * 테이블 생성 (내부 전용, getConnection 사용 안 함)
     */
    private static void createTablesInternal() throws SQLException {
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {

            // 분석 이력 테이블
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS analysis_history (
                            id VARCHAR(36) PRIMARY KEY,
                            analysis_date TIMESTAMP NOT NULL,
                            file_name VARCHAR(500),
                            transaction_count INT NOT NULL,
                            subscription_count INT NOT NULL,
                            monthly_total DECIMAL(15,2) DEFAULT 0,
                            annual_projection DECIMAL(15,2) DEFAULT 0,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                    """);

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_analysis_date ON analysis_history(analysis_date)");

            // 구독 이력 테이블
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS subscription_history (
                            id VARCHAR(36) PRIMARY KEY,
                            analysis_id VARCHAR(36) NOT NULL,
                            subscription_id VARCHAR(36) NOT NULL,
                            service_name VARCHAR(200) NOT NULL,
                            monthly_amount DECIMAL(15,2) DEFAULT 0,
                            billing_cycle VARCHAR(50) NOT NULL,
                            status VARCHAR(50) NOT NULL,
                            first_detected_date DATE,
                            last_charge_date DATE,
                            next_charge_date DATE,
                            transaction_count INT DEFAULT 0,
                            total_spent DECIMAL(15,2) DEFAULT 0,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            FOREIGN KEY (analysis_id) REFERENCES analysis_history(id) ON DELETE CASCADE
                        )
                    """);

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_service_name ON subscription_history(service_name)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_analysis_id ON subscription_history(analysis_id)");

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

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_subscription_id ON subscription_changes(subscription_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_change_date ON subscription_changes(change_date)");

            log.info("데이터베이스 테이블 생성 완료");

        } catch (SQLException e) {
            log.error("테이블 생성 실패", e);
            throw e;
        }
    }

    /**
     * 데이터베이스 연결 획득
     */
    public static Connection getConnection() throws SQLException {
        if (!initialized || dataSource == null) {
            throw new SQLException("데이터베이스가 초기화되지 않았습니다. initialize()를 먼저 호출하세요.");
        }

        Connection conn = dataSource.getConnection();

        if (conn == null || conn.isClosed()) {
            throw new SQLException("유효한 연결을 가져올 수 없습니다");
        }

        return conn;
    }

    /**
     * 데이터베이스 상태 확인
     */
    public static boolean isHealthy() {
        if (!initialized || dataSource == null || dataSource.isClosed()) {
            return false;
        }

        try (Connection conn = getConnection()) {
            return conn.isValid(2);
        } catch (SQLException e) {
            log.warn("데이터베이스 상태 확인 실패", e);
            return false;
        }
    }

    /**
     * 연결 풀 통계 정보
     */
    public static String getPoolStats() {
        if (dataSource == null) {
            return "DataSource not initialized";
        }

        return String.format(
                "Active: %d, Idle: %d, Total: %d, Waiting: %d",
                dataSource.getHikariPoolMXBean().getActiveConnections(),
                dataSource.getHikariPoolMXBean().getIdleConnections(),
                dataSource.getHikariPoolMXBean().getTotalConnections(),
                dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection());
    }

    /**
     * 데이터베이스 종료
     */
    public static synchronized void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            initialized = false;
            log.info("데이터베이스 연결 종료");
        }
    }

    /**
     * Private 생성자
     */
    private DatabaseManager() {
        throw new UnsupportedOperationException("Utility class");
    }
}