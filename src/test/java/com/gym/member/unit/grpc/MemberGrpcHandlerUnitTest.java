package com.gym.member.unit.grpc;

import com.gym.common.error.NotFoundException;
import com.gym.common.grpc.security.GrpcSecurityContext;
import com.gym.common.grpc.security.UserClaims;
import com.gym.common.pagination.NormalPage;
import com.gym.member.adapter.in.grpc.*;
import com.gym.member.adapter.out.grpc.PaymentGrpcClient;
import com.gym.member.application.port.in.SubscriptionLifecycleUseCase;
import com.gym.member.application.service.GymLocationService;
import com.gym.member.application.service.GymQRService;
import com.gym.member.application.service.MemberService;
import com.gym.member.domain.dto.GymDailySecretDto;
import com.gym.member.domain.dto.GymLocationDto;
import com.gym.member.domain.dto.MemberDto;
import com.gym.member.domain.dto.PlanDto;
import com.gym.member.domain.dto.SubscriptionDto;
import com.gym.member.domain.exception.CannotPauseLifetimeException;
import com.gym.member.domain.model.MembershipStatus;
import com.gym.member.domain.model.PlanType;
import com.gym.member.mapper.GymLocationMapper;
import com.gym.member.mapper.GymQRSecretMapper;
import com.gym.member.mapper.MemberMapper;
import com.gym.member.mapper.SubscriptionMapper;
import com.gym.proto.member.v1.*;
import com.gym.proto.payment.v1.InitiatePaymentResponse;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberGrpcHandlerUnitTest {

    @Mock
    private MemberService memberService;

    @Mock
    private SubscriptionLifecycleUseCase subscriptionLifecycleUseCase;

    @Mock
    private GymLocationService gymLocationService;

    @Mock
    private GymQRService gymQRService;

    @Mock
    private PaymentGrpcClient paymentGrpcClient;

    @Mock
    private StreamObserver responseObserver;

    @Spy
    private MemberMapper memberMapper = Mappers.getMapper(MemberMapper.class);

    @Spy
    private SubscriptionMapper subscriptionMapper = Mappers.getMapper(SubscriptionMapper.class);

    @Spy
    private GymLocationMapper gymLocationMapper = Mappers.getMapper(GymLocationMapper.class);

    @Spy
    private GymQRSecretMapper gymQRSecretMapper = Mappers.getMapper(GymQRSecretMapper.class);

    private MemberGrpcDelegate memberGrpcDelegate;
    private SubscriptionGrpcDelegate subscriptionGrpcDelegate;
    private GymLocationGrpcDelegate gymLocationGrpcDelegate;
    private MemberGrpcHandler memberGrpcHandler;

    private UUID memberId;
    private UUID userId;
    private UUID gymId;
    private UUID chainId;
    private MemberDto memberDto;

    @BeforeEach
    void setUp() {
        memberId = UUID.randomUUID();
        userId = UUID.randomUUID();
        gymId = UUID.randomUUID();
        chainId = UUID.randomUUID();

        memberDto = new MemberDto(memberId, userId, gymId, "John Doe", "123456", "http://avatar", LocalDate.of(1995, 5, 15), MembershipStatus.ACTIVE, null, null);

        memberGrpcDelegate = new MemberGrpcDelegate(memberService, memberMapper);
        subscriptionGrpcDelegate = new SubscriptionGrpcDelegate(memberService, subscriptionLifecycleUseCase, paymentGrpcClient, subscriptionMapper);
        gymLocationGrpcDelegate = new GymLocationGrpcDelegate(gymLocationService, gymQRService, gymLocationMapper, gymQRSecretMapper);
        memberGrpcHandler = new MemberGrpcHandler(memberGrpcDelegate, subscriptionGrpcDelegate, gymLocationGrpcDelegate);
    }

    private void runWithClaims(String uId, String role, String gId, Runnable action) {
        UserClaims claims = new UserClaims(uId, role, gId);
        Context.current().withValue(GrpcSecurityContext.CLAIMS_KEY, claims).run(action);
    }

    @Test
    void givenValidMemberId_whenGetMember_thenReturnsMemberResponse() {
        GetMemberRequest request = GetMemberRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(memberService.getMember(memberId.toString())).thenReturn(memberDto);

        runWithClaims(userId.toString(), "CUSTOMER", gymId.toString(), () -> {
            memberGrpcHandler.getMember(request, responseObserver);
        });

        verify(responseObserver, times(1)).onNext(any(MemberResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenNotFoundException_whenGetMember_thenCallsOnError() {
        GetMemberRequest request = GetMemberRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(memberService.getMember(memberId.toString())).thenThrow(new NotFoundException("Not found"));

        runWithClaims(userId.toString(), "CUSTOMER", gymId.toString(), () -> {
            memberGrpcHandler.getMember(request, responseObserver);
        });

        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void givenIllegalArgumentException_whenGetMember_thenCallsOnError() {
        GetMemberRequest request = GetMemberRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(memberService.getMember(memberId.toString())).thenThrow(new IllegalArgumentException("Invalid argument"));

        runWithClaims(userId.toString(), "CUSTOMER", gymId.toString(), () -> {
            memberGrpcHandler.getMember(request, responseObserver);
        });

        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void givenDomainException_whenGetMember_thenCallsOnError() {
        GetMemberRequest request = GetMemberRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(memberService.getMember(memberId.toString())).thenThrow(new CannotPauseLifetimeException("Precondition failed"));

        runWithClaims(userId.toString(), "CUSTOMER", gymId.toString(), () -> {
            memberGrpcHandler.getMember(request, responseObserver);
        });

        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void givenRuntimeException_whenGetMember_thenCallsOnError() {
        GetMemberRequest request = GetMemberRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(memberService.getMember(memberId.toString())).thenThrow(new RuntimeException("Internal error"));

        runWithClaims(userId.toString(), "CUSTOMER", gymId.toString(), () -> {
            memberGrpcHandler.getMember(request, responseObserver);
        });

        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void givenValidUpdateProfileRequest_whenUpdateProfile_thenReturnsMemberResponse() {
        UpdateProfileRequest request = UpdateProfileRequest.newBuilder().setMemberId(memberId.toString()).setFullName("Jane").build();
        when(memberService.getMember(memberId.toString())).thenReturn(memberDto);
        when(memberService.updateProfile(eq(memberId.toString()), eq("Jane"), any(), any(), any())).thenReturn(memberDto);

        runWithClaims(userId.toString(), "CUSTOMER", gymId.toString(), () -> {
            memberGrpcHandler.updateProfile(request, responseObserver);
        });

        verify(responseObserver, times(1)).onNext(any(MemberResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenErrorOnUpdateProfile_whenUpdateProfile_thenCallsOnError() {
        UpdateProfileRequest request = UpdateProfileRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(memberService.getMember(memberId.toString())).thenReturn(memberDto);
        when(memberService.updateProfile(any(), any(), any(), any(), any())).thenThrow(new RuntimeException("Error"));

        runWithClaims(userId.toString(), "CUSTOMER", gymId.toString(), () -> {
            memberGrpcHandler.updateProfile(request, responseObserver);
        });

        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void givenValidListMembersRequest_whenListMembers_thenReturnsListMembersResponse() {
        ListMembersRequest request = ListMembersRequest.newBuilder().setGymId(gymId.toString()).setPage(0).setLimit(10).build();
        NormalPage<MemberDto> normalPage = new NormalPage<>(List.of(memberDto), 0, 10, 1L, 1);
        when(memberService.listMembers(gymId.toString(), 0, 10)).thenReturn(normalPage);

        runWithClaims(userId.toString(), "ADMIN", gymId.toString(), () -> {
            memberGrpcHandler.listMembers(request, responseObserver);
        });

        verify(responseObserver, times(1)).onNext(any(ListMembersResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenErrorOnListMembers_whenListMembers_thenCallsOnError() {
        ListMembersRequest request = ListMembersRequest.newBuilder().build();
        when(memberService.listMembers(any(), anyInt(), anyInt())).thenThrow(new RuntimeException("Error"));

        runWithClaims(userId.toString(), "SUPER_ADMIN", gymId.toString(), () -> {
            memberGrpcHandler.listMembers(request, responseObserver);
        });

        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void givenValidGymId_whenGetPlans_thenReturnsPlansResponse() {
        GetPlansRequest request = GetPlansRequest.newBuilder().setGymId(gymId.toString()).build();
        PlanDto plan = new PlanDto(UUID.randomUUID(), gymId, "Monthly Pass", PlanType.MONTHLY, 30, 500000L, "Desc", true);
        when(gymLocationService.getPlans(gymId.toString())).thenReturn(List.of(plan));

        runWithClaims(userId.toString(), "CUSTOMER", gymId.toString(), () -> {
            memberGrpcHandler.getPlans(request, responseObserver);
        });

        verify(responseObserver, times(1)).onNext(any(PlansResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenErrorOnGetPlans_whenGetPlans_thenCallsOnError() {
        GetPlansRequest request = GetPlansRequest.newBuilder().build();
        when(gymLocationService.getPlans(any())).thenThrow(new RuntimeException("Error"));

        runWithClaims(userId.toString(), "SUPER_ADMIN", gymId.toString(), () -> {
            memberGrpcHandler.getPlans(request, responseObserver);
        });

        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void givenPurchaseRequest_whenPurchaseMembership_thenReturnsPurchaseResponse() {
        PurchaseMembershipRequest request = PurchaseMembershipRequest.newBuilder().setPlanId(UUID.randomUUID().toString()).setProvider("STRIPE").build();
        when(memberService.getMemberByUserId(userId.toString())).thenReturn(memberDto);
        InitiatePaymentResponse paymentResponse = InitiatePaymentResponse.newBuilder().setPaymentId("pay-1").setPaymentUrl("http://pay").build();
        when(paymentGrpcClient.initiatePayment(any())).thenReturn(paymentResponse);

        runWithClaims(userId.toString(), "CUSTOMER", gymId.toString(), () -> {
            memberGrpcHandler.purchaseMembership(request, responseObserver);
        });

        verify(responseObserver, times(1)).onNext(any(PurchaseResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenValidMemberId_whenPauseMembership_thenReturnsMembershipResponse() {
        PauseMembershipRequest request = PauseMembershipRequest.newBuilder().setMemberId(memberId.toString()).build();
        SubscriptionDto subDto = new SubscriptionDto(UUID.randomUUID(), memberId, UUID.randomUUID(), MembershipStatus.PAUSED, LocalDate.now(), LocalDate.now().plusDays(30), null, 30, 1);
        when(memberService.getMember(memberId.toString())).thenReturn(memberDto);
        when(subscriptionLifecycleUseCase.pauseSubscription(memberId.toString())).thenReturn(subDto);

        runWithClaims(userId.toString(), "CUSTOMER", gymId.toString(), () -> {
            memberGrpcHandler.pauseMembership(request, responseObserver);
        });

        verify(responseObserver, times(1)).onNext(any(MembershipResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenErrorOnPauseMembership_whenPauseMembership_thenCallsOnError() {
        PauseMembershipRequest request = PauseMembershipRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(memberService.getMember(memberId.toString())).thenReturn(memberDto);
        when(subscriptionLifecycleUseCase.pauseSubscription(any())).thenThrow(new RuntimeException("Error"));

        runWithClaims(userId.toString(), "CUSTOMER", gymId.toString(), () -> {
            memberGrpcHandler.pauseMembership(request, responseObserver);
        });

        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void givenValidMemberId_whenResumeMembership_thenReturnsMembershipResponse() {
        ResumeMembershipRequest request = ResumeMembershipRequest.newBuilder().setMemberId(memberId.toString()).build();
        SubscriptionDto subDto = new SubscriptionDto(UUID.randomUUID(), memberId, UUID.randomUUID(), MembershipStatus.ACTIVE, LocalDate.now(), LocalDate.now().plusDays(30), null, 30, 1);
        when(memberService.getMember(memberId.toString())).thenReturn(memberDto);
        when(subscriptionLifecycleUseCase.resumeSubscription(memberId.toString())).thenReturn(subDto);

        runWithClaims(userId.toString(), "CUSTOMER", gymId.toString(), () -> {
            memberGrpcHandler.resumeMembership(request, responseObserver);
        });

        verify(responseObserver, times(1)).onNext(any(MembershipResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenErrorOnResumeMembership_whenResumeMembership_thenCallsOnError() {
        ResumeMembershipRequest request = ResumeMembershipRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(memberService.getMember(memberId.toString())).thenReturn(memberDto);
        when(subscriptionLifecycleUseCase.resumeSubscription(any())).thenThrow(new RuntimeException("Error"));

        runWithClaims(userId.toString(), "CUSTOMER", gymId.toString(), () -> {
            memberGrpcHandler.resumeMembership(request, responseObserver);
        });

        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void givenValidMemberId_whenGetMembershipStatus_thenReturnsMembershipResponse() {
        GetMembershipStatusRequest request = GetMembershipStatusRequest.newBuilder().setMemberId(memberId.toString()).build();
        SubscriptionDto subDto = new SubscriptionDto(UUID.randomUUID(), memberId, UUID.randomUUID(), MembershipStatus.ACTIVE, LocalDate.now(), LocalDate.now().plusDays(30), null, 30, 1);
        when(memberService.getMember(memberId.toString())).thenReturn(memberDto);
        when(subscriptionLifecycleUseCase.getActiveSubscription(memberId.toString())).thenReturn(subDto);

        runWithClaims(userId.toString(), "CUSTOMER", gymId.toString(), () -> {
            memberGrpcHandler.getMembershipStatus(request, responseObserver);
        });

        verify(responseObserver, times(1)).onNext(any(MembershipResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenErrorOnGetMembershipStatus_whenGetMembershipStatus_thenCallsOnError() {
        GetMembershipStatusRequest request = GetMembershipStatusRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(memberService.getMember(memberId.toString())).thenReturn(memberDto);
        when(subscriptionLifecycleUseCase.getActiveSubscription(any())).thenThrow(new RuntimeException("Error"));

        runWithClaims(userId.toString(), "CUSTOMER", gymId.toString(), () -> {
            memberGrpcHandler.getMembershipStatus(request, responseObserver);
        });

        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void givenValidCreateGymLocationRequest_whenCreateGymLocation_thenReturnsGymLocationResponse() {
        CreateGymLocationRequest request = CreateGymLocationRequest.newBuilder().setChainId(chainId.toString()).setName("Gym A").setAddress("Addr").setCity("City").build();
        GymLocationDto locDto = new GymLocationDto(gymId, chainId, "Gym A", "Addr", "City", "ACTIVE");
        when(gymLocationService.createGymLocation(chainId.toString(), "Gym A", "Addr", "City")).thenReturn(locDto);

        runWithClaims(userId.toString(), "SUPER_ADMIN", gymId.toString(), () -> {
            memberGrpcHandler.createGymLocation(request, responseObserver);
        });

        verify(responseObserver, times(1)).onNext(any(GymLocationResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenErrorOnCreateGymLocation_whenCreateGymLocation_thenCallsOnError() {
        CreateGymLocationRequest request = CreateGymLocationRequest.newBuilder().build();
        when(gymLocationService.createGymLocation(any(), any(), any(), any())).thenThrow(new RuntimeException("Error"));

        runWithClaims(userId.toString(), "SUPER_ADMIN", gymId.toString(), () -> {
            memberGrpcHandler.createGymLocation(request, responseObserver);
        });

        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void givenValidUpdateGymLocationRequest_whenUpdateGymLocation_thenReturnsGymLocationResponse() {
        UpdateGymLocationRequest request = UpdateGymLocationRequest.newBuilder().setId(gymId.toString()).setName("Gym B").build();
        GymLocationDto locDto = new GymLocationDto(gymId, chainId, "Gym B", "Addr", "City", "ACTIVE");
        when(gymLocationService.getGymLocation(gymId.toString())).thenReturn(locDto);
        when(gymLocationService.updateGymLocation(eq(gymId.toString()), eq("Gym B"), any(), any(), any())).thenReturn(locDto);

        runWithClaims(userId.toString(), "ADMIN", gymId.toString(), () -> {
            memberGrpcHandler.updateGymLocation(request, responseObserver);
        });

        verify(responseObserver, times(1)).onNext(any(GymLocationResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenErrorOnUpdateGymLocation_whenUpdateGymLocation_thenCallsOnError() {
        UpdateGymLocationRequest request = UpdateGymLocationRequest.newBuilder().setId(gymId.toString()).build();
        GymLocationDto locDto = new GymLocationDto(gymId, chainId, "Gym B", "Addr", "City", "ACTIVE");
        when(gymLocationService.getGymLocation(gymId.toString())).thenReturn(locDto);
        when(gymLocationService.updateGymLocation(any(), any(), any(), any(), any())).thenThrow(new RuntimeException("Error"));

        runWithClaims(userId.toString(), "SUPER_ADMIN", gymId.toString(), () -> {
            memberGrpcHandler.updateGymLocation(request, responseObserver);
        });

        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void givenChainId_whenListGymLocations_thenReturnsGymLocationsResponse() {
        ListGymLocationsRequest request = ListGymLocationsRequest.newBuilder().setChainId(chainId.toString()).build();
        GymLocationDto locDto = new GymLocationDto(gymId, chainId, "Gym B", "Addr", "City", "ACTIVE");
        when(gymLocationService.listGymLocations(chainId.toString())).thenReturn(List.of(locDto));

        runWithClaims(userId.toString(), "SUPER_ADMIN", gymId.toString(), () -> {
            memberGrpcHandler.listGymLocations(request, responseObserver);
        });

        verify(responseObserver, times(1)).onNext(any(GymLocationsResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenErrorOnListGymLocations_whenListGymLocations_thenCallsOnError() {
        ListGymLocationsRequest request = ListGymLocationsRequest.newBuilder().build();
        when(gymLocationService.listGymLocations(any())).thenThrow(new RuntimeException("Error"));

        runWithClaims(userId.toString(), "SUPER_ADMIN", gymId.toString(), () -> {
            memberGrpcHandler.listGymLocations(request, responseObserver);
        });

        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void givenGymId_whenGetGymLocation_thenReturnsGymLocationResponse() {
        GetGymLocationRequest request = GetGymLocationRequest.newBuilder().setId(gymId.toString()).build();
        GymLocationDto locDto = new GymLocationDto(gymId, chainId, "Gym B", "Addr", "City", "ACTIVE");
        when(gymLocationService.getGymLocation(gymId.toString())).thenReturn(locDto);

        runWithClaims(userId.toString(), "ADMIN", gymId.toString(), () -> {
            memberGrpcHandler.getGymLocation(request, responseObserver);
        });

        verify(responseObserver, times(1)).onNext(any(GymLocationResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenErrorOnGetGymLocation_whenGetGymLocation_thenCallsOnError() {
        GetGymLocationRequest request = GetGymLocationRequest.newBuilder().setId(gymId.toString()).build();
        when(gymLocationService.getGymLocation(any())).thenThrow(new RuntimeException("Error"));

        runWithClaims(userId.toString(), "SUPER_ADMIN", gymId.toString(), () -> {
            memberGrpcHandler.getGymLocation(request, responseObserver);
        });

        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void givenValidMember_whenValidateMembership_thenReturnsValidateMembershipResponse() {
        ValidateMembershipRequest request = ValidateMembershipRequest.newBuilder().setMemberId(memberId.toString()).setGymId(gymId.toString()).build();
        when(memberService.getMember(memberId.toString())).thenReturn(memberDto);

        runWithClaims("checkin-service-id", "CHECKIN_SERVICE", gymId.toString(), () -> {
            memberGrpcHandler.validateMembership(request, responseObserver);
        });

        verify(responseObserver, times(1)).onNext(any(ValidateMembershipResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenErrorOnValidateMembership_whenValidateMembership_thenCallsOnError() {
        ValidateMembershipRequest request = ValidateMembershipRequest.newBuilder().setMemberId(memberId.toString()).setGymId(gymId.toString()).build();
        when(memberService.getMember(any())).thenThrow(new RuntimeException("Error"));

        runWithClaims("checkin-service-id", "CHECKIN_SERVICE", gymId.toString(), () -> {
            memberGrpcHandler.validateMembership(request, responseObserver);
        });

        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void givenGymId_whenGetGymDailySecret_thenReturnsGymDailySecretResponse() {
        GetGymDailySecretRequest request = GetGymDailySecretRequest.newBuilder().setGymId(gymId.toString()).build();
        GymDailySecretDto secretDto = new GymDailySecretDto(gymId, "secret-xyz");
        when(gymQRService.getGymDailySecret(gymId.toString())).thenReturn(secretDto);

        runWithClaims("checkin-service-id", "CHECKIN_SERVICE", gymId.toString(), () -> {
            memberGrpcHandler.getGymDailySecret(request, responseObserver);
        });

        verify(responseObserver, times(1)).onNext(any(GymDailySecretResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenErrorOnGetGymDailySecret_whenGetGymDailySecret_thenCallsOnError() {
        GetGymDailySecretRequest request = GetGymDailySecretRequest.newBuilder().setGymId(gymId.toString()).build();
        when(gymQRService.getGymDailySecret(any())).thenThrow(new RuntimeException("Error"));

        runWithClaims("checkin-service-id", "CHECKIN_SERVICE", gymId.toString(), () -> {
            memberGrpcHandler.getGymDailySecret(request, responseObserver);
        });

        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void givenStatus_whenListMembersByStatus_thenReturnsListMembersByStatusResponse() {
        ListMembersByStatusRequest request = ListMembersByStatusRequest.newBuilder().setStatus("ACTIVE").addGymIds(gymId.toString()).build();
        when(memberService.listMembersByStatus(MembershipStatus.ACTIVE, List.of(gymId.toString()))).thenReturn(List.of(memberDto));

        runWithClaims("notification-service-id", "NOTIFICATION_SERVICE", gymId.toString(), () -> {
            memberGrpcHandler.listMembersByStatus(request, responseObserver);
        });

        verify(responseObserver, times(1)).onNext(any(ListMembersByStatusResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenErrorOnListMembersByStatus_whenListMembersByStatus_thenCallsOnError() {
        ListMembersByStatusRequest request = ListMembersByStatusRequest.newBuilder().setStatus("ACTIVE").build();
        when(memberService.listMembersByStatus(any(), any())).thenThrow(new RuntimeException("Error"));

        runWithClaims("notification-service-id", "NOTIFICATION_SERVICE", gymId.toString(), () -> {
            memberGrpcHandler.listMembersByStatus(request, responseObserver);
        });

        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void givenMalformedDateOfBirth_whenUpdateProfile_thenCallsOnErrorWithInvalidArgumentStatus() {
        UpdateProfileRequest request = UpdateProfileRequest.newBuilder()
                .setMemberId(memberId.toString())
                .setDateOfBirth("invalid-date-format")
                .build();
        when(memberService.getMember(memberId.toString())).thenReturn(memberDto);

        runWithClaims(userId.toString(), "CUSTOMER", gymId.toString(), () -> {
            memberGrpcHandler.updateProfile(request, responseObserver);
        });

        verify(responseObserver, times(1)).onError(argThat(throwable ->
                throwable instanceof io.grpc.StatusRuntimeException &&
                        ((io.grpc.StatusRuntimeException) throwable).getStatus().getCode() == io.grpc.Status.Code.INVALID_ARGUMENT
        ));
    }
}
