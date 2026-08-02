package com.gym.member.application.scheduler;

import com.gym.member.application.port.in.SubscriptionExpiryUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionExpiryWarningScheduler {

    private final SubscriptionExpiryUseCase expiryUseCase;

    @Scheduled(cron = "${app.member.cron.expiry-warning}")
    public void sendExpiryWarnings() {
        log.info("Starting scheduled 9 AM subscription 7-day expiry warning job");
        expiryUseCase.processExpiringSoonWarnings();
    }
}
