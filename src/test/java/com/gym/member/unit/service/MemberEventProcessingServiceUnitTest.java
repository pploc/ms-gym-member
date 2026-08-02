package com.gym.member.unit.service;

import com.gym.member.member.application.port.in.MemberSuspensionUseCase;
import com.gym.member.member.application.port.in.MemberUseCase;
import com.gym.member.member.application.service.MemberEventProcessingService;
import com.gym.member.member.application.service.strategy.MembershipPaymentHandler;
import com.gym.member.member.application.service.strategy.PaymentTypeHandler;
import com.gym.member.shared.idempotency.service.IdempotencyService;
import com.gym.proto.events.v1.PaymentCompletedEvent;
import com.gym.proto.events.v1.UserRegisteredEvent;
import com.gym.proto.events.v1.UserSuspendedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberEventProcessingServiceUnitTest {

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private MemberUseCase memberUseCase;

    @Mock
    private MemberSuspensionUseCase memberSuspensionUseCase;

    @Mock
    private MembershipPaymentHandler membershipPaymentHandler;

    private MemberEventProcessingService service;
    private String eventId;

    @BeforeEach
    void setUp() {
        List<PaymentTypeHandler> handlers = List.of(membershipPaymentHandler);
        service = new MemberEventProcessingService(idempotencyService, memberUseCase, memberSuspensionUseCase, handlers);
        eventId = UUID.randomUUID().toString();
    }

    @Test
    void givenDuplicateEvent_whenProcessUserRegistered_thenReturnsDuplicateResult() {
        when(idempotencyService.claimEvent(eventId, "user.registered")).thenReturn(false);

        UserRegisteredEvent event = UserRegisteredEvent.newBuilder()
                .setUserId("user-123")
                .setFullName("John Doe")
                .setGymId("gym-1")
                .build();

        MemberEventProcessingService.EventProcessingResult result = service.processUserRegistered(eventId, "user.registered", event);

        assertEquals(MemberEventProcessingService.EventProcessingResult.DUPLICATE, result);
        verify(memberUseCase, never()).createMemberShell(anyString(), anyString(), anyString());
    }

    @Test
    void givenBlankUserId_whenProcessUserRegistered_thenThrowsIllegalArgumentException() {
        when(idempotencyService.claimEvent(eventId, "user.registered")).thenReturn(true);

        UserRegisteredEvent event = UserRegisteredEvent.newBuilder()
                .setUserId("")
                .setFullName("John Doe")
                .setGymId("gym-1")
                .build();

        assertThrows(IllegalArgumentException.class, () -> service.processUserRegistered(eventId, "user.registered", event));
    }

    @Test
    void givenUnsupportedPaymentType_whenProcessPaymentCompleted_thenReturnsSkippedResult() {
        when(idempotencyService.claimEvent(eventId, "payment.completed")).thenReturn(true);
        when(membershipPaymentHandler.supports("MERCHANDISE")).thenReturn(false);

        PaymentCompletedEvent event = PaymentCompletedEvent.newBuilder()
                .setPaymentId("pay-1")
                .setType("MERCHANDISE")
                .build();

        MemberEventProcessingService.EventProcessingResult result = service.processPaymentCompleted(eventId, "payment.completed", event, "user-123");

        assertEquals(MemberEventProcessingService.EventProcessingResult.SKIPPED, result);
        verify(membershipPaymentHandler, never()).handle(any(), anyString());
    }

    @Test
    void givenSupportedPaymentType_whenProcessPaymentCompleted_thenInvokesStrategyAndReturnsProcessed() {
        when(idempotencyService.claimEvent(eventId, "payment.completed")).thenReturn(true);
        when(membershipPaymentHandler.supports("MEMBERSHIP")).thenReturn(true);

        PaymentCompletedEvent event = PaymentCompletedEvent.newBuilder()
                .setPaymentId("pay-1")
                .setType("MEMBERSHIP")
                .setUserId("user-123")
                .setReferenceId("plan-1")
                .build();

        MemberEventProcessingService.EventProcessingResult result = service.processPaymentCompleted(eventId, "payment.completed", event, "user-123");

        assertEquals(MemberEventProcessingService.EventProcessingResult.PROCESSED, result);
        verify(membershipPaymentHandler, times(1)).handle(event, "user-123");
    }

    @Test
    void givenNullPaymentEvent_whenProcessPaymentCompleted_thenThrowsIllegalArgumentException() {
        when(idempotencyService.claimEvent(eventId, "payment.completed")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.processPaymentCompleted(eventId, "payment.completed", null, "user-123"));
    }

    @Test
    void givenValidUserSuspendedEvent_whenProcessUserSuspended_thenInvokesSuspensionUseCase() {
        when(idempotencyService.claimEvent(eventId, "user.suspended")).thenReturn(true);

        UserSuspendedEvent event = UserSuspendedEvent.newBuilder()
                .setUserId("user-123")
                .build();

        MemberEventProcessingService.EventProcessingResult result = service.processUserSuspended(eventId, "user.suspended", event);

        assertEquals(MemberEventProcessingService.EventProcessingResult.PROCESSED, result);
        verify(memberSuspensionUseCase, times(1)).suspendMemberAndSubscription("user-123");
    }
}
