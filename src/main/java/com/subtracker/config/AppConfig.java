package com.subtracker.config;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 애플리케이션 설정 관리
 */
@Slf4j
public class AppConfig {

    private static final Properties properties = new Properties();

    static {
        try (InputStream input = AppConfig.class
                .getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (input != null) {
                properties.load(input);
                log.info("설정 파일 로드 완료");
            } else {
                log.warn("application.properties 파일을 찾을 수 없습니다. 기본값을 사용합니다.");
            }
        } catch (IOException e) {
            log.error("설정 파일 로드 실패", e);
        }
    }

    // Database
    public static String getDbUrl() {
        return get("db.url", "jdbc:h2:./data/subscriptions;AUTO_SERVER=TRUE;MODE=MySQL");
    }

    public static String getDbUser() {
        return get("db.user", "sa");
    }

    public static String getDbPassword() {
        return get("db.password", "");
    }

    public static int getDbPoolMax() {
        return getInt("db.pool.max", 10);
    }

    public static int getDbPoolMin() {
        return getInt("db.pool.min", 2);
    }

    // Server
    public static int getServerPort() {
        return getInt("server.port", 8080);
    }

    public static long getUploadMaxSize() {
        return getLong("server.upload.max-size", 10485760L); // 10MB
    }

    // Subscription Detection
    public static int getSubscriptionMinOccurrence() {
        return getInt("subscription.min-occurrence", 2);
    }

    public static double getSubscriptionAmountTolerance() {
        return getDouble("subscription.amount-tolerance", 5.0);
    }

    public static int getSubscriptionMaxDayVariance() {
        return getInt("subscription.max-day-variance", 5);
    }

    // File Upload
    public static String getUploadTempDir() {
        return get("upload.temp-dir", "/temp");
    }

    public static String[] getUploadAllowedExtensions() {
        String extensions = get("upload.allowed-extensions", "csv,CSV");
        return extensions.split(",");
    }

    // Helper methods
    public static String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public static int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            log.warn("잘못된 정수 값: {} = {}", key, properties.getProperty(key));
            return defaultValue;
        }
    }

    public static long getLong(String key, long defaultValue) {
        try {
            return Long.parseLong(properties.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            log.warn("잘못된 long 값: {} = {}", key, properties.getProperty(key));
            return defaultValue;
        }
    }

    public static double getDouble(String key, double defaultValue) {
        try {
            return Double.parseDouble(properties.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            log.warn("잘못된 double 값: {} = {}", key, properties.getProperty(key));
            return defaultValue;
        }
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(properties.getProperty(key, String.valueOf(defaultValue)));
    }

    private AppConfig() {
        throw new UnsupportedOperationException("Utility class");
    }
}