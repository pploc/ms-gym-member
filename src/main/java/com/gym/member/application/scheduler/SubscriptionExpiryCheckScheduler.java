package com.gym.member.application.scheduler;

import com.gym.member.application.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionExpiryCheckScheduler {

    private final SubscriptionService subscriptionService;

    @Scheduled(cron = "0 0 6 * * *")
    public void checkExpiredSubscriptions() {
        log.info("Starting scheduled 6 AM subscription expiry check");
        subscriptionService.processExpiredSubscriptions();
    }
}
