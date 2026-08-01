package com.gym.member.application.scheduler;

import com.gym.member.application.service.GymQRService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GymQRRotationSchedulerTest {

    @Mock
    private GymQRService gymQRService;

    @InjectMocks
    private GymQRRotationScheduler scheduler;

    @Test
    void rotateSecrets_triggersService() {
        scheduler.rotateSecrets();

        verify(gymQRService, times(1)).rotateAllGymDailySecrets();
    }
}
