package com.gym.member.unit.scheduler;

import com.gym.member.application.scheduler.SubscriptionExpiryWarningScheduler;
import com.gym.member.application.service.SubscriptionExpiryService;
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
    private SubscriptionExpiryService expiryService;

    @InjectMocks
    private SubscriptionExpiryWarningScheduler scheduler;

    @Test
    void givenScheduledWarningTrigger_whenSendExpiryWarnings_thenProcessesExpiringSoonWarnings() {
        // Given - Scheduled warning trigger

        // When
        scheduler.sendExpiryWarnings();

        // Then
        verify(expiryService, times(1)).processExpiringSoonWarnings();
    }
}
