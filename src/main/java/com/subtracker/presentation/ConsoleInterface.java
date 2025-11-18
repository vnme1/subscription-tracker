package com.subtracker.presentation;

import com.subtracker.domain.model.Subscription;
import com.subtracker.domain.model.SubscriptionSummary;
import com.subtracker.domain.model.Transaction;
import com.subtracker.domain.service.CsvParser;
import com.subtracker.domain.service.SubscriptionDetector;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.List;
import java.util.Scanner;

/**
 * 사용자와 상호작용하는 콘솔 인터페이스
 */
@Slf4j
public class ConsoleInterface {
    
    private final Scanner scanner;
    private final CsvParser csvParser;
    private final SubscriptionDetector detector;
    
    private List<Transaction> currentTransactions;
    private List<Subscription> currentSubscriptions;
    
    public ConsoleInterface() {
        this.scanner = new Scanner(System.in);
        this.csvParser = new CsvParser();
        this.detector = new SubscriptionDetector();
    }
    
    /**
     * 메인 실행 루프
     */
    public void run() {
        printWelcome();
        
        boolean running = true;
        while (running) {
            printMenu();
            
            try {
                int choice = Integer.parseInt(scanner.nextLine().trim());
                
                switch (choice) {
                    case 1 -> loadCsvFile();
                    case 2 -> analyzeSubscriptions();
                    case 3 -> showSummaryReport();
                    case 4 -> showDetailedSubscriptions();
                    case 5 -> exportReport();
                    case 0 -> {
                        running = false;
                        printGoodbye();
                    }
                    default -> System.out.println("⚠️ 잘못된 입력입니다. 다시 선택해주세요.");
                }
            } catch (NumberFormatException e) {
                System.out.println("⚠️ 숫자를 입력해주세요.");
            } catch (Exception e) {
                log.error("처리 중 오류 발생", e);
                System.out.println("❌ 오류가 발생했습니다: " + e.getMessage());
            }
            
            System.out.println();
        }
        
        scanner.close();
    }
    
    /**
     * 환영 메시지
     */
    private void printWelcome() {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("      🎯 구독 서비스 관리 도우미 v1.0");
        System.out.println("=".repeat(50));
        System.out.println("당신의 구독 서비스를 스마트하게 관리하세요!");
        System.out.println();
    }
    
    /**
     * 메뉴 출력
     */
    private void printMenu() {
        System.out.println("\n📋 메뉴를 선택하세요:");
        System.out.println("  1. CSV 파일 불러오기");
        System.out.println("  2. 구독 서비스 분석");
        System.out.println("  3. 요약 보고서 보기");
        System.out.println("  4. 상세 구독 목록 보기");
        System.out.println("  5. 보고서 내보내기");
        System.out.println("  0. 종료");
        System.out.print("\n선택: ");
    }
    
    /**
     * CSV 파일 로드
     */
    private void loadCsvFile() {
        System.out.print("\nCSV 파일 경로를 입력하세요: ");
        String filePath = scanner.nextLine().trim();
        
        // 파일 존재 여부 체크
        File file = new File(filePath);
        if (!file.exists()) {
            System.out.println("❌ 파일을 찾을 수 없습니다: " + filePath);
            return;
        }
        
        System.out.print("첫 줄이 헤더입니까? (y/n): ");
        boolean hasHeader = scanner.nextLine().trim().equalsIgnoreCase("y");
        
        System.out.println("📂 파일을 읽는 중...");
        currentTransactions = csvParser.parseTransactions(filePath, hasHeader);
        
        System.out.println("✅ " + currentTransactions.size() + "개의 거래 내역을 불러왔습니다.");
        
        // 간단한 통계 출력
        if (!currentTransactions.isEmpty()) {
            printTransactionStats();
        }
    }
    
    /**
     * 거래 내역 통계 출력
     */
    private void printTransactionStats() {
        System.out.println("\n📊 거래 내역 통계:");
        System.out.println("  • 총 거래 건수: " + currentTransactions.size() + "건");
        
        // 날짜 범위
        currentTransactions.stream()
            .map(Transaction::getTransactionDate)
            .min(java.time.LocalDate::compareTo)
            .ifPresent(minDate -> 
                System.out.println("  • 시작 날짜: " + minDate));
        
        currentTransactions.stream()
            .map(Transaction::getTransactionDate)
            .max(java.time.LocalDate::compareTo)
            .ifPresent(maxDate -> 
                System.out.println("  • 종료 날짜: " + maxDate));
        
        // 총 지출액
        var totalAmount = currentTransactions.stream()
            .map(Transaction::getAmount)
            .filter(amount -> amount.compareTo(java.math.BigDecimal.ZERO) > 0)
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        
        System.out.printf("  • 총 지출액: ₩%,.0f%n", totalAmount);
    }
    
