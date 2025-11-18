package com.subtracker;

import com.subtracker.presentation.ConsoleInterface;
import lombok.extern.slf4j.Slf4j;

/**
 * 구독 서비스 관리 도우미 애플리케이션
 * 
 * @author Subscription Tracker
 * @version 1.0.0
 */
@Slf4j
public class SubscriptionTrackerApplication {
    
    public static void main(String[] args) {
        try {
            log.info("구독 서비스 관리 도우미 시작");
            
            // 콘솔 인터페이스 실행
            ConsoleInterface console = new ConsoleInterface();
            console.run();
            
            log.info("구독 서비스 관리 도우미 종료");
            
        } catch (Exception e) {
            log.error("애플리케이션 실행 중 오류 발생", e);
            System.err.println("치명적 오류가 발생했습니다: " + e.getMessage());
            System.exit(1);
        }
    }
}