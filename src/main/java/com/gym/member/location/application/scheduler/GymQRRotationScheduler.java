package com.gym.member.location.application.scheduler;

import com.gym.member.location.application.service.GymQRService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GymQRRotationScheduler {

    private final GymQRService gymQRService;

    @Scheduled(cron = "${app.member.cron.qr-rotation}")
    public void rotateSecrets() {
        log.info("Starting scheduled midnight Gym QR token secret rotation");
        gymQRService.rotateAllGymDailySecrets();
    }
}
