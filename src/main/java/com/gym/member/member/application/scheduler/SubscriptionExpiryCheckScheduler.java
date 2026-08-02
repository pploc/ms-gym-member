package com.gym.member.member.application.scheduler;

import com.gym.member.member.application.port.in.SubscriptionExpiryCheckUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionExpiryCheckScheduler {

    private final SubscriptionExpiryCheckUseCase expiryUseCase;

    @Scheduled(cron = "${app.member.cron.expiry-check}")
    public void checkExpiredSubscriptions() {
        log.info("Starting scheduled 6 AM subscription expiry check");
        expiryUseCase.processExpiredSubscriptions();
    }
}
