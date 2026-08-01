package com.gym.member.unit.scheduler;

import com.gym.member.application.scheduler.SubscriptionExpiryWarningScheduler;
import com.gym.member.application.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SubscriptionExpiryWarningSchedulerUnitTest {

    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private SubscriptionExpiryWarningScheduler scheduler;

    @Test
    void sendExpiryWarnings_executesService() {
        scheduler.sendExpiryWarnings();

        verify(subscriptionService, times(1)).processExpiringSoonWarnings();
    }
}
