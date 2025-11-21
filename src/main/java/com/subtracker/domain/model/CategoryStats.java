package com.subtracker.domain.model;

import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryStats {

    public enum SubscriptionCategory {
        ENTERTAINMENT("엔터테인먼트", "🎬"),
        MUSIC("음악", "🎵"),
        VIDEO("동영상", "📺"),
        SHOPPING("쇼핑", "🛒"),
        SOFTWARE("소프트웨어", "💻"),
        EDUCATION("교육", "📚"),
        FITNESS("운동/건강", "💪"),
        STORAGE("클라우드", "☁️"),
        NEWS("뉴스/잡지", "📰"),
        OTHER("기타", "📦");

        private final String korean;
        private final String emoji;

        SubscriptionCategory(String korean, String emoji) {
            this.korean = korean;
            this.emoji = emoji;
        }

        public String getKorean() {
            return korean;
        }

        public String getEmoji() {
            return emoji;
        }
    }

    private SubscriptionCategory category;
    private int count;
    private BigDecimal totalAmount;
    private double percentage;
    private String displayName;
}