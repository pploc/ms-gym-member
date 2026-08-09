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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void given_duplicate_event_when_process_user_registered_then_returns_duplicate_result() {
        // given
        when(idempotencyService.claimEvent(eventId, "user.registered")).thenReturn(false);
        UserRegisteredEvent event = UserRegisteredEvent.newBuilder()
                .setUserId("user-123")
                .setFullName("John Doe")
                .build();

        // when
        MemberEventProcessingService.EventProcessingResult result =
                service.processUserRegistered(eventId, "user.registered", event);

        // then
        assertEquals(MemberEventProcessingService.EventProcessingResult.DUPLICATE, result);
        verify(memberUseCase, never()).createMemberShell(anyString(), anyString());
    }

    @Test
    void given_blank_user_id_when_process_user_registered_then_throws_and_does_not_release_claim() {
        // given
        when(idempotencyService.claimEvent(eventId, "user.registered")).thenReturn(true);
        UserRegisteredEvent event = UserRegisteredEvent.newBuilder()
                .setUserId("")
                .setFullName("John Doe")
                .build();

        // when / then
        assertThrows(
                IllegalArgumentException.class,
                () -> service.processUserRegistered(eventId, "user.registered", event));
        // claim stays in same TX; rollback on exception reopens redelivery path
        verify(memberUseCase, never()).createMemberShell(anyString(), anyString());
    }

    @Test
    void given_unsupported_payment_type_when_process_payment_completed_then_returns_skipped_result() {
        // given
        when(idempotencyService.claimEvent(eventId, "payment.completed")).thenReturn(true);
        when(membershipPaymentHandler.supports(com.gym.proto.common.v1.PaymentType.PAYMENT_TYPE_TRAINER_BOOKING))
                .thenReturn(false);
        PaymentCompletedEvent event = PaymentCompletedEvent.newBuilder()
                .setPaymentId("pay-1")
                .setType(com.gym.proto.common.v1.PaymentType.PAYMENT_TYPE_TRAINER_BOOKING)
                .build();

        // when
        MemberEventProcessingService.EventProcessingResult result =
                service.processPaymentCompleted(eventId, "payment.completed", event, "user-123");

        // then
        assertEquals(MemberEventProcessingService.EventProcessingResult.SKIPPED, result);
        verify(membershipPaymentHandler, never()).handle(any(), anyString());
    }

    @Test
    void given_supported_payment_type_when_process_payment_completed_then_invokes_strategy_and_returns_processed() {
        // given
        when(idempotencyService.claimEvent(eventId, "payment.completed")).thenReturn(true);
        when(membershipPaymentHandler.supports(com.gym.proto.common.v1.PaymentType.PAYMENT_TYPE_MEMBERSHIP))
                .thenReturn(true);
        PaymentCompletedEvent event = PaymentCompletedEvent.newBuilder()
                .setPaymentId("pay-1")
                .setType(com.gym.proto.common.v1.PaymentType.PAYMENT_TYPE_MEMBERSHIP)
                .setUserId("user-123")
                .setReferenceId("plan-1")
                .build();

        // when
        MemberEventProcessingService.EventProcessingResult result =
                service.processPaymentCompleted(eventId, "payment.completed", event, "user-123");

        // then
        assertEquals(MemberEventProcessingService.EventProcessingResult.PROCESSED, result);
        verify(membershipPaymentHandler, times(1)).handle(event, "user-123");
    }

    @Test
    void given_null_payment_event_when_process_payment_completed_then_throws_illegal_argument_exception() {
        // given
        when(idempotencyService.claimEvent(eventId, "payment.completed")).thenReturn(true);

        // when / then
        assertThrows(
                IllegalArgumentException.class,
                () -> service.processPaymentCompleted(eventId, "payment.completed", null, "user-123"));
    }

    @Test
    void given_valid_user_suspended_event_when_process_user_suspended_then_invokes_suspension_use_case() {
        // given
        when(idempotencyService.claimEvent(eventId, "user.suspended")).thenReturn(true);
        UserSuspendedEvent event = UserSuspendedEvent.newBuilder()
                .setUserId("user-123")
                .build();

        // when
        MemberEventProcessingService.EventProcessingResult result =
                service.processUserSuspended(eventId, "user.suspended", event);

        // then
        assertEquals(MemberEventProcessingService.EventProcessingResult.PROCESSED, result);
        verify(memberSuspensionUseCase, times(1)).suspendMemberAndSubscription("user-123");
    }
}
