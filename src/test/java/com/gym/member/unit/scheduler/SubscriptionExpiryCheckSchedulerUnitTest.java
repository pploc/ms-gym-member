package com.gym.member.unit.scheduler;

import com.gym.member.application.scheduler.SubscriptionExpiryCheckScheduler;
import com.gym.member.application.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SubscriptionExpiryCheckSchedulerUnitTest {

    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private SubscriptionExpiryCheckScheduler scheduler;

    @Test
    void checkExpiredSubscriptions_executesService() {
        scheduler.checkExpiredSubscriptions();

        verify(subscriptionService, times(1)).processExpiredSubscriptions();
    }
}
