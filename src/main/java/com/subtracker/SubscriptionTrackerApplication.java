package com.subtracker;

import com.subtracker.presentation.ConsoleInterface;
import com.subtracker.presentation.WebServer;
import lombok.extern.slf4j.Slf4j;

import java.util.Scanner;

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
            // 실행 모드 선택
            System.out.println("\n" + "=".repeat(50));
            System.out.println("      🎯 구독 서비스 관리 도우미 v1.0");
            System.out.println("=".repeat(50));
            System.out.println("\n실행 모드를 선택하세요:");
            System.out.println("  1. 웹 인터페이스 (권장) 🌐");
            System.out.println("  2. 콘솔 인터페이스");
            System.out.print("\n선택 (1 또는 2): ");

            Scanner scanner = new Scanner(System.in);
            String choice = scanner.nextLine().trim();

            if ("1".equals(choice) || choice.isEmpty()) {
                // 웹 서버 실행
                log.info("웹 서버 모드로 시작");
                WebServer server = new WebServer();
                server.start();

                System.out.println("\n서버를 종료하려면 Ctrl+C를 누르세요.");

                // 서버가 종료되지 않도록 대기
                Thread.currentThread().join();

            } else if ("2".equals(choice)) {
                // 콘솔 인터페이스 실행
                log.info("콘솔 모드로 시작");
                ConsoleInterface console = new ConsoleInterface();
                console.run();

            } else {
                System.out.println("잘못된 선택입니다. 웹 인터페이스로 시작합니다.");
                WebServer server = new WebServer();
                server.start();
            }

            scanner.close();
            log.info("구독 서비스 관리 도우미 종료");

        } catch (Exception e) {
            log.error("애플리케이션 실행 중 오류 발생", e);
            System.err.println("치명적 오류가 발생했습니다: " + e.getMessage());
            System.exit(1);
        }
    }
}