    /**
     * 구독 서비스 분석
     */
    private void analyzeSubscriptions() {
        if (currentTransactions == null || currentTransactions.isEmpty()) {
            System.out.println("⚠️ 먼저 CSV 파일을 불러와주세요.");
            return;
        }
        
        System.out.println("\n🔍 구독 서비스를 분석하는 중...");
        currentSubscriptions = detector.detectSubscriptions(currentTransactions);
        
        if (currentSubscriptions.isEmpty()) {
            System.out.println("ℹ️ 감지된 구독 서비스가 없습니다.");
        } else {
            System.out.println("✅ " + currentSubscriptions.size() + "개의 구독 서비스를 발견했습니다!");
            
            // 간단한 목록 출력
            System.out.println("\n발견된 구독 서비스:");
            for (int i = 0; i < Math.min(5, currentSubscriptions.size()); i++) {
                Subscription sub = currentSubscriptions.get(i);
                System.out.printf("  • %s: ₩%,.0f/%s%n", 
                    sub.getServiceName(), 
                    sub.getMonthlyAmount(),
                    sub.getBillingCycle().getKorean());
            }
            
            if (currentSubscriptions.size() > 5) {
                System.out.println("  ... 외 " + (currentSubscriptions.size() - 5) + "개");
            }
        }
    }
    
    /**
     * 요약 보고서 표시
     */
    private void showSummaryReport() {
        if (currentSubscriptions == null || currentSubscriptions.isEmpty()) {
            System.out.println("⚠️ 먼저 구독 서비스 분석을 실행해주세요.");
            return;
        }
        
        SubscriptionSummary summary = SubscriptionSummary.from(currentSubscriptions);
        System.out.println(summary.generateReport());
    }
    
    /**
     * 상세 구독 목록 표시
     */
    private void showDetailedSubscriptions() {
        if (currentSubscriptions == null || currentSubscriptions.isEmpty()) {
            System.out.println("⚠️ 먼저 구독 서비스 분석을 실행해주세요.");
            return;
        }
        
        System.out.println("\n" + "=".repeat(70));
        System.out.println("                     상세 구독 서비스 목록");
        System.out.println("=".repeat(70));
        
        for (int i = 0; i < currentSubscriptions.size(); i++) {
            Subscription sub = currentSubscriptions.get(i);
            
            System.out.printf("\n%d. %s%n", i + 1, sub.getServiceName());
            System.out.println("-".repeat(40));
            System.out.printf("   상태: %s%n", sub.getStatus().getKorean());
            System.out.printf("   결제 주기: %s%n", sub.getBillingCycle().getKorean());
            System.out.printf("   월 금액: ₩%,.0f%n", sub.getMonthlyAmount());
            System.out.printf("   연간 예상: ₩%,.0f%n", sub.calculateAnnualCost());
            System.out.printf("   첫 결제: %s%n", sub.getFirstDetectedDate());
            System.out.printf("   최근 결제: %s%n", sub.getLastChargeDate());
            
            if (sub.getNextChargeDate() != null && sub.isActive()) {
                System.out.printf("   다음 결제 예정: %s%n", sub.getNextChargeDate());
            }
            
            System.out.printf("   총 결제 횟수: %d회%n", sub.getTransactionCount());
            System.out.printf("   총 지출액: ₩%,.0f%n", sub.getTotalSpent());
        }
        
        System.out.println("\n" + "=".repeat(70));
    }
    
    /**
     * 보고서 내보내기
     */
    private void exportReport() {
        if (currentSubscriptions == null || currentSubscriptions.isEmpty()) {
            System.out.println("⚠️ 먼저 구독 서비스 분석을 실행해주세요.");
            return;
        }
        
        System.out.print("\n저장할 파일명을 입력하세요 (예: report.txt): ");
        String filename = scanner.nextLine().trim();
        
        if (filename.isEmpty()) {
            filename = "subscription_report_" + java.time.LocalDate.now() + ".txt";
        }
        
        try {
            SubscriptionSummary summary = SubscriptionSummary.from(currentSubscriptions);
            String report = summary.generateReport();
            
            // 상세 내역 추가
            StringBuilder fullReport = new StringBuilder(report);
            fullReport.append("\n\n").append("=".repeat(70));
            fullReport.append("\n                     상세 구독 서비스 목록\n");
            fullReport.append("=".repeat(70)).append("\n");
            
            for (int i = 0; i < currentSubscriptions.size(); i++) {
                Subscription sub = currentSubscriptions.get(i);
                fullReport.append(String.format("\n%d. %s\n", i + 1, sub.getServiceName()));
                fullReport.append("-".repeat(40)).append("\n");
                fullReport.append(String.format("   상태: %s\n", sub.getStatus().getKorean()));
                fullReport.append(String.format("   결제 주기: %s\n", sub.getBillingCycle().getKorean()));
                fullReport.append(String.format("   월 금액: ₩%,.0f\n", sub.getMonthlyAmount()));
                fullReport.append(String.format("   총 지출액: ₩%,.0f\n", sub.getTotalSpent()));
            }
            
            // 파일 저장
            java.nio.file.Files.write(
                java.nio.file.Paths.get(filename),
                fullReport.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );
            
            System.out.println("✅ 보고서가 저장되었습니다: " + filename);
            
        } catch (Exception e) {
            log.error("보고서 저장 실패", e);
            System.out.println("❌ 보고서 저장 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    /**
     * 종료 메시지
     */
    private void printGoodbye() {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("        감사합니다! 또 만나요 👋");
        System.out.println("=".repeat(50));
    }
}