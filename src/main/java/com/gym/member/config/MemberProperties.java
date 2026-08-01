package com.gym.member.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.member")
public record MemberProperties(
        CronProperties cron,
        SubscriptionProperties subscription
) {
    public record CronProperties(
            String qrRotation,
            String expiryCheck,
            String expiryWarning
    ) {}

    public record SubscriptionProperties(
            int maxPauseCount,
            int warningNoticeDays,
            int defaultDurationDays
    ) {}
}
