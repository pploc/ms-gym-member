package com.gym.member.unit.scheduler;

import com.gym.member.member.application.scheduler.SubscriptionExpiryCheckScheduler;
import com.gym.member.member.application.service.SubscriptionExpiryService;
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
    private SubscriptionExpiryService expiryService;

    @InjectMocks
    private SubscriptionExpiryCheckScheduler scheduler;

    @Test
    void givenScheduledTrigger_whenCheckExpiredSubscriptions_thenProcessesExpiredSubscriptions() {
        // Given - Scheduled trigger

        // When
        scheduler.checkExpiredSubscriptions();

        // Then
        verify(expiryService, times(1)).processExpiredSubscriptions();
    }
}
