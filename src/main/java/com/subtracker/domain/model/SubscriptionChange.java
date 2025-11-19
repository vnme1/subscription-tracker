package com.subtracker.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 구독 변화를 추적하는 도메인 모델
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionChange {

    public enum ChangeType {
        CREATED("신규 구독"),
        AMOUNT_CHANGED("금액 변경"),
        STATUS_CHANGED("상태 변경"),
        CYCLE_CHANGED("주기 변경"),
        CANCELLED("구독 취소");

        private final String korean;

        ChangeType(String korean) {
            this.korean = korean;
        }

        public String getKorean() {
            return korean;
        }
    }

    private Long id;
    private String subscriptionId;
    private ChangeType changeType;
    private String oldValue;
    private String newValue;
    private LocalDateTime changeDate;
    private String notes;
}