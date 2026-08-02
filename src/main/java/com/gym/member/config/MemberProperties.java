package com.gym.member.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.member")
public record MemberProperties(
        CronProperties cron,
        SubscriptionProperties subscription,
        OutboxProperties outbox,
        PaymentProperties payment,
        boolean requireEventId
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

    public record OutboxProperties(
            int batchSize,
            Duration pollDelay,
            Duration retryDelay,
            int maxAttempts,
            Duration leaseDuration
    ) {}

    public record PaymentProperties(
            String target,
            Duration deadline
    ) {}
}
