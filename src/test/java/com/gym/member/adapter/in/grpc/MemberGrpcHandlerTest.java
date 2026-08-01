package com.gym.member.adapter.in.grpc;

import com.gym.common.error.NotFoundException;
import com.gym.common.pagination.NormalPage;
import com.gym.member.domain.exception.CannotPauseLifetimeException;
import com.gym.member.application.service.GymLocationService;
import com.gym.member.application.service.GymQRService;
import com.gym.member.application.service.MemberService;
import com.gym.member.application.service.SubscriptionService;
import com.gym.member.domain.dto.GymDailySecretDto;
import com.gym.member.domain.dto.GymLocationDto;
import com.gym.member.domain.dto.MemberDto;
import com.gym.member.domain.dto.PlanDto;
import com.gym.member.domain.dto.SubscriptionDto;
import com.gym.member.domain.model.MembershipStatus;
import com.gym.member.domain.model.PlanType;
import com.gym.proto.member.v1.*;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberGrpcHandlerTest {

    @Mock
    private MemberService memberService;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private GymLocationService gymLocationService;

    @Mock
    private GymQRService gymQRService;

    @Mock
    private StreamObserver responseObserver;

    @InjectMocks
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

        memberDto = new MemberDto(memberId, userId, gymId, "John Doe", "123456", "http://avatar", "98765", MembershipStatus.ACTIVE, null, null);
    }

    @Test
    void getMember_success() {
        GetMemberRequest request = GetMemberRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(memberService.getMember(memberId.toString())).thenReturn(memberDto);

        memberGrpcHandler.getMember(request, responseObserver);

        verify(responseObserver, times(1)).onNext(any(MemberResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void getMember_notFound_handlesError() {
        GetMemberRequest request = GetMemberRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(memberService.getMember(memberId.toString())).thenThrow(new NotFoundException("Not found"));

        memberGrpcHandler.getMember(request, responseObserver);

        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void getMember_illegalArgument_handlesError() {
        GetMemberRequest request = GetMemberRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(memberService.getMember(memberId.toString())).thenThrow(new IllegalArgumentException("Invalid argument"));

        memberGrpcHandler.getMember(request, responseObserver);

        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void getMember_domainException_handlesError() {
        GetMemberRequest request = GetMemberRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(memberService.getMember(memberId.toString())).thenThrow(new CannotPauseLifetimeException("Precondition failed"));

        memberGrpcHandler.getMember(request, responseObserver);

        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void getMember_runtimeException_handlesError() {
        GetMemberRequest request = GetMemberRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(memberService.getMember(memberId.toString())).thenThrow(new RuntimeException("Internal error"));

        memberGrpcHandler.getMember(request, responseObserver);

        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void updateProfile_success() {
        UpdateProfileRequest request = UpdateProfileRequest.newBuilder().setMemberId(memberId.toString()).setFullName("Jane").build();
        when(memberService.updateProfile(eq(memberId.toString()), eq("Jane"), any(), any(), any())).thenReturn(memberDto);

        memberGrpcHandler.updateProfile(request, responseObserver);

        verify(responseObserver, times(1)).onNext(any(MemberResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void updateProfile_error() {
        UpdateProfileRequest request = UpdateProfileRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(memberService.updateProfile(any(), any(), any(), any(), any())).thenThrow(new RuntimeException("Error"));

        memberGrpcHandler.updateProfile(request, responseObserver);

        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void listMembers_success() {
        ListMembersRequest request = ListMembersRequest.newBuilder().setGymId(gymId.toString()).setPage(0).setLimit(10).build();
        NormalPage<MemberDto> normalPage = new NormalPage<>(List.of(memberDto), 0, 10, 1L, 1);
        when(memberService.listMembers(gymId.toString(), 0, 10)).thenReturn(normalPage);

        memberGrpcHandler.listMembers(request, responseObserver);

        verify(responseObserver, times(1)).onNext(any(ListMembersResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void listMembers_error() {
        ListMembersRequest request = ListMembersRequest.newBuilder().build();
        when(memberService.listMembers(any(), anyInt(), anyInt())).thenThrow(new RuntimeException("Error"));

        memberGrpcHandler.listMembers(request, responseObserver);

        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void getPlans_success() {
        GetPlansRequest request = GetPlansRequest.newBuilder().setGymId(gymId.toString()).build();
        PlanDto plan = new PlanDto(UUID.randomUUID(), gymId, "Monthly Pass", PlanType.MONTHLY, 30, 500000L, "Desc", true);
        when(gymLocationService.getPlans(gymId.toString())).thenReturn(List.of(plan));

        memberGrpcHandler.getPlans(request, responseObserver);

        verify(responseObserver, times(1)).onNext(any(PlansResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void getPlans_error() {
        GetPlansRequest request = GetPlansRequest.newBuilder().build();
        when(gymLocationService.getPlans(any())).thenThrow(new RuntimeException("Error"));

        memberGrpcHandler.getPlans(request, responseObserver);

        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void purchaseMembership_success() {
        PurchaseMembershipRequest request = PurchaseMembershipRequest.newBuilder().setPlanId(UUID.randomUUID().toString()).build();

        memberGrpcHandler.purchaseMembership(request, responseObserver);

        verify(responseObserver, times(1)).onNext(any(PurchaseResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void pauseMembership_success() {
        PauseMembershipRequest request = PauseMembershipRequest.newBuilder().setMemberId(memberId.toString()).build();
        SubscriptionDto subDto = new SubscriptionDto(UUID.randomUUID(), memberId, UUID.randomUUID(), MembershipStatus.PAUSED, LocalDate.now(), LocalDate.now().plusDays(30), null, 30, 1);
        when(subscriptionService.pauseSubscription(memberId.toString())).thenReturn(subDto);

        memberGrpcHandler.pauseMembership(request, responseObserver);

        verify(responseObserver, times(1)).onNext(any(MembershipResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void pauseMembership_error() {
        PauseMembershipRequest request = PauseMembershipRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(subscriptionService.pauseSubscription(any())).thenThrow(new RuntimeException("Error"));

        memberGrpcHandler.pauseMembership(request, responseObserver);

        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void resumeMembership_success() {
        ResumeMembershipRequest request = ResumeMembershipRequest.newBuilder().setMemberId(memberId.toString()).build();
        SubscriptionDto subDto = new SubscriptionDto(UUID.randomUUID(), memberId, UUID.randomUUID(), MembershipStatus.ACTIVE, LocalDate.now(), LocalDate.now().plusDays(30), null, 30, 1);
        when(subscriptionService.resumeSubscription(memberId.toString())).thenReturn(subDto);

        memberGrpcHandler.resumeMembership(request, responseObserver);

        verify(responseObserver, times(1)).onNext(any(MembershipResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void resumeMembership_error() {
        ResumeMembershipRequest request = ResumeMembershipRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(subscriptionService.resumeSubscription(any())).thenThrow(new RuntimeException("Error"));

        memberGrpcHandler.resumeMembership(request, responseObserver);

        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void getMembershipStatus_success() {
        GetMembershipStatusRequest request = GetMembershipStatusRequest.newBuilder().setMemberId(memberId.toString()).build();
        SubscriptionDto subDto = new SubscriptionDto(UUID.randomUUID(), memberId, UUID.randomUUID(), MembershipStatus.ACTIVE, LocalDate.now(), LocalDate.now().plusDays(30), null, 30, 1);
        when(subscriptionService.getActiveSubscription(memberId.toString())).thenReturn(subDto);

        memberGrpcHandler.getMembershipStatus(request, responseObserver);

        verify(responseObserver, times(1)).onNext(any(MembershipResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void getMembershipStatus_error() {
        GetMembershipStatusRequest request = GetMembershipStatusRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(subscriptionService.getActiveSubscription(any())).thenThrow(new RuntimeException("Error"));

        memberGrpcHandler.getMembershipStatus(request, responseObserver);

        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void createGymLocation_success() {
        CreateGymLocationRequest request = CreateGymLocationRequest.newBuilder().setChainId(chainId.toString()).setName("Gym A").setAddress("Addr").setCity("City").build();
        GymLocationDto locDto = new GymLocationDto(gymId, chainId, "Gym A", "Addr", "City", "ACTIVE");
        when(gymLocationService.createGymLocation(chainId.toString(), "Gym A", "Addr", "City")).thenReturn(locDto);

        memberGrpcHandler.createGymLocation(request, responseObserver);

        verify(responseObserver, times(1)).onNext(any(GymLocationResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void createGymLocation_error() {
        CreateGymLocationRequest request = CreateGymLocationRequest.newBuilder().build();
        when(gymLocationService.createGymLocation(any(), any(), any(), any())).thenThrow(new RuntimeException("Error"));

        memberGrpcHandler.createGymLocation(request, responseObserver);

        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void updateGymLocation_success() {
        UpdateGymLocationRequest request = UpdateGymLocationRequest.newBuilder().setId(gymId.toString()).setName("Gym B").build();
        GymLocationDto locDto = new GymLocationDto(gymId, chainId, "Gym B", "Addr", "City", "ACTIVE");
        when(gymLocationService.updateGymLocation(eq(gymId.toString()), eq("Gym B"), any(), any(), any())).thenReturn(locDto);

        memberGrpcHandler.updateGymLocation(request, responseObserver);

        verify(responseObserver, times(1)).onNext(any(GymLocationResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void updateGymLocation_error() {
        UpdateGymLocationRequest request = UpdateGymLocationRequest.newBuilder().setId(gymId.toString()).build();
        when(gymLocationService.updateGymLocation(any(), any(), any(), any(), any())).thenThrow(new RuntimeException("Error"));

        memberGrpcHandler.updateGymLocation(request, responseObserver);

        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void listGymLocations_success() {
        ListGymLocationsRequest request = ListGymLocationsRequest.newBuilder().setChainId(chainId.toString()).build();
        GymLocationDto locDto = new GymLocationDto(gymId, chainId, "Gym B", "Addr", "City", "ACTIVE");
        when(gymLocationService.listGymLocations(chainId.toString())).thenReturn(List.of(locDto));

        memberGrpcHandler.listGymLocations(request, responseObserver);

        verify(responseObserver, times(1)).onNext(any(GymLocationsResponse.newBuilder().build().getClass()));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void listGymLocations_error() {
        ListGymLocationsRequest request = ListGymLocationsRequest.newBuilder().build();
        when(gymLocationService.listGymLocations(any())).thenThrow(new RuntimeException("Error"));

        memberGrpcHandler.listGymLocations(request, responseObserver);

        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void getGymLocation_success() {
        GetGymLocationRequest request = GetGymLocationRequest.newBuilder().setId(gymId.toString()).build();
        GymLocationDto locDto = new GymLocationDto(gymId, chainId, "Gym B", "Addr", "City", "ACTIVE");
        when(gymLocationService.getGymLocation(gymId.toString())).thenReturn(locDto);

        memberGrpcHandler.getGymLocation(request, responseObserver);

        verify(responseObserver, times(1)).onNext(any(GymLocationResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void getGymLocation_error() {
        GetGymLocationRequest request = GetGymLocationRequest.newBuilder().setId(gymId.toString()).build();
        when(gymLocationService.getGymLocation(any())).thenThrow(new RuntimeException("Error"));

        memberGrpcHandler.getGymLocation(request, responseObserver);

        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void validateMembership_valid() {
        ValidateMembershipRequest request = ValidateMembershipRequest.newBuilder().setMemberId(memberId.toString()).setGymId(gymId.toString()).build();
        when(memberService.getMember(memberId.toString())).thenReturn(memberDto);

        memberGrpcHandler.validateMembership(request, responseObserver);

        verify(responseObserver, times(1)).onNext(any(ValidateMembershipResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void validateMembership_invalid_wrongGymId() {
        ValidateMembershipRequest request = ValidateMembershipRequest.newBuilder().setMemberId(memberId.toString()).setGymId(UUID.randomUUID().toString()).build();
        when(memberService.getMember(memberId.toString())).thenReturn(memberDto);

        memberGrpcHandler.validateMembership(request, responseObserver);

        verify(responseObserver, times(1)).onNext(any(ValidateMembershipResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void validateMembership_error() {
        ValidateMembershipRequest request = ValidateMembershipRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(memberService.getMember(any())).thenThrow(new RuntimeException("Error"));

        memberGrpcHandler.validateMembership(request, responseObserver);

        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void getGymDailySecret_success() {
        GetGymDailySecretRequest request = GetGymDailySecretRequest.newBuilder().setGymId(gymId.toString()).build();
        GymDailySecretDto secretDto = new GymDailySecretDto(gymId, "secret-xyz");
        when(gymQRService.getGymDailySecret(gymId.toString())).thenReturn(secretDto);

        memberGrpcHandler.getGymDailySecret(request, responseObserver);

        verify(responseObserver, times(1)).onNext(any(GymDailySecretResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void getGymDailySecret_error() {
        GetGymDailySecretRequest request = GetGymDailySecretRequest.newBuilder().setGymId(gymId.toString()).build();
        when(gymQRService.getGymDailySecret(any())).thenThrow(new RuntimeException("Error"));

        memberGrpcHandler.getGymDailySecret(request, responseObserver);

        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void listMembersByStatus_success() {
        ListMembersByStatusRequest request = ListMembersByStatusRequest.newBuilder().setStatus("ACTIVE").build();
        when(memberService.listMembersByStatus(MembershipStatus.ACTIVE, List.of())).thenReturn(List.of(memberDto));

        memberGrpcHandler.listMembersByStatus(request, responseObserver);

        verify(responseObserver, times(1)).onNext(any(ListMembersByStatusResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void listMembersByStatus_error() {
        ListMembersByStatusRequest request = ListMembersByStatusRequest.newBuilder().setStatus("ACTIVE").build();
        when(memberService.listMembersByStatus(any(), any())).thenThrow(new RuntimeException("Error"));

        memberGrpcHandler.listMembersByStatus(request, responseObserver);

        verify(responseObserver, times(1)).onError(any());
    }
}
