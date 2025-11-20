package com.subtracker.domain.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import com.subtracker.domain.model.Transaction;
import lombok.extern.slf4j.Slf4j;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CSV 파일을 파싱하여 Transaction 객체로 변환하는 서비스
 * UTF-8 인코딩 지원
 */
@Slf4j
public class CsvParser {

    // 다양한 날짜 포맷 지원
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ofPattern("yyyyMMdd"));

    /**
     * CSV 파일을 읽어서 Transaction 리스트로 변환
     * ✅ UTF-8 인코딩으로 읽기
     * 
     * @param filePath  CSV 파일 경로
     * @param hasHeader 첫 줄이 헤더인지 여부
     * @return Transaction 리스트
     */
    public List<Transaction> parseTransactions(String filePath, boolean hasHeader) {
        List<Transaction> transactions = new ArrayList<>();

        // ✅ UTF-8 인코딩 명시
        try (FileInputStream fis = new FileInputStream(filePath);
                InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
                CSVReader reader = new CSVReader(isr)) {

            List<String[]> records = reader.readAll();

            // 헤더가 있으면 첫 줄 스킵
            int startIndex = hasHeader ? 1 : 0;

            for (int i = startIndex; i < records.size(); i++) {
                try {
                    Transaction transaction = parseRecord(records.get(i), i);
                    if (transaction != null) {
                        transactions.add(transaction);
                    }
                } catch (Exception e) {
                    log.warn("레코드 파싱 실패 (줄 {}): {}", i + 1, e.getMessage());
                }
            }

            log.info("총 {} 건의 거래 내역을 파싱했습니다.", transactions.size());

        } catch (IOException | CsvException e) {
            log.error("CSV 파일 읽기 실패: {}", e.getMessage());
            throw new RuntimeException("CSV 파일 처리 중 오류 발생", e);
        }

        return transactions;
    }

    /**
     * CSV 레코드 하나를 Transaction 객체로 변환
     * 일반적인 은행/카드사 CSV 포맷 가정:
     * [거래일자, 가맹점명, 거래금액, 카테고리(옵션), 설명(옵션), 카드번호(옵션)]
     */
    private Transaction parseRecord(String[] record, int lineNumber) {
        if (record.length < 3) {
            log.warn("레코드 필드 부족 (줄 {}): 최소 3개 필드 필요", lineNumber + 1);
            return null;
        }

        try {
            Transaction.TransactionBuilder builder = Transaction.builder()
                    .transactionId(UUID.randomUUID().toString())
                    .transactionDate(parseDate(record[0].trim()))
                    .merchant(cleanMerchantName(record[1].trim()))
                    .amount(parseAmount(record[2].trim()));

            // 선택적 필드 처리
            if (record.length > 3 && !record[3].trim().isEmpty()) {
                builder.category(record[3].trim());
            }
            if (record.length > 4 && !record[4].trim().isEmpty()) {
                builder.description(record[4].trim());
            }
            if (record.length > 5 && !record[5].trim().isEmpty()) {
                builder.cardNumber(maskCardNumber(record[5].trim()));
            }

            return builder.build();

        } catch (Exception e) {
            log.error("레코드 변환 실패 (줄 {}): {}", lineNumber + 1, e.getMessage());
            return null;
        }
    }

    /**
     * 다양한 날짜 형식 파싱
     */
    private LocalDate parseDate(String dateStr) {
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(dateStr, formatter);
            } catch (DateTimeParseException e) {
                // 다음 포맷 시도
            }
        }
        throw new IllegalArgumentException("날짜 파싱 실패: " + dateStr);
    }

    /**
     * 금액 파싱 (콤마, 원화 기호 등 제거)
     */
    private BigDecimal parseAmount(String amountStr) {
        // 숫자와 소수점만 남기고 모두 제거
        String cleanAmount = amountStr.replaceAll("[^0-9.-]", "");

        // 음수 처리 (출금/환불)
        if (cleanAmount.startsWith("-") || amountStr.contains("환불") || amountStr.contains("취소")) {
            cleanAmount = "-" + cleanAmount.replaceAll("-", "");
        }

        try {
            return new BigDecimal(cleanAmount);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("금액 파싱 실패: " + amountStr);
        }
    }

    /**
     * 가맹점명 정제 (불필요한 문자 제거)
     */
    private String cleanMerchantName(String merchant) {
        // 특수문자 제거하고 정규화
        return merchant
                .replaceAll("\\*+", "") // 별표 제거
                .replaceAll("\\s+", " ") // 연속 공백을 하나로
                .trim();
    }

    /**
     * 카드번호 마스킹
     */
    private String maskCardNumber(String cardNumber) {
        // 숫자만 추출
        String digits = cardNumber.replaceAll("[^0-9]", "");

        if (digits.length() < 8) {
            return cardNumber; // 너무 짧으면 그대로 반환
        }

        // 앞 4자리와 뒤 4자리만 보여주고 나머지는 마스킹
        String prefix = digits.substring(0, 4);
        String suffix = digits.substring(digits.length() - 4);

        return prefix + "-****-****-" + suffix;
    }
}