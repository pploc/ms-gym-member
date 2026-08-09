package com.gym.member.unit.grpc;

import com.gym.common.error.NotFoundException;
import com.gym.common.grpc.security.GrpcSecurityContext;
import com.gym.common.grpc.security.UserClaims;
import com.gym.common.pagination.NormalPage;
import com.gym.member.member.adapter.in.grpc.MemberGrpcDelegate;
import com.gym.member.member.adapter.in.grpc.MemberGrpcHandler;
import com.gym.member.member.adapter.in.grpc.SubscriptionGrpcDelegate;
import com.gym.member.member.adapter.out.persistence.mapper.MemberMapper;
import com.gym.member.member.adapter.out.persistence.mapper.SubscriptionMapper;
import com.gym.member.member.application.port.in.MembershipPurchaseUseCase;
import com.gym.member.member.application.port.in.SubscriptionLifecycleUseCase;
import com.gym.member.member.application.service.MemberService;
import com.gym.member.member.domain.dto.MemberDto;
import com.gym.member.member.domain.dto.SubscriptionDto;
import com.gym.member.member.domain.exception.CannotPauseLifetimeException;
import com.gym.member.member.domain.model.MembershipStatus;
import com.gym.proto.member.v1.GetMemberRequest;
import com.gym.proto.member.v1.GetMembershipStatusRequest;
import com.gym.proto.member.v1.ListMembersByStatusRequest;
import com.gym.proto.member.v1.ListMembersByStatusResponse;
import com.gym.proto.member.v1.ListMembersRequest;
import com.gym.proto.member.v1.ListMembersResponse;
import com.gym.proto.member.v1.GetMemberResponse;
import com.gym.proto.member.v1.UpdateProfileResponse;
import com.gym.proto.member.v1.PauseMembershipResponse;
import com.gym.proto.member.v1.ResumeMembershipResponse;
import com.gym.proto.member.v1.GetMembershipStatusResponse;
import com.gym.proto.member.v1.PauseMembershipRequest;
import com.gym.proto.member.v1.PurchaseMembershipRequest;
import com.gym.proto.member.v1.PurchaseMembershipResponse;
import com.gym.proto.member.v1.ResumeMembershipRequest;
import com.gym.proto.member.v1.UpdateProfileRequest;
import com.gym.proto.member.v1.ValidateMembershipRequest;
import com.gym.proto.member.v1.ValidateMembershipResponse;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberGrpcHandlerUnitTest {

    @Mock
    private MemberService memberService;

    @Mock
    private SubscriptionLifecycleUseCase subscriptionLifecycleUseCase;

    @Mock
    private MembershipPurchaseUseCase membershipPurchaseUseCase;

    @Mock
    private StreamObserver responseObserver;

    @Spy
    private MemberMapper memberMapper = Mappers.getMapper(MemberMapper.class);

    @Spy
    private SubscriptionMapper subscriptionMapper = Mappers.getMapper(SubscriptionMapper.class);

    private MemberGrpcHandler memberGrpcHandler;

    private UUID memberId;
    private UUID userId;
    private UUID gymId;
    private MemberDto memberDto;

    @BeforeEach
    void setUp() {
        memberId = UUID.randomUUID();
        userId = UUID.randomUUID();
        gymId = UUID.randomUUID();

        memberDto = new MemberDto(
                memberId, userId, "John Doe", "123456", "http://avatar",
                LocalDate.of(1995, 5, 15), MembershipStatus.ACTIVE, null, null);

        MemberGrpcDelegate memberGrpcDelegate =
                new MemberGrpcDelegate(memberService, subscriptionLifecycleUseCase, memberMapper);
        SubscriptionGrpcDelegate subscriptionGrpcDelegate = new SubscriptionGrpcDelegate(
                memberService, subscriptionLifecycleUseCase, membershipPurchaseUseCase, subscriptionMapper);
        memberGrpcHandler = new MemberGrpcHandler(memberGrpcDelegate, subscriptionGrpcDelegate);
    }

    private void runWithClaims(String uId, String role, String gId, Runnable action) {
        UserClaims claims = new UserClaims(uId, role, gId, null);
        Context.current().withValue(GrpcSecurityContext.CLAIMS_KEY, claims).run(action);
    }

    @Test
    void givenValidMemberId_whenGetMember_thenReturnsGetMemberResponse() {
        GetMemberRequest request = GetMemberRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(memberService.getMember(memberId.toString())).thenReturn(memberDto);

        runWithClaims(userId.toString(), "CUSTOMER", gymId.toString(), () ->
                memberGrpcHandler.getMember(request, responseObserver));

        verify(responseObserver, times(1)).onNext(any(GetMemberResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenNotFoundException_whenGetMember_thenCallsOnError() {
        GetMemberRequest request = GetMemberRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(memberService.getMember(memberId.toString())).thenThrow(new NotFoundException("Not found"));

        assertThrows(Throwable.class, () -> runWithClaims(userId.toString(), "CUSTOMER", gymId.toString(), () ->
                memberGrpcHandler.getMember(request, responseObserver)));
    }

    @Test
    void givenIllegalArgumentException_whenGetMember_thenCallsOnError() {
        GetMemberRequest request = GetMemberRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(memberService.getMember(memberId.toString())).thenThrow(new IllegalArgumentException("Invalid argument"));

        assertThrows(Throwable.class, () -> runWithClaims(userId.toString(), "CUSTOMER", gymId.toString(), () ->
                memberGrpcHandler.getMember(request, responseObserver)));
    }

    @Test
    void givenDomainException_whenGetMember_thenCallsOnError() {
        GetMemberRequest request = GetMemberRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(memberService.getMember(memberId.toString()))
                .thenThrow(new CannotPauseLifetimeException("Precondition failed"));

        assertThrows(Throwable.class, () -> runWithClaims(userId.toString(), "CUSTOMER", gymId.toString(), () ->
                memberGrpcHandler.getMember(request, responseObserver)));
    }

    @Test
    void givenRuntimeException_whenGetMember_thenCallsOnError() {
        GetMemberRequest request = GetMemberRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(memberService.getMember(memberId.toString())).thenThrow(new RuntimeException("Internal error"));

        assertThrows(Throwable.class, () -> runWithClaims(userId.toString(), "CUSTOMER", gymId.toString(), () ->
                memberGrpcHandler.getMember(request, responseObserver)));
    }

    @Test
    void givenValidUpdateProfileRequest_whenUpdateProfile_thenReturnsUpdateProfileResponse() {
        UpdateProfileRequest request = UpdateProfileRequest.newBuilder()
                .setMemberId(memberId.toString())
                .setFullName("Jane")
                .build();
        when(memberService.getMember(memberId.toString())).thenReturn(memberDto);
        when(memberService.updateProfile(eq(memberId.toString()), eq("Jane"), any(), any(), any()))
                .thenReturn(memberDto);

        runWithClaims(userId.toString(), "CUSTOMER", gymId.toString(), () ->
                memberGrpcHandler.updateProfile(request, responseObserver));

        verify(responseObserver, times(1)).onNext(any(UpdateProfileResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenErrorOnUpdateProfile_whenUpdateProfile_thenCallsOnError() {
        UpdateProfileRequest request = UpdateProfileRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(memberService.getMember(memberId.toString())).thenReturn(memberDto);
        when(memberService.updateProfile(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Error"));

        assertThrows(Throwable.class, () -> runWithClaims(userId.toString(), "CUSTOMER", gymId.toString(), () ->
                memberGrpcHandler.updateProfile(request, responseObserver)));
    }

    @Test
    void givenValidListMembersRequest_whenListMembers_thenReturnsListMembersResponse() {
        ListMembersRequest request = ListMembersRequest.newBuilder()
                .setGymId(gymId.toString())
                .setPage(0)
                .setLimit(10)
                .build();
        NormalPage<MemberDto> normalPage = new NormalPage<>(List.of(memberDto), 0, 10, 1L, 1);
        when(memberService.listMembers(gymId.toString(), 0, 10)).thenReturn(normalPage);

        runWithClaims(userId.toString(), "ADMIN", gymId.toString(), () ->
                memberGrpcHandler.listMembers(request, responseObserver));

        verify(responseObserver, times(1)).onNext(any(ListMembersResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenErrorOnListMembers_whenListMembers_thenCallsOnError() {
        ListMembersRequest request = ListMembersRequest.newBuilder().build();
        when(memberService.listMembers(any(), anyInt(), anyInt())).thenThrow(new RuntimeException("Error"));

        assertThrows(Throwable.class, () -> runWithClaims(userId.toString(), "SUPER_ADMIN", gymId.toString(), () ->
                memberGrpcHandler.listMembers(request, responseObserver)));
    }

    @Test
    void givenPurchaseRequest_whenPurchaseMembership_thenReturnsPurchaseMembershipResponse() {
        String planId = UUID.randomUUID().toString();
        PurchaseMembershipRequest request = PurchaseMembershipRequest.newBuilder()
                .setPlanId(planId)
                .setProvider("STRIPE")
                .build();
        when(memberService.getMemberByUserId(userId.toString())).thenReturn(memberDto);
        when(membershipPurchaseUseCase.purchaseMembership(
                userId.toString(), gymId.toString(), planId, "STRIPE", ""))
                .thenReturn(PurchaseMembershipResponse.newBuilder().setPaymentId("pay-1").setPaymentUrl("http://pay").build());

        runWithClaims(userId.toString(), "CUSTOMER", gymId.toString(), () ->
                memberGrpcHandler.purchaseMembership(request, responseObserver));

        verify(responseObserver, times(1)).onNext(any(PurchaseMembershipResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenValidMemberId_whenPauseMembership_thenReturnsPauseMembershipResponse() {
        PauseMembershipRequest request =
                PauseMembershipRequest.newBuilder().setMemberId(memberId.toString()).build();
        SubscriptionDto subDto = new SubscriptionDto(
                UUID.randomUUID(), memberId, gymId, UUID.randomUUID(), MembershipStatus.PAUSED,
                LocalDate.now(), LocalDate.now().plusDays(30), null, 30, 1);
        when(memberService.getMember(memberId.toString())).thenReturn(memberDto);
        when(subscriptionLifecycleUseCase.pauseSubscription(memberId.toString(), gymId.toString()))
                .thenReturn(subDto);

        runWithClaims(userId.toString(), "CUSTOMER", gymId.toString(), () ->
                memberGrpcHandler.pauseMembership(request, responseObserver));

        verify(responseObserver, times(1)).onNext(any(PauseMembershipResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenErrorOnPauseMembership_whenPauseMembership_thenCallsOnError() {
        PauseMembershipRequest request =
                PauseMembershipRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(memberService.getMember(memberId.toString())).thenReturn(memberDto);
        when(subscriptionLifecycleUseCase.pauseSubscription(any(), any())).thenThrow(new RuntimeException("Error"));

        assertThrows(Throwable.class, () -> runWithClaims(userId.toString(), "CUSTOMER", gymId.toString(), () ->
                memberGrpcHandler.pauseMembership(request, responseObserver)));
    }

    @Test
    void givenValidMemberId_whenResumeMembership_thenReturnsResumeMembershipResponse() {
        ResumeMembershipRequest request =
                ResumeMembershipRequest.newBuilder().setMemberId(memberId.toString()).build();
        SubscriptionDto subDto = new SubscriptionDto(
                UUID.randomUUID(), memberId, gymId, UUID.randomUUID(), MembershipStatus.ACTIVE,
                LocalDate.now(), LocalDate.now().plusDays(30), null, 30, 1);
        when(memberService.getMember(memberId.toString())).thenReturn(memberDto);
        when(subscriptionLifecycleUseCase.resumeSubscription(memberId.toString(), gymId.toString()))
                .thenReturn(subDto);

        runWithClaims(userId.toString(), "CUSTOMER", gymId.toString(), () ->
                memberGrpcHandler.resumeMembership(request, responseObserver));

        verify(responseObserver, times(1)).onNext(any(ResumeMembershipResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenErrorOnResumeMembership_whenResumeMembership_thenCallsOnError() {
        ResumeMembershipRequest request =
                ResumeMembershipRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(memberService.getMember(memberId.toString())).thenReturn(memberDto);
        when(subscriptionLifecycleUseCase.resumeSubscription(any(), any())).thenThrow(new RuntimeException("Error"));

        assertThrows(Throwable.class, () -> runWithClaims(userId.toString(), "CUSTOMER", gymId.toString(), () ->
                memberGrpcHandler.resumeMembership(request, responseObserver)));
    }

    @Test
    void givenValidMemberId_whenGetMembershipStatus_thenReturnsGetMembershipStatusResponse() {
        GetMembershipStatusRequest request =
                GetMembershipStatusRequest.newBuilder().setMemberId(memberId.toString()).build();
        SubscriptionDto subDto = new SubscriptionDto(
                UUID.randomUUID(), memberId, gymId, UUID.randomUUID(), MembershipStatus.ACTIVE,
                LocalDate.now(), LocalDate.now().plusDays(30), null, 30, 1);
        when(memberService.getMember(memberId.toString())).thenReturn(memberDto);
        when(subscriptionLifecycleUseCase.getActiveSubscription(memberId.toString(), gymId.toString()))
                .thenReturn(subDto);

        runWithClaims(userId.toString(), "CUSTOMER", gymId.toString(), () ->
                memberGrpcHandler.getMembershipStatus(request, responseObserver));

        verify(responseObserver, times(1)).onNext(any(GetMembershipStatusResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenErrorOnGetMembershipStatus_whenGetMembershipStatus_thenCallsOnError() {
        GetMembershipStatusRequest request =
                GetMembershipStatusRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(memberService.getMember(memberId.toString())).thenReturn(memberDto);
        when(subscriptionLifecycleUseCase.getActiveSubscription(any(), any()))
                .thenThrow(new RuntimeException("Error"));

        assertThrows(Throwable.class, () -> runWithClaims(userId.toString(), "CUSTOMER", gymId.toString(), () ->
                memberGrpcHandler.getMembershipStatus(request, responseObserver)));
    }

    @Test
    void givenValidMember_whenValidateMembership_thenReturnsValidateMembershipResponse() {
        ValidateMembershipRequest request = ValidateMembershipRequest.newBuilder()
                .setMemberId(memberId.toString())
                .setGymId(gymId.toString())
                .build();
        SubscriptionDto subDto = new SubscriptionDto(
                UUID.randomUUID(), memberId, gymId, UUID.randomUUID(), MembershipStatus.ACTIVE,
                LocalDate.now(), LocalDate.now().plusDays(30), null, 30, 1);
        when(memberService.getMember(memberId.toString())).thenReturn(memberDto);
        when(subscriptionLifecycleUseCase.getActiveSubscription(memberId.toString(), gymId.toString()))
                .thenReturn(subDto);

        runWithClaims("checkin-service-id", "CHECKIN_SERVICE", gymId.toString(), () ->
                memberGrpcHandler.validateMembership(request, responseObserver));

        verify(responseObserver, times(1)).onNext(any(ValidateMembershipResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenErrorOnValidateMembership_whenValidateMembership_thenCallsOnError() {
        ValidateMembershipRequest request = ValidateMembershipRequest.newBuilder()
                .setMemberId(memberId.toString())
                .setGymId(gymId.toString())
                .build();
        when(memberService.getMember(any())).thenThrow(new RuntimeException("Error"));

        assertThrows(Throwable.class, () -> runWithClaims("checkin-service-id", "CHECKIN_SERVICE", gymId.toString(), () ->
                memberGrpcHandler.validateMembership(request, responseObserver)));
    }

    @Test
    void givenStatus_whenListMembersByStatus_thenReturnsListMembersByStatusResponse() {
        ListMembersByStatusRequest request = ListMembersByStatusRequest.newBuilder()
                .setStatus(com.gym.proto.common.v1.MembershipStatus.MEMBERSHIP_STATUS_ACTIVE)
                .addGymIds(gymId.toString())
                .build();
        when(memberService.listMembersByStatus(MembershipStatus.ACTIVE, List.of(gymId.toString())))
                .thenReturn(List.of(memberDto));

        runWithClaims("notification-service-id", "NOTIFICATION_SERVICE", gymId.toString(), () ->
                memberGrpcHandler.listMembersByStatus(request, responseObserver));

        verify(responseObserver, times(1)).onNext(any(ListMembersByStatusResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenErrorOnListMembersByStatus_whenListMembersByStatus_thenCallsOnError() {
        ListMembersByStatusRequest request =
                ListMembersByStatusRequest.newBuilder().setStatus(com.gym.proto.common.v1.MembershipStatus.MEMBERSHIP_STATUS_ACTIVE).build();
        when(memberService.listMembersByStatus(any(), any())).thenThrow(new RuntimeException("Error"));

        assertThrows(Throwable.class, () -> runWithClaims("notification-service-id", "NOTIFICATION_SERVICE", gymId.toString(), () ->
                memberGrpcHandler.listMembersByStatus(request, responseObserver)));
    }

    @Test
    void givenMalformedDateOfBirth_whenUpdateProfile_thenCallsOnErrorWithInvalidArgumentStatus() {
        UpdateProfileRequest request = UpdateProfileRequest.newBuilder()
                .setMemberId(memberId.toString())
                .setDateOfBirth("invalid-date-format")
                .build();
        when(memberService.getMember(memberId.toString())).thenReturn(memberDto);

        assertThrows(java.time.format.DateTimeParseException.class, () ->
                runWithClaims(userId.toString(), "CUSTOMER", gymId.toString(), () ->
                        memberGrpcHandler.updateProfile(request, responseObserver)));
    }
}